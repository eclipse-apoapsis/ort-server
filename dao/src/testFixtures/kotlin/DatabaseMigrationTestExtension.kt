/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.core.test.TestCase

import kotlinx.coroutines.runBlocking

import org.eclipse.apoapsis.ortserver.dao.connect

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

/**
 * A specialized test extension for database migration tests. This extension sets up a test environment similar to
 * [DatabaseTestExtension], but allows configurable schema migration. The schema is migrated to the specified
 * [initialVersion] before each test.
 *
 * The [testAppliedMigration] function must be used in the test cases to run the migration to the [targetVersion] and
 * execute the test code.
 */
class DatabaseMigrationTestExtension(
    private val initialVersion: String,
    private val targetVersion: String
) : DatabaseTestExtension() {
    override suspend fun beforeEach(testCase: TestCase) {
        db = dataSource.connect()
        migrateToVersion(initialVersion)
        fixtures = Fixtures(db)
    }

    fun testAppliedMigration(test: suspend () -> Unit) {
        migrateToVersion(targetVersion)

        @Suppress("ForbiddenMethodCall")
        runBlocking { test() }
    }

    private fun migrateToVersion(targetVersion: String) {
        getTestFlywayConfig(dataSource).migrateToVersion(targetVersion)
    }
}

private fun FluentConfiguration.migrateToVersion(version: String) = Flyway(target(version)).migrate()
