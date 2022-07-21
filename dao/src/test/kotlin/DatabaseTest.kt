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
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.testcontainers.JdbcTestContainerExtension

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.clean
import org.ossreviewtoolkit.server.dao.migrate

import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base class for integration tests using the Database within TestContainers. The TestApplication needs to be configured
 * to use the provided [dataSource] using `configureDatabase(dataSource)`.
 */
open class DatabaseTest : FunSpec() {
    private val postgres = PostgreSQLContainer<Nothing>("postgres:11").apply {
        startupAttempts = 1
    }

    val dataSource by lazy {
        install(JdbcTestContainerExtension(postgres)) {
            poolName = "integrationTestsConnectionPool"
            maximumPoolSize = 5
            schema = "ort_server"
        }
    }

    override suspend fun beforeSpec(spec: Spec) {
        Database.connect(dataSource)
        migrate(dataSource)
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        // Ensure every integration test uses a clean database.
        clean(dataSource)
    }
}
