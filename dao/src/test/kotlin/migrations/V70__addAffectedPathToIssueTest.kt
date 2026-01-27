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

package org.eclipse.apoapsis.ortserver.dao.migrations

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseMigrationTestExtension
import org.eclipse.apoapsis.ortserver.model.Severity

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Suppress("ClassNaming")
class V70__addAffectedPathToIssueTest : WordSpec({
    val extension = extension(DatabaseMigrationTestExtension("69", "70"))

    "migration V70" should {
        "set the affectedPath column for existing issues" {
            var issueIdWithTimeoutError: Long = 0
            var issueIdWithoutTimeoutError: Long = 0

            transaction {
                issueIdWithTimeoutError = V69IssuesTable.create(
                    "ERROR: Timeout after 12345 seconds while scanning file 'some/file/path'."
                )
                issueIdWithoutTimeoutError = V69IssuesTable.create("message")
            }

            extension.testAppliedMigration {
                transaction {
                    V70IssuesTable.selectAll().where { V70IssuesTable.id eq issueIdWithTimeoutError }.single().let {
                        it[V70IssuesTable.affectedPath] shouldBe "some/file/path"
                    }

                    V70IssuesTable.selectAll().where { V70IssuesTable.id eq issueIdWithoutTimeoutError }.single().let {
                        it[V70IssuesTable.affectedPath] shouldBe null
                    }
                }
            }
        }
    }
})

private object V69IssuesTable : LongIdTable("issues") {
    val timestamp = timestamp("timestamp")
    val issueSource = text("source")
    val message = text("message")
    val severity = enumerationByName<Severity>("severity", 128)

    fun create(message: String) = insertAndGetId {
        it[timestamp] = Clock.System.now()
        it[issueSource] = "source"
        it[V69IssuesTable.message] = message
        it[severity] = Severity.ERROR
    }.value
}

private object V70IssuesTable : LongIdTable("issues") {
    val timestamp = timestamp("timestamp")
    val issueSource = text("source")
    val message = text("message")
    val severity = enumerationByName<Severity>("severity", 128)
    val affectedPath = text("affected_path").nullable()
}
