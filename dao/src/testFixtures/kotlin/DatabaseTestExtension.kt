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

package org.ossreviewtoolkit.server.dao.test

import io.kotest.core.extensions.install
import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeEachListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.testcontainers.JdbcTestContainerExtension

import javax.sql.DataSource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.migrate

import org.testcontainers.containers.PostgreSQLContainer

/**
 * A test extension for integration tests that need database access. The extension sets up a test container with a
 * Postgres database and creates a data source for this database. Schema migration is run. After each test a cleanup
 * is performed, so that every test sees a fresh database.
 */
class DatabaseTestExtension(
    /**
     * A code block that gets executed before each test after the connection to the database has been established.
     * Use this mechanism instead of the before test lifecycle hook to work-around problems with the initialization
     * order of test extensions. (_beforeTest()_ of the test class is actually called before the _beforeTest()_
     * function of the extension; therefore, no database access is possible there.)
     */
    private val fixture: () -> Unit = {}
) : BeforeSpecListener, AfterSpecListener, BeforeEachListener, AfterEachListener {
    private val postgres = PostgreSQLContainer<Nothing>("postgres:14").apply {
        startupAttempts = 1
    }

    private lateinit var dataSource: DataSource

    override suspend fun beforeSpec(spec: Spec) {
        dataSource = spec.install(JdbcTestContainerExtension(postgres)) {
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
        dataSource.connect()
        dataSource.migrate()

        fixture()
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

private fun getTestFlywayConfig(dataSource: DataSource) = FluentConfiguration()
    .dataSource(dataSource)
    .schemas(TEST_DB_SCHEMA)
    .cleanDisabled(false)
    .createSchemas(true)
    .baselineOnMigrate(true)
