/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource

import org.eclipse.apoapsis.ortserver.dao.migrate
import org.eclipse.apoapsis.ortserver.utils.test.Images

import org.testcontainers.postgresql.PostgreSQLContainer

internal const val TEST_DB_SCHEMA = "ort_server_test"

/**
 * A Postgres test container that is shared by all tests running in the same JVM. The container is started and the
 * schema is migrated on first access, and the container is stopped when the JVM exits. Data inserted by migrations
 * is removed, so that every test starts with an empty database.
 *
 * Tests must not change the schema, and must remove their data after running by calling [truncateTables].
 */
internal object SharedPostgresTestContainer {
    val postgres: PostgreSQLContainer by lazy {
        PostgreSQLContainer(Images.POSTGRES).apply {
            startupAttempts = 1
            start()
        }
    }

    val dataSource: DataSource by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                poolName = "integrationTestsConnectionPool"
                maximumPoolSize = 5
                schema = TEST_DB_SCHEMA
            }
        ).also { dataSource ->
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    dataSource.close()
                    postgres.stop()
                }
            )

            dataSource.migrate()
            tables = dataSource.readTables()

            // Remove data inserted by migrations, so that every test starts with the same empty database.
            dataSource.truncate(tables)
        }
    }

    private lateinit var tables: List<String>

    /** Remove all data from all tables and reset all sequences, so that generated IDs start at 1 again. */
    fun truncateTables() = dataSource.truncate(tables)
}

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
