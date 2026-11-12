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

import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.listeners.BeforeEachListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult

import javax.sql.DataSource

import org.eclipse.apoapsis.ortserver.dao.connect

import org.jetbrains.exposed.v1.jdbc.Database

import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * A test extension for integration tests that need database access. The extension uses a
 * [Postgres test container][SharedPostgresTestContainer] that is shared by all tests running in the same JVM, so that
 * the container is started and the schema is migrated only once. After each test, all tables are truncated, so that
 * every test sees an empty database.
 *
 * The execution order of lifecycle callbacks in Kotest depends on the way the extension is installed and the test class
 * implements the callbacks. To ensure that the database connection has already been set up, either declare the
 * extension by overriding the `extensions` property of the spec, or, if it is installed via `extension()` or
 * `install()`, do not override the callback functions like `suspend fun beforeEach` but use the DSL functions like
 * `beforeEach {}` instead. For details see [this Kotest issue](https://github.com/kotest/kotest/issues/3555).
 */
open class DatabaseTestExtension : BeforeEachListener, AfterEachListener {
    internal val postgres: PostgreSQLContainer
        get() = SharedPostgresTestContainer.postgres

    private val dataSource: DataSource
        get() = SharedPostgresTestContainer.dataSource

    lateinit var db: Database
    lateinit var fixtures: Fixtures

    override suspend fun beforeEach(testCase: TestCase) {
        db = dataSource.connect()
        fixtures = Fixtures(db)
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        // Ensure every integration test uses a clean database.
        SharedPostgresTestContainer.truncateTables()
    }
}
