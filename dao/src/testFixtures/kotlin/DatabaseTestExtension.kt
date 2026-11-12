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

package org.eclipse.apoapsis.ortserver.dao.test

import io.kotest.core.extensions.install
import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeEachListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerSpecExtension

import javax.sql.DataSource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.dao.connect
import org.eclipse.apoapsis.ortserver.dao.migrate
import org.eclipse.apoapsis.ortserver.shared.coroutines.Virtual
import org.eclipse.apoapsis.ortserver.utils.test.Images

import org.jetbrains.exposed.v1.jdbc.Database

import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * A test extension for integration tests that need database access. The extension sets up a test container with a
 * Postgres database and creates a data source for this database. Schema migration is run once before the first test.
 * After each test, all tables are truncated, so that every test sees an empty database. This is much faster than
 * re-creating the schema for every test.
 *
 * The execution order of lifecycle callbacks in Kotest depends on the way the extension is installed and the test class
 * implements the callbacks. To ensure that the database migrations have already been performed, either declare the
 * extension by overriding the `extensions` property of the spec, or, if it is installed via `extension()` or
 * `install()`, do not override the callback functions like `suspend fun beforeEach` but use the DSL functions like
 * `beforeEach {}` instead. For details see [this Kotest issue](https://github.com/kotest/kotest/issues/3555).
 */
open class DatabaseTestExtension : BeforeSpecListener, AfterSpecListener, BeforeEachListener, AfterEachListener {
    internal val postgres = PostgreSQLContainer(Images.POSTGRES).apply {
        startupAttempts = 1
    }

    lateinit var dataSource: DataSource

    private lateinit var tables: List<String>

    lateinit var db: Database
    lateinit var fixtures: Fixtures

    override suspend fun beforeSpec(spec: Spec) {
        dataSource = spec.install(JdbcDatabaseContainerSpecExtension(postgres)) {
            poolName = "integrationTestsConnectionPool"
            maximumPoolSize = 5
            schema = TEST_DB_SCHEMA
        }

        dataSource.migrate()
        tables = dataSource.readTables()

        // Remove data inserted by migrations, so that every test starts with the same empty database.
        dataSource.truncate(tables)
    }

    override suspend fun afterSpec(spec: Spec) {
        if (postgres.isRunning) {
            withContext(Dispatchers.Virtual) { postgres.stop() }
        }
    }

    override suspend fun beforeEach(testCase: TestCase) {
        db = dataSource.connect()
        fixtures = Fixtures(db)
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        // Ensure every integration test uses a clean database.
        dataSource.truncate(tables)
    }
}

internal const val TEST_DB_SCHEMA = "ort_server_test"

/**
 * Return the names of all tables in the test schema, except for the Flyway schema history table which must be kept to
 * not run the migrations again.
 */
private fun DataSource.readTables(): List<String> =
    connection.use { connection ->
        connection.prepareStatement(
            "SELECT tablename FROM pg_tables WHERE schemaname = ? AND tablename <> 'flyway_schema_history'"
        ).use { statement ->
            statement.setString(1, TEST_DB_SCHEMA)
            statement.executeQuery().use { resultSet ->
                buildList { while (resultSet.next()) add(resultSet.getString(1)) }
            }
        }
    }

/** Remove all data from the given [tables] and reset all sequences, so that generated IDs start at 1 again. */
private fun DataSource.truncate(tables: List<String>) {
    connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                "TRUNCATE TABLE ${tables.joinToString { "\"$TEST_DB_SCHEMA\".\"$it\"" }} RESTART IDENTITY CASCADE"
            )
        }
    }
}
