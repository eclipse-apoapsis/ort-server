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

package org.eclipse.apoapsis.ortserver.services.ortrun

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

import io.mockk.mockk

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.IssueFilter
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.v1.jdbc.Database

@Suppress("LargeClass")
class IssueServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures
    private lateinit var service: IssueService

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures

            val ortRunService = OrtRunService(
                db,
                fixtures.advisorJobRepository,
                fixtures.advisorRunRepository,
                fixtures.analyzerJobRepository,
                fixtures.analyzerRunRepository,
                fixtures.evaluatorJobRepository,
                fixtures.evaluatorRunRepository,
                fixtures.ortRunRepository,
                fixtures.reporterJobRepository,
                fixtures.reporterRunRepository,
                fixtures.notifierJobRepository,
                fixtures.notifierRunRepository,
                fixtures.repositoryConfigurationRepository,
                fixtures.repositoryRepository,
                fixtures.resolvedConfigurationRepository,
                fixtures.scannerJobRepository,
                fixtures.scannerRunRepository,
                mockk(),
                mockk()
            )

            service = IssueService(db, ortRunService)
        }

        "listForOrtRunId" should {
            "return issues for the given ORT run ID" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts",
                        identifier = Identifier("Maven", "com.example", "example-lib", "1.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "timeout while scanning package",
                        severity = Severity.WARNING,
                        affectedPath = "src/main/kotlin",
                        identifier = Identifier("Maven", "com.example", "scanner-test", "2.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(2.seconds),
                        source = "Scanner",
                        message = "unresolved npm issue",
                        severity = Severity.WARNING,
                        affectedPath = "src/test/kotlin",
                        identifier = Identifier("Maven", "com.example", "unresolved-lib", "3.0.0")
                    )
                )

                val resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = "dependency not found.*",
                            reason = IssueResolutionReason.CANT_FIX_ISSUE,
                            comment = "This is a known issue with the external dependency repository"
                        ),
                        IssueResolution(
                            message = "timeout while scanning.*",
                            reason = IssueResolutionReason.CANT_FIX_ISSUE,
                            comment = "Timeout issues are acceptable for this type of scanning"
                        )
                    )
                )

                val ortRun = createOrtRunWithIssueResolutions(repositoryId, issues, resolutions)

                val result = service.listForOrtRunId(ortRun.id)

                result.data.size shouldBe 3
                result.totalCount shouldBe 3

                val dependencyIssue = result.data.single { "dependency not found" in it.message }
                dependencyIssue.resolutions.size shouldBe 1
                dependencyIssue.resolutions[0].message shouldStartWith "dependency not found"

                val timeoutIssue = result.data.single { "timeout while scanning" in it.message }
                timeoutIssue.resolutions.size shouldBe 1
                timeoutIssue.resolutions[0].message shouldStartWith "timeout while scanning"

                val unresolvedIssue = result.data.single { "unresolved npm issue" in it.message }
                unresolvedIssue.resolutions.size shouldBe 0
            }

            "filter resolved issues when issuesFilter.resolved is true" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts",
                        identifier = Identifier("Maven", "com.example", "example-lib", "1.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "NPM",
                        message = "unresolved npm issue",
                        severity = Severity.WARNING,
                        affectedPath = "src",
                        identifier = Identifier("Maven", "com.example", "unresolved-lib", "3.0.0")
                    )
                )

                val resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = "dependency not found.*",
                            reason = IssueResolutionReason.CANT_FIX_ISSUE,
                            comment = "This is a known issue with the external dependency repository"
                        )
                    )
                )

                val ortRun = createOrtRunWithIssueResolutions(repositoryId, issues, resolutions)

                val result = service.listForOrtRunId(ortRun.id, issuesFilter = IssueFilter(resolved = true))

                result.data.size shouldBe 1
                result.totalCount shouldBe 1
                result.data[0].message shouldContain "dependency not found"
                result.data[0].resolutions.size shouldBe 1
            }

            "filter unresolved issues when issuesFilter.resolved is false" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts",
                        identifier = Identifier("Maven", "com.example", "example-lib", "1.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "unresolved npm issue",
                        severity = Severity.WARNING,
                        identifier = Identifier("Maven", "com.example", "unresolved-lib", "3.0.0")
                    )
                )

                val resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = "dependency not found.*",
                            reason = IssueResolutionReason.CANT_FIX_ISSUE,
                            comment = "This is a known issue with the external dependency repository"
                        )
                    )
                )

                val ortRun = createOrtRunWithIssueResolutions(repositoryId, issues, resolutions)

                val result = service.listForOrtRunId(ortRun.id, issuesFilter = IssueFilter(resolved = false))

                result.data.size shouldBe 1
                result.totalCount shouldBe 1
                result.data[0].message shouldContain "unresolved npm issue"
                result.data[0].resolutions.size shouldBe 0
            }

            "return all issues when issuesFilter.resolved is null" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts",
                        identifier = Identifier("Maven", "com.example", "example-lib", "1.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "unresolved npm issue",
                        severity = Severity.WARNING,
                        affectedPath = "src/test/kotlin",
                        identifier = Identifier("Maven", "com.example", "unresolved-lib", "3.0.0")
                    )
                )

                val resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = "dependency not found.*",
                            reason = IssueResolutionReason.CANT_FIX_ISSUE,
                            comment = "This is a known issue with the external dependency repository"
                        )
                    )
                )

                val ortRun = createOrtRunWithIssueResolutions(repositoryId, issues, resolutions)

                val result = service.listForOrtRunId(ortRun.id, issuesFilter = IssueFilter(resolved = null))

                result.data.size shouldBe 2
                result.totalCount shouldBe 2

                val resolvedIssue = result.data.single { "dependency not found" in it.message }
                resolvedIssue.resolutions.size shouldBe 1

                val unresolvedIssue = result.data.single { "unresolved npm issue" in it.message }
                unresolvedIssue.resolutions.size shouldBe 0
            }

            "return purl for issues that are related to packages" {
                val repositoryId = fixtures.createRepository().id

                val pkg = fixtures.generatePackage(Identifier("Maven", "com.example", "example-lib", "1.0.0"))
                val project = fixtures.getProject()

                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Gradle Inspector",
                        message = "could not resolve org.example:example-tool:1.0.0 from project :example",
                        severity = Severity.ERROR,
                        affectedPath = "example",
                        identifier = project.identifier
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts",
                        identifier = pkg.identifier
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                val analyzerJob = fixtures.createAnalyzerJob(ortRun.id)
                fixtures.createAnalyzerRun(analyzerJob.id, setOf(project), setOf(pkg))

                val result = service.listForOrtRunId(ortRun.id)

                result.data.size shouldBe 2
                result.totalCount shouldBe 2

                val couldNotResolveIssue = result.data.single { "could not resolve" in it.message }
                couldNotResolveIssue.purl shouldBe null

                val dependencyIssue = result.data.single { "dependency not found" in it.message }
                dependencyIssue.purl shouldBe pkg.purl
            }

            "sort by timestamp descending by default" {
                val repositoryId = fixtures.createRepository().id
                val now = Clock.System.now()
                val issues = listOf(
                    Issue(
                        timestamp = now,
                        source = "A",
                        message = "middle",
                        severity = Severity.ERROR
                    ),
                    Issue(
                        timestamp = now.plus(2.seconds),
                        source = "B",
                        message = "newest",
                        severity = Severity.WARNING
                    ),
                    Issue(
                        timestamp = now.minus(2.seconds),
                        source = "C",
                        message = "oldest",
                        severity = Severity.HINT
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                val result = service.listForOrtRunId(ortRun.id)

                result.data shouldHaveSize 3
                result.data[0].message shouldBe "newest"
                result.data[1].message shouldBe "middle"
                result.data[2].message shouldBe "oldest"
            }

            "sort by timestamp ascending" {
                val repositoryId = fixtures.createRepository().id
                val now = Clock.System.now()
                val issues = listOf(
                    Issue(
                        timestamp = now,
                        source = "A",
                        message = "middle",
                        severity = Severity.ERROR
                    ),
                    Issue(
                        timestamp = now.plus(2.seconds),
                        source = "B",
                        message = "newest",
                        severity = Severity.WARNING
                    ),
                    Issue(
                        timestamp = now.minus(2.seconds),
                        source = "C",
                        message = "oldest",
                        severity = Severity.HINT
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)
                val params = ListQueryParameters(
                    sortFields = listOf(OrderField("timestamp", OrderDirection.ASCENDING))
                )

                val result = service.listForOrtRunId(ortRun.id, params)

                result.data shouldHaveSize 3
                result.data[0].message shouldBe "oldest"
                result.data[1].message shouldBe "middle"
                result.data[2].message shouldBe "newest"
            }

            "sort by severity" {
                val repositoryId = fixtures.createRepository().id
                val now = Clock.System.now()
                val issues = listOf(
                    Issue(
                        timestamp = now,
                        source = "A",
                        message = "warning",
                        severity = Severity.WARNING
                    ),
                    Issue(
                        timestamp = now.plus(1.seconds),
                        source = "B",
                        message = "error",
                        severity = Severity.ERROR
                    ),
                    Issue(
                        timestamp = now.plus(2.seconds),
                        source = "C",
                        message = "hint",
                        severity = Severity.HINT
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)
                val params = ListQueryParameters(
                    sortFields = listOf(OrderField("severity", OrderDirection.ASCENDING))
                )

                val result = service.listForOrtRunId(ortRun.id, params)

                result.data shouldHaveSize 3
                // Severity is stored as enumerationByName, so SQL sorts alphabetically:
                // ERROR < HINT < WARNING
                result.data.map { it.severity } shouldBe
                    listOf(Severity.ERROR, Severity.HINT, Severity.WARNING)
            }

            "sort by source" {
                val repositoryId = fixtures.createRepository().id
                val now = Clock.System.now()
                val issues = listOf(
                    Issue(
                        timestamp = now,
                        source = "Maven",
                        message = "issue 1",
                        severity = Severity.ERROR
                    ),
                    Issue(
                        timestamp = now.plus(1.seconds),
                        source = "Analyzer",
                        message = "issue 2",
                        severity = Severity.ERROR
                    ),
                    Issue(
                        timestamp = now.plus(2.seconds),
                        source = "Scanner",
                        message = "issue 3",
                        severity = Severity.ERROR
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)
                val params = ListQueryParameters(
                    sortFields = listOf(OrderField("source", OrderDirection.ASCENDING))
                )

                val result = service.listForOrtRunId(ortRun.id, params)

                result.data shouldHaveSize 3
                result.data.map { it.source } shouldBe listOf("Analyzer", "Maven", "Scanner")
            }

            "sort by multiple fields" {
                val repositoryId = fixtures.createRepository().id
                val now = Clock.System.now()
                val issues = listOf(
                    Issue(
                        timestamp = now,
                        source = "A",
                        message = "error-older",
                        severity = Severity.ERROR
                    ),
                    Issue(
                        timestamp = now.plus(1.seconds),
                        source = "B",
                        message = "error-newer",
                        severity = Severity.ERROR
                    ),
                    Issue(
                        timestamp = now.plus(2.seconds),
                        source = "C",
                        message = "warning",
                        severity = Severity.WARNING
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                // Sort by severity DESC (alphabetically: WARNING > HINT > ERROR), then timestamp ASC.
                val params = ListQueryParameters(
                    sortFields = listOf(
                        OrderField("severity", OrderDirection.DESCENDING),
                        OrderField("timestamp", OrderDirection.ASCENDING)
                    )
                )

                val result = service.listForOrtRunId(ortRun.id, params)

                result.data shouldHaveSize 3
                result.data[0].message shouldBe "warning"
                result.data[1].message shouldBe "error-older"
                result.data[2].message shouldBe "error-newer"
            }

            "apply pagination with limit and offset" {
                val repositoryId = fixtures.createRepository().id
                val now = Clock.System.now()
                val issues = (1..5).map { i ->
                    Issue(
                        timestamp = now.plus(i.seconds),
                        source = "Source",
                        message = "issue $i",
                        severity = Severity.ERROR
                    )
                }

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                // Sort ascending by timestamp, take page of 2 starting at offset 1.
                val params = ListQueryParameters(
                    sortFields = listOf(OrderField("timestamp", OrderDirection.ASCENDING)),
                    limit = 2,
                    offset = 1
                )

                val result = service.listForOrtRunId(ortRun.id, params)

                result.totalCount shouldBe 5
                result.data shouldHaveSize 2
                result.data[0].message shouldBe "issue 2"
                result.data[1].message shouldBe "issue 3"
            }

            "return empty list for ORT run with no issues" {
                val repositoryId = fixtures.createRepository().id
                val ortRun = fixtures.createOrtRun(repositoryId)

                val result = service.listForOrtRunId(ortRun.id)

                result.data shouldHaveSize 0
                result.totalCount shouldBe 0
            }

            "handle issues with null identifier, worker, and affectedPath" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Analyzer",
                        message = "issue with nulls",
                        severity = Severity.ERROR,
                        affectedPath = null,
                        identifier = null,
                        worker = null
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                val result = service.listForOrtRunId(ortRun.id)

                result.data shouldHaveSize 1
                result.data[0].identifier shouldBe null
                result.data[0].worker shouldBe null
                result.data[0].affectedPath shouldBe null
                result.data[0].purl shouldBe null
            }

            "throw QueryParametersException for unknown sort field" {
                val repositoryId = fixtures.createRepository().id
                val ortRun = createOrtRunWithIssues(repositoryId, generateIssues())

                val params = ListQueryParameters(
                    sortFields = listOf(OrderField("nonExistent", OrderDirection.ASCENDING))
                )

                shouldThrow<QueryParametersException> {
                    service.listForOrtRunId(ortRun.id, params)
                }
            }

            "return totalCount reflecting resolved filter, not full issue count" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "timeout scanning package",
                        severity = Severity.WARNING
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(2.seconds),
                        source = "Scanner",
                        message = "missing license info",
                        severity = Severity.WARNING
                    )
                )

                val resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = "dependency not found.*",
                            reason = IssueResolutionReason.CANT_FIX_ISSUE,
                            comment = "Known"
                        )
                    )
                )

                val ortRun = createOrtRunWithIssueResolutions(repositoryId, issues, resolutions)

                // Paginate unresolved with limit 1 — totalCount should still be 2 (all unresolved).
                val params = ListQueryParameters(limit = 1)
                val result = service.listForOrtRunId(
                    ortRun.id,
                    params,
                    issuesFilter = IssueFilter(resolved = false)
                )

                result.data shouldHaveSize 1
                result.totalCount shouldBe 2
            }
        }

        "countForOrtRunIds" should {
            "return issue count for ORT run" {
                val ortRun = createOrtRunWithIssues()

                service.countForOrtRunIds(ortRun.id) shouldBe 4
            }

            "return count of issues found in ORT runs" {
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

                service.countForOrtRunIds(ortRun1Id, ortRun2Id) shouldBe 9
            }
        }

        "countIssuesBySeverityForOrtRunIds" should {
            "return the counts per severity for issues found in ORT runs" {
                val repositoryId = fixtures.createRepository().id

                val ortRun1Id = createOrtRunWithIssues(
                    repositoryId,
                    generateIssues().plus(
                        Issue(
                            timestamp = Clock.System.now(),
                            source = "Evaluator",
                            message = "Issue",
                            severity = Severity.HINT,
                            affectedPath = "path",
                            identifier = Identifier("Maven", "com.example", "example", "1.0")
                        )
                    )
                ).id
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

                val severitiesToCounts = service.countBySeverityForOrtRunIds(ortRun1Id, ortRun2Id)

                severitiesToCounts.map.size shouldBe Severity.entries.size
                severitiesToCounts.map.keys shouldContainExactlyInAnyOrder Severity.entries
                severitiesToCounts.getCount(Severity.HINT) shouldBe 1
                severitiesToCounts.getCount(Severity.WARNING) shouldBe 3
                severitiesToCounts.getCount(Severity.ERROR) shouldBe 6
            }

            "return counts by severity that sum up to the count returned by countForOrtRunIds" {
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

                val severitiesToCounts = service.countBySeverityForOrtRunIds(ortRun1Id, ortRun2Id)
                val count = service.countForOrtRunIds(ortRun1Id, ortRun2Id)

                severitiesToCounts.map.values.sum() shouldBe count
            }

            "include counts of 0 for severities that are not found in issues" {
                val repositoryId = fixtures.createRepository().id
                val ortRunId = fixtures.createOrtRun(repositoryId).id

                val severitiesToCounts = service.countBySeverityForOrtRunIds(ortRunId)

                severitiesToCounts.map.keys shouldContainExactlyInAnyOrder Severity.entries
                severitiesToCounts.map.values.sum() shouldBe 0
            }
        }

        "countUnresolvedForOrtRunIds" should {
            "return all issues when none are resolved" {
                val ortRun = createOrtRunWithIssues()

                service.countUnresolvedForOrtRunIds(ortRun.id) shouldBe 4
            }

            "return count of unresolved issues when some are resolved" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts",
                        identifier = Identifier("Maven", "com.example", "example-lib", "1.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "timeout while scanning package",
                        severity = Severity.WARNING,
                        affectedPath = "src/main/kotlin",
                        identifier = Identifier("Maven", "com.example", "scanner-test", "2.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(2.seconds),
                        source = "Scanner",
                        message = "unresolved npm issue",
                        severity = Severity.WARNING,
                        affectedPath = "src/test/kotlin",
                        identifier = Identifier("Maven", "com.example", "unresolved-lib", "3.0.0")
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                // Total issues = 3
                service.countForOrtRunIds(ortRun.id) shouldBe 3

                // Resolve one issue
                val resolutionToApply = IssueResolution(
                    message = "dependency not found.*",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "This is a known issue"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(
                        issues = mapOf(issues[0] to listOf(resolutionToApply))
                    )
                )

                // Unresolved should be 2 (total 3 - resolved 1)
                service.countUnresolvedForOrtRunIds(ortRun.id) shouldBe 2
            }

            "return zero when all issues are resolved" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts",
                        identifier = Identifier("Maven", "com.example", "example-lib", "1.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "timeout error",
                        severity = Severity.WARNING,
                        affectedPath = "src",
                        identifier = Identifier("Maven", "com.example", "test", "1.0.0")
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                val resolution1 = IssueResolution(
                    message = "dependency not found.*",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "Known issue"
                )
                val resolution2 = IssueResolution(
                    message = "timeout error",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "Known issue"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(
                        issues = mapOf(
                            issues[0] to listOf(resolution1),
                            issues[1] to listOf(resolution2)
                        )
                    )
                )

                service.countUnresolvedForOrtRunIds(ortRun.id) shouldBe 0
            }

            "return count of unresolved issues found in ORT runs" {
                val repositoryId = fixtures.createRepository().id

                val issues1 = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "issue 1",
                        severity = Severity.ERROR,
                        affectedPath = "path"
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "issue 2",
                        severity = Severity.WARNING,
                        affectedPath = "path"
                    )
                )

                val issues2 = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Scanner",
                        message = "issue 3",
                        severity = Severity.ERROR,
                        affectedPath = "path"
                    )
                )

                val ortRun1 = createOrtRunWithIssues(repositoryId, issues1)
                val ortRun2 = createOrtRunWithIssues(repositoryId, issues2)

                // Resolve one issue in ortRun1
                val resolution = IssueResolution(
                    message = "issue 1",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "Known issue"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun1.id,
                    ResolvedItemsResult(
                        issues = mapOf(issues1[0] to listOf(resolution))
                    )
                )

                // Total unresolved across both runs should be 2 (1 from ortRun1 + 1 from ortRun2)
                service.countUnresolvedForOrtRunIds(ortRun1.id, ortRun2.id) shouldBe 2
            }
        }

        "countUnresolvedBySeverityForOrtRunIds" should {
            "return counts by severity for unresolved issues" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "dependency not found: example-lib",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts",
                        identifier = Identifier("Maven", "com.example", "example-lib", "1.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "timeout while scanning package",
                        severity = Severity.WARNING,
                        affectedPath = "src/main/kotlin",
                        identifier = Identifier("Maven", "com.example", "scanner-test", "2.0.0")
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(2.seconds),
                        source = "Scanner",
                        message = "unresolved npm issue",
                        severity = Severity.WARNING,
                        affectedPath = "src/test/kotlin",
                        identifier = Identifier("Maven", "com.example", "unresolved-lib", "3.0.0")
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                // Resolve the ERROR issue
                val resolution = IssueResolution(
                    message = "dependency not found.*",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "Known issue"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(
                        issues = mapOf(issues[0] to listOf(resolution))
                    )
                )

                val severitiesToCounts = service.countUnresolvedBySeverityForOrtRunIds(ortRun.id)

                severitiesToCounts.map.keys shouldContainExactlyInAnyOrder Severity.entries
                severitiesToCounts.getCount(Severity.ERROR) shouldBe 0
                severitiesToCounts.getCount(Severity.WARNING) shouldBe 2
                severitiesToCounts.getCount(Severity.HINT) shouldBe 0
            }

            "return sum that equals countUnresolvedForOrtRunIds" {
                val repositoryId = fixtures.createRepository().id
                val issues = listOf(
                    Issue(
                        timestamp = Clock.System.now(),
                        source = "Maven",
                        message = "error issue",
                        severity = Severity.ERROR,
                        affectedPath = "build.gradle.kts"
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(1.seconds),
                        source = "NPM",
                        message = "warning issue",
                        severity = Severity.WARNING,
                        affectedPath = "src"
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(2.seconds),
                        source = "Scanner",
                        message = "another warning",
                        severity = Severity.WARNING,
                        affectedPath = "test"
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                // Resolve one warning issue
                val resolution = IssueResolution(
                    message = "warning issue",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "Known issue"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(
                        issues = mapOf(issues[1] to listOf(resolution))
                    )
                )

                val severitiesToCounts = service.countUnresolvedBySeverityForOrtRunIds(ortRun.id)
                val unresolvedCount = service.countUnresolvedForOrtRunIds(ortRun.id)

                severitiesToCounts.map.values.sum() shouldBe unresolvedCount
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
                timestamp = Clock.System.now().plus(1.seconds),
                source = "Advisor",
                message = "Issue 2",
                severity = Severity.ERROR,
                affectedPath = "path",
                identifier = Identifier("Maven", "com.example", "example", "1.0")
            ),
            Issue(
                timestamp = Clock.System.now().minus(1.seconds),
                source = "Advisor",
                message = "Issue 3",
                severity = Severity.WARNING,
                affectedPath = "path",
                identifier = Identifier("Maven", "com.example", "example", "1.1")
            ),
            Issue(
                timestamp = Clock.System.now().minus(2.seconds),
                source = "scanner",
                message = "Issue 4",
                severity = Severity.ERROR,
                affectedPath = "package/dist/somefile"
            )
        )

    private fun createOrtRunWithIssueResolutions(
        repositoryId: Long,
        issues: List<Issue>,
        resolutions: Resolutions
    ): OrtRun {
        val ortRun = fixtures.createOrtRun(repositoryId, "revision", JobConfigurations())

        val analyzerJob = fixtures.createAnalyzerJob(
            ortRunId = ortRun.id,
            configuration = AnalyzerJobConfiguration()
        )

        fixtures.analyzerRunRepository.create(
            analyzerJobId = analyzerJob.id,
            startTime = Clock.System.now().toDatabasePrecision(),
            endTime = Clock.System.now().toDatabasePrecision(),
            environment = Environment(
                ortVersion = "1.0",
                javaVersion = "11.0.16",
                os = "Linux",
                processors = 8,
                maxMemory = 8321499136,
                variables = emptyMap()
            ),
            config = AnalyzerConfiguration(
                allowDynamicVersions = false,
                enabledPackageManagers = emptyList(),
                disabledPackageManagers = null,
                packageManagers = emptyMap(),
                skipExcluded = false
            ),
            projects = emptySet(),
            packages = emptySet(),
            issues = issues,
            dependencyGraphs = emptyMap()
        )

        // Match issues to resolutions by pattern and create ResolvedItemsResult
        val issueToResolutions = issues.associateWith { issue ->
            resolutions.issues.filter { resolution ->
                resolution.message.toRegex().containsMatchIn(issue.message)
            }
        }.filterValues { it.isNotEmpty() }

        fixtures.resolvedConfigurationRepository.addResolutions(
            ortRun.id,
            ResolvedItemsResult(issues = issueToResolutions)
        )

        return ortRun
    }

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
