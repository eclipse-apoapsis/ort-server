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

package org.ossreviewtoolkit.server.dao.test.repositories

import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.migrate
import org.ossreviewtoolkit.server.dao.repositories.DaoEnvironmentRepository
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentToolVersionDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentToolVersionsTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentVariableDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentVariablesTable
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class DaoEnvironmentRepositoryTest : DatabaseTest() {
    private val environmentRepository = DaoEnvironmentRepository()

    private val ortVersion = "1.0"
    private val javaVersion = "11.0.16"
    private val os = "Linux"
    private val processors = 8
    private val maxMemory = 8321499136

    private val variables = mapOf(
        "SHELL" to "/bin/bash",
        "TERM" to "xterm-256color"
    )

    private val toolVersions = mapOf(
        "Conan" to "1.53.0",
        "NPM" to "8.15.1"
    )

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()
        dataSource.migrate()
    }

    init {
        test("create should create an entry in the database") {
            val createdEnvironment = environmentRepository.create(
                ortVersion,
                javaVersion,
                os,
                processors,
                maxMemory,
                variables,
                toolVersions
            )

            val dbEntry = environmentRepository.get(createdEnvironment.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe Environment(
                dbEntry.id,
                ortVersion,
                javaVersion,
                os,
                processors,
                maxMemory,
                variables,
                toolVersions
            )
        }

        test("list should retrieve all entities from the database") {
            val createdEnvironment1 = environmentRepository.create(
                ortVersion,
                javaVersion,
                os,
                processors,
                maxMemory,
                variables,
                toolVersions
            )

            val createdEnvironment2 = environmentRepository.create(
                "2.0",
                javaVersion,
                os,
                processors,
                maxMemory,
                variables,
                toolVersions
            )

            environmentRepository.list() should containExactlyInAnyOrder(createdEnvironment1, createdEnvironment2)
        }

        test("delete should delete an entity from the database") {
            val createdEnvironment = environmentRepository.create(
                ortVersion,
                javaVersion,
                os,
                processors,
                maxMemory,
                variables,
                toolVersions
            )

            environmentRepository.delete(createdEnvironment.id)

            environmentRepository.list() should beEmpty()

            transaction {
                EnvironmentVariableDao.find { EnvironmentVariablesTable.environmentId eq createdEnvironment.id }
                    .toList() should beEmpty()

                EnvironmentToolVersionDao.find { EnvironmentToolVersionsTable.environmentId eq createdEnvironment.id }
                    .toList() should beEmpty()
            }
        }
    }
}
