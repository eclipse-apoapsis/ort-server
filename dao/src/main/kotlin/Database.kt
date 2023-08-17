/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager

import org.koin.core.module.Module
import org.koin.dsl.module

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.utils.config.getStringOrNull

/**
 * Connect the database.
 */
fun DataSource.connect() = Database.connect(this)

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

fun createDataSource(config: DatabaseConfig): DataSource {
    val dataSourceConfig = HikariConfig().apply {
        jdbcUrl = "${config.jdbcUrl}/${config.name}"
        schema = config.schema
        username = config.username
        password = config.password
        maximumPoolSize = config.maximumPoolSize
        driverClassName = "org.postgresql.Driver"

        addDataSourceProperty("ApplicationName", "ort_server")
        addDataSourceProperty("sslmode", config.sslMode)
        config.sslCert?.let { addDataSourceProperty("sslcert", it) }
        config.sslKey?.let { addDataSourceProperty("sslkey", it) }
        config.sslRootCert?.let { addDataSourceProperty("sslrootcert", it) }
    }

    dataSourceConfig.validate()

    return HikariDataSource(dataSourceConfig)
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val name: String,
    val schema: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int,
    val driverClassName: String,
    val sslMode: String,
    val sslCert: String?,
    val sslKey: String?,
    val sslRootCert: String?,
)

/**
 * Create a [DatabaseConfig] object for the *database* configuration, given in [config].
 */
fun createDatabaseConfig(config: ConfigManager) = DatabaseConfig(
    jdbcUrl = config.getString("database.url"),
    name = config.getString("database.name"),
    schema = config.getString("database.schema"),
    username = config.getSecret(Path("database.username")),
    password = config.getSecret(Path("database.password")),
    maximumPoolSize = config.getInt("database.poolsize"),
    driverClassName = "org.postgresql.Driver",
    sslMode = config.getString("database.sslmode"),

    sslCert = config.getStringOrNull("database.sslcert"),
    sslKey = config.getStringOrNull("database.sslkey"),
    sslRootCert = config.getStringOrNull("database.sslrootcert")
)

/**
 * Return a Koin [Module] that sets up a database connection based on the current application configuration.
 */
fun databaseModule(): Module = module {
    single { createDatabaseConfig(get()) }

    single(createdAtStart = true) { createDataSource(get()).connect() }
}

/**
 * Execute the [block] in a database [transaction], configured with the provided [transactionIsolation] and [readOnly].
 *
 * Returns the actual result type. Throws an exception in case of a failure.
 */
suspend fun <T> Database.dbQuery(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: () -> T
): T =
    dbQueryCatching(transactionIsolation, readOnly, block).getOrThrow()

/**
 * Execute the [block] in a database [transaction], configured with the provided [transactionIsolation] and [readOnly].
 *
 * Returns a wrapped [Result] object and delegates exceptions handling to the caller.
 */
suspend fun <T> Database.dbQueryCatching(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: () -> T
): Result<T> =
    runCatching {
        withContext(Dispatchers.IO) {
            transaction(transactionIsolation, readOnly, this@runCatching) { block() }
        }
    }.mapExceptions()

/**
 * Execute the [block] in a database [transaction], configured with the provided [transactionIsolation] and [readOnly].
 *
 * Returns the actual result type. Throws an exception in case of a failure.
 */
fun <T> Database.blockingQuery(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: () -> T
): T =
    blockingQueryCatching(transactionIsolation, readOnly, block).getOrThrow()

/**
 * Execute the [block] in a database [transaction], configured with the provided [transactionIsolation] and [readOnly].
 *
 * Returns a wrapped [Result] object and delegates exceptions handling to the caller.
 */
fun <T> Database.blockingQueryCatching(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: () -> T
): Result<T> =
    runCatching { transaction(transactionIsolation, readOnly, this) { block() } }.mapExceptions()

/**
 * Execute the [block] in a [blockingQueryCatching], configured with the provided [transactionIsolation] and [readOnly].
 * Return the encapsulated value or null if an [EntityNotFoundException] is thrown. Otherwise, throw the exception.
 */
fun <T> Database.entityQuery(
    transactionIsolation: Int = transactionManager.defaultIsolationLevel,
    readOnly: Boolean = transactionManager.defaultReadOnly,
    block: () -> T
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
    runCatching {
        onFailure {
            if (it is ExposedSQLException) {
                when (it.sqlState) {
                    PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value -> {
                        throw UniqueConstraintException("Unique constraint violation: ${it.message}.", it)
                    }
                }
            }

            throw it
        }.getOrThrow()
    }

/**
 * Alias definition for a function that generates the conditions of a WHERE clause that can be used to select entities
 * in queries.
 */
typealias ConditionBuilder = SqlExpressionBuilder.() -> Op<Boolean>

/**
 * Find a single entity of this entity class based on the passed in [condition]. Throw an [EntityNotFoundException] if
 * no entity is matched by the selection criteria. This extension function is useful to implement certain get
 * operations in repositories operating on multiple properties that uniquely identify an entity. Note: It should be
 * ensured via constraints in the database that the query will not return multiple entities.
 */
fun <T : LongEntity> LongEntityClass<T>.findSingle(condition: ConditionBuilder): T =
    find(condition).singleOrNull() ?: throw EntityNotFoundException(EntityID(0L, table), this)
