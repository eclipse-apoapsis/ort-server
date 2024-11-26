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

package org.eclipse.apoapsis.ortserver.dao.tables

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue

class ScanSummariesIssuesTableTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    init {
        "createByIssue" should {
            "create an entity for an issue" {
                val summary = createScanSummary()
                val issue = Issue(
                    timestamp = Instant.parse("2024-10-18T05:56:06Z"),
                    source = "test",
                    message = "Some test issue",
                    severity = Severity.WARNING,
                    affectedPath = "test/path",
                    identifier = Identifier("testType", "testNS", "testName", "testVersion"),
                    worker = "testWorker"
                )

                dbExtension.db.blockingQuery {
                    val newEntity = ScanSummariesIssuesDao.createByIssue(summary.id.value, issue)

                    ScanSummariesIssuesDao.all().toList() shouldContainExactlyInAnyOrder listOf(newEntity)

                    val expectedIssue = issue.copy(
                        identifier = null,
                        worker = null
                    )
                    newEntity.mapToModel() shouldBe expectedIssue
                }
            }

            "deduplicate issues" {
                val summary = createScanSummary()
                val issueTime = Instant.parse("2024-10-18T07:32:48Z")
                val issue1 = Issue(
                    timestamp = issueTime,
                    source = "test",
                    message = "Some test issue",
                    severity = Severity.WARNING,
                    affectedPath = "test/path",
                    identifier = null,
                    worker = null
                )
                val issue2 = Issue(
                    timestamp = issueTime,
                    source = "test",
                    message = "Another test issue",
                    severity = Severity.WARNING,
                    affectedPath = "test/path",
                    identifier = null,
                    worker = null
                )
                val issue3 = issue1.copy(timestamp = Instant.parse("2024-10-18T07:34:44Z"))

                dbExtension.db.blockingQuery {
                    listOf(issue1, issue2, issue3).forEach {
                        ScanSummariesIssuesDao.createByIssue(summary.id.value, it)
                    }

                    val allIssues = IssueDao.all().map {
                        it.mapToModel(issueTime, null, null)
                    }
                    allIssues shouldContainExactlyInAnyOrder listOf(issue1, issue2)
                }
            }
        }
    }

    /**
     * Create a [ScanSummaryDao] entity that can be used to assign issues to it.
     */
    private fun createScanSummary(): ScanSummaryDao =
        dbExtension.db.blockingQuery {
            ScanSummaryDao.new {
                startTime = Clock.System.now().minus(2.minutes).toDatabasePrecision()
                endTime = Clock.System.now().toDatabasePrecision()
            }
        }
}
