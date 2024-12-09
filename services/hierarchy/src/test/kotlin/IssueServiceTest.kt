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

package org.eclipse.apoapsis.ortserver.services

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder

class IssueServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
        }

        "OrderDirection.toSortOrder" should {
            "return SortOrder.ASC when OrderDirection is ASCENDING" {
                OrderDirection.ASCENDING.toSortOrder() shouldBe SortOrder.ASC
            }

            "return SortOrder.DESC when OrderDirection is DESCENDING" {
                OrderDirection.DESCENDING.toSortOrder() shouldBe SortOrder.DESC
            }
        }

        "countForOrtRunIds" should {
            "return issue count for ORT run" {
                val service = IssueService(db)
                val ortRun = createOrtRunWithIssues()

                service.countForOrtRunIds(ortRun.id) shouldBe 3
            }

            "return count of issues found in ORT runs" {
                val service = IssueService(db)
                val repositoryId = fixtures.createRepository().id

                val ortRun1Id = createOrtRunWithIssues(repositoryId).id

                val ortRun2Id = createOrtRunWithIssues(
                    repositoryId,
                    generateIssues().plus(
                        Issue(
                            timestamp = Clock.System.now(),
                            source = "Scanner",
                            message = "Issue",
                            severity = Severity.WARNING,
                            affectedPath = "path",
                            identifier = Identifier("Maven", "com.example", "example", "1.0")
                        )
                    )
                ).id

                service.countForOrtRunIds(ortRun1Id, ortRun2Id) shouldBe 7
            }
        }
    }

    private fun generateIssues(): List<Issue> =
        listOf(
            Issue(
                timestamp = Clock.System.now(),
                source = "Analyzer",
                message = "Issue 1",
                severity = Severity.ERROR,
                affectedPath = "path"
            ),
            Issue(
                timestamp = Clock.System.now(),
                source = "Advisor",
                message = "Issue 1",
                severity = Severity.ERROR,
                affectedPath = "path",
                identifier = Identifier("Maven", "com.example", "example", "1.0")
            ),
            Issue(
                timestamp = Clock.System.now(),
                source = "Advisor",
                message = "Issue 2",
                severity = Severity.WARNING,
                affectedPath = "path",
                identifier = Identifier("Maven", "com.example", "example", "1.0")
            )
        )

    private fun createOrtRunWithIssues(
        repositoryId: Long = fixtures.createRepository().id,
        issues: List<Issue> = this.generateIssues()
    ): OrtRun {
        val ortRun = fixtures.createOrtRun(repositoryId)

        fixtures.ortRunRepository.update(
            ortRun.id,
            issues = issues.asPresent()
        )

        return ortRun
    }
}
