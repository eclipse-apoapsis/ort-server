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
import io.kotest.core.test.TestResult
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerExtension
import io.kotest.extensions.testcontainers.toDataSource

import javax.sql.DataSource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.dao.connect
import org.eclipse.apoapsis.ortserver.dao.migrate

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

import org.jetbrains.exposed.sql.Database

import org.testcontainers.containers.PostgreSQLContainer

/**
 * A test extension for integration tests that need database access. The extension sets up a test container with a
 * Postgres database and creates a data source for this database. Schema migration is run before each test. After each
 *  test, a cleanup is performed, so that every test sees a fresh database.
 *
 * The execution order of lifecycle callbacks in Kotest depends on the way the extension is installed and the test class
 * implements the callbacks. To ensure that the database migrations have already been performed, do not override the
 * callback functions like `suspend fun beforeEach` but use the DSL functions like `beforeEach {}` instead. For details
 * see [this Kotest issue](https://github.com/kotest/kotest/issues/3555).
 */
open class DatabaseTestExtension : BeforeSpecListener, AfterSpecListener, BeforeEachListener, AfterEachListener {
    private val postgres = PostgreSQLContainer<Nothing>("postgres:14").apply {
        startupAttempts = 1
    }

    lateinit var dataSource: DataSource

    lateinit var db: Database
    lateinit var fixtures: Fixtures

    override suspend fun beforeSpec(spec: Spec) {
        spec.install(JdbcDatabaseContainerExtension(postgres))
        dataSource = postgres.toDataSource {
            poolName = "integrationTestsConnectionPool"
            maximumPoolSize = 5
            schema = TEST_DB_SCHEMA
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        if (postgres.isRunning) {
            withContext(Dispatchers.IO) { postgres.stop() }
        }
    }

    override suspend fun beforeEach(testCase: TestCase) {
        db = dataSource.connect()
        dataSource.migrate()
        fixtures = Fixtures(db)
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        // Ensure every integration test uses a clean database.
        clean(dataSource)
    }
}

private const val TEST_DB_SCHEMA = "ort_server_test"

private fun clean(dataSource: DataSource) {
    Flyway(getTestFlywayConfig(dataSource)).clean()
}

internal fun getTestFlywayConfig(dataSource: DataSource) = FluentConfiguration()
    .dataSource(dataSource)
    .schemas(TEST_DB_SCHEMA)
    .cleanDisabled(false)
    .createSchemas(true)
    .baselineOnMigrate(true)
