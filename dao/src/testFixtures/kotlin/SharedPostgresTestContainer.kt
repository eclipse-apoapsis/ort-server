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

import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * A singleton that manages a shared [PostgreSQLContainer] across all test specs within a JVM process. The container is
 * started lazily on the first access to [dataSource] and stopped via a JVM shutdown hook when the process exits.
 *
 * Using this shared container avoids the overhead of starting a new PostgreSQL instance for every test spec. Schema
 * isolation between specs is still ensured by [DatabaseTestExtension], which runs Flyway migrations before each test
 * and cleans the schema after each test.
 */
internal object SharedPostgresTestContainer {
    private const val SCHEMA = "ort_server_test"

    private val containerLazy: Lazy<PostgreSQLContainer> = lazy {
        PostgreSQLContainer("postgres:15").apply {
            startupAttempts = 1
        }.also { it.start() }
    }

    private val container: PostgreSQLContainer by containerLazy

    val dataSource: DataSource by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = container.jdbcUrl
                username = container.username
                password = container.password
                schema = SCHEMA
                poolName = "integrationTestsConnectionPool"
                maximumPoolSize = 5
            }
        )
    }

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                if (containerLazy.isInitialized() && container.isRunning) {
                    container.stop()
                }
            }
        )
    }
}
