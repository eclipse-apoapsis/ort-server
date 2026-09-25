/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

@file:Suppress("TooManyFunctions")

package org.eclipse.apoapsis.ortserver.dao

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource

import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.shared.coroutines.Virtual

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.SizedIterable
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DataSource::class.java)

/**
 * Connect the database.
 */
fun DataSource.connect() = Database.connect(
    datasource = this,
    databaseConfig = getDatabaseConfig()
)

private fun getDatabaseConfig(): DatabaseConfig =
    DatabaseConfig {
        sqlLogger = SqlQueryTraceLogger

        // This only affects transactions started with `suspendTransaction`.
        dispatcher = Dispatchers.Virtual
    }

/**
 * Run the database migrations.
 */
fun DataSource.migrate() {
    Flyway(getFlywayConfig(this)).migrate()
}

private fun getFlywayConfig(dataSource: DataSource) = FluentConfiguration()
    .dataSource(dataSource)
    .cleanDisabled(false)
    .createSchemas(true)
    .baselineOnMigrate(true)

fun createDataSource(config: DataSourceConfig): DataSource {
    val dataSourceConfig = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.name}"
        schema = config.schema
        username = config.username
        password = config.password

        connectionTimeout = config.connectionTimeout
        idleTimeout = config.idleTimeout
        keepaliveTime = config.keepaliveTime
        maxLifetime = config.maxLifetime
        maximumPoolSize = config.maximumPoolSize
        minimumIdle = config.minimumIdle

        addDataSourceProperty("ApplicationName", "ort_server")
        addDataSourceProperty("sslmode", config.sslMode)
        config.sslCert?.let { addDataSourceProperty("sslcert", it) }
        config.sslKey?.let { addDataSourceProperty("sslkey", it) }
        config.sslRootCert?.let { addDataSourceProperty("sslrootcert", it) }

        config.initSqlStatement?.let {
            logger.info("Setting connection initialization statement to '{}'.", it)
            connectionInitSql = it
        }
    }

    dataSourceConfig.validate()

    return runCatching {
        HikariDataSource(dataSourceConfig)
    }.onFailure {
        logger.error("Failed to create data source.", it)
    }.getOrThrow()
}

/**
 * Return a Koin [Module] that sets up a database connection based on the current application configuration.
 * Depending on the [startEager] parameter, the database connection is established either eagerly or lazily.
 */
fun databaseModule(startEager: Boolean = true): Module = module {
    singleOf(DataSourceConfig::create)

    single(createdAtStart = startEager) { createDataSource(get()).connect() }
}

/**
 * Execute the [block] in a database [suspendTransaction], configured with the provided [transactionIsolation] and
 * [readOnly], and return the result. Throws an exception in case of a failure.
 *
 * See [Database.transactionCatching] for details on how the current transaction is propagated to nested calls of
 * [blockingQuery], [blockingQueryCatching], [dbQuery], [dbQueryCatching], [Database.transaction] and
 * [Database.transactionCatching].
 */
suspend fun <T> Database.transaction(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: suspend JdbcTransaction.() -> T
): T =
    transactionCatching(transactionIsolation, readOnly, block).getOrThrow()

/**
 * Execute the [block] in a database [suspendTransaction], configured with the provided [transactionIsolation] and
 * [readOnly], and return a wrapped [Result] object. [CancellationException] and [Error] are propagated, all other
 * exceptions are wrapped in a [Result] object and delegated to the caller.
 *
 * Calls to [Database.transaction] and [Database.transactionCatching] from [block] are running in the same transaction,
 * as long as the coroutine context is not dropped. This is because Exposed's `suspendTransaction` stores the current
 * transaction in the coroutine context. Otherwise, nested calls will start a new transaction.
 *
 * Calls to [blockingQuery] and [blockingQueryCatching] from [block] are running in the same transaction, because they
 * do not switch the thread and `suspendTransaction` stores the current transaction in a
 * [kotlinx.coroutines.ThreadContextElement] which adds the transaction from the coroutine to the thread-local.
 *
 * Calls to [dbQuery] and [dbQueryCatching] from [block] are running in the same transaction, because even though they
 * switch the thread using `withContext(Dispatchers.IO)`, this keeps the coroutine context intact and copying the
 * current transaction from the [kotlinx.coroutines.ThreadContextElement] to the thread-local still works.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> Database.transactionCatching(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: suspend JdbcTransaction.() -> T
): Result<T> =
    try {
        val result = suspendTransaction(this@transactionCatching, transactionIsolation, readOnly) { block() }
        Result.success(result)
    } catch (e: CancellationException) {
        throw e // Preserve structured concurrency.
    } catch (e: Error) {
        throw e // Don't swallow fatal JVM errors.
    } catch (e: Throwable) {
        Result.failure<T>(e).mapExceptions()
    }

/**
 * Execute the [block] in a database [transaction], configured with the provided [transactionIsolation] and [readOnly],
 * and return the result. Throws an exception in case of a failure.
 *
 * See [dbQueryCatching] for details on how the current transaction is propagated to nested calls of [blockingQuery],
 * [blockingQueryCatching], [dbQuery], [dbQueryCatching], [Database.transaction] and [Database.transactionCatching].
 */
suspend fun <T> Database.dbQuery(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: JdbcTransaction.() -> T
): T =
    dbQueryCatching(transactionIsolation, readOnly, block).getOrThrow()

/**
 * Execute the [block] in a database [transaction], configured with the provided [transactionIsolation] and [readOnly],
 * and return a wrapped [Result] object.
 *
 * Calls to this function from [block] will start a new transaction, because switching the thread using
 * `withContext(Dispatchers.IO)` does not preserve the thread-local that Exposed's blocking `transaction` uses to store
 * the current transaction.
 *
 * Calls to [blockingQuery] and [blockingQueryCatching] from [block] will run in the same transaction, because they do
 * not switch the thread and Exposed's blocking [transaction] uses a thread-local to store the current transaction.
 *
 * Calls to [Database.transaction] and [Database.transactionCatching] from [block] will start a new transaction, because
 * Exposed's [suspendTransaction] expects the current transaction to be stored in the coroutine context, while the
 * blocking [transaction] stores it in a thread-local.
 */
suspend fun <T> Database.dbQueryCatching(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: JdbcTransaction.() -> T
): Result<T> =
    runCatching {
        withContext(Dispatchers.IO) {
            transaction(this@runCatching, transactionIsolation, readOnly) { block() }
        }
    }.mapExceptions()

/**
 * Execute the [block] in a database [transaction], configured with the provided [transactionIsolation] and [readOnly],
 * and return the result. Throws an exception in case of a failure.
 *
 * See [blockingQueryCatching] for details on how the current transaction is propagated to nested calls of
 * [blockingQuery], [blockingQueryCatching], [dbQuery], [dbQueryCatching], [Database.transaction] and
 * [Database.transactionCatching].
 */
fun <T> Database.blockingQuery(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: Transaction.() -> T
): T =
    blockingQueryCatching(transactionIsolation, readOnly, block).getOrThrow()

/**
 * Execute the [block] in a database [transaction], configured with the provided [transactionIsolation] and [readOnly],
 * and return a wrapped [Result] object.
 *
 * Calls to this function from [block] will run in the same transaction, because it does not switch the thread and
 * Exposed's blocking [transaction] uses a thread-local to store the current transaction.
 *
 * Calls to [dbQuery] and [dbQueryCatching] from [block] will start a new transaction, because switching the thread
 * using `withContext(Dispatchers.IO)` does not preserve the thread-local that Exposed's blocking [transaction] uses to
 * store the current transaction.
 *
 * Calls to [Database.transaction] and [Database.transactionCatching] from [block] will start a new transaction, because
 * Exposed's [suspendTransaction] expects the current transaction to be stored in the coroutine context, while the
 * blocking [transaction] stores it in a thread-local.
 */
fun <T> Database.blockingQueryCatching(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: Transaction.() -> T
): Result<T> =
    runCatching { transaction(this, transactionIsolation, readOnly) { block() } }.mapExceptions()

/**
 * Execute the [block] in a [blockingQueryCatching], configured with the provided [transactionIsolation] and [readOnly].
 * Return the encapsulated value or null if an [EntityNotFoundException] is thrown. Otherwise, throw the exception.
 */
fun <T> Database.entityQuery(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: Transaction.() -> T
): T? = blockingQueryCatching(transactionIsolation, readOnly, block).getOrElse {
    when (it) {
        is EntityNotFoundException -> null
        else -> throw it
    }
}

/**
 * Map the generic database exceptions to more specific exceptions.
 */
internal fun <T> Result<T>.mapExceptions(): Result<T> =
    recoverCatching {
        if (it is ExposedSQLException) {
            when (it.sqlState) {
                PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value -> {
                    throw UniqueConstraintException("Unique constraint violation: ${it.message}.", it)
                }
            }
        }

        throw it
    }

/**
 * Find a single entity of this entity class based on the passed in [condition]. Throw an [EntityNotFoundException] if
 * no entity is matched by the selection criteria. This extension function is useful to implement certain get
 * operations in repositories operating on multiple properties that uniquely identify an entity. Note: It should be
 * ensured via constraints in the database that the query will not return multiple entities.
 */
fun <T : LongEntity> LongEntityClass<T>.findSingle(condition: Op<Boolean>): T =
    find(condition).singleOrNull() ?: throw EntityNotFoundException(EntityID(0L, table), this)

/**
 * Map the given [modelObjects] collection to a [SizedIterable] of data objects using the given [mapper] function.
 * This function should be used to populate intermediate tables used for the implementation of m:n relations. It
 * makes sure that the result does not contain any duplicates.
 */
fun <M, D> mapAndDeduplicate(modelObjects: Collection<M>?, mapper: (M) -> D): SizedIterable<D> =
    SizedCollection(mapToSet(modelObjects, mapper).orEmpty())

/**
 * Map the given [modelObjects] collection to a [Set] using the given [mapper] function.
 */
private fun <D, M> mapToSet(modelObjects: Iterable<M>?, mapper: (M) -> D): Set<D>? =
    modelObjects?.mapTo(mutableSetOf(), mapper)
