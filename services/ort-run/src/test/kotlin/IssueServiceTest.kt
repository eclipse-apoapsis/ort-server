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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

import io.mockk.mockk

import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.IssueFilter
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.sql.Database

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
                            reason = "CANT_FIX_ISSUE",
                            comment = "This is a known issue with the external dependency repository"
                        ),
                        IssueResolution(
                            message = "timeout while scanning.*",
                            reason = "CANT_FIX_ISSUE",
                            comment = "Timeout issues are acceptable for this type of scanning"
                        )
                    )
                )

                val ortRun = createOrtRunWithIssueResolutions(repositoryId, issues, resolutions)

                val result = service.listForOrtRunId(ortRun.id)

                result.data.size shouldBe 3
                result.totalCount shouldBe 3

                val dependencyIssue = result.data.single { it.message.contains("dependency not found") }
                dependencyIssue.resolutions.size shouldBe 1
                dependencyIssue.resolutions[0].message shouldStartWith "dependency not found"

                val timeoutIssue = result.data.single { it.message.contains("timeout while scanning") }
                timeoutIssue.resolutions.size shouldBe 1
                timeoutIssue.resolutions[0].message shouldStartWith "timeout while scanning"

                val unresolvedIssue = result.data.single { it.message.contains("unresolved npm issue") }
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
                            reason = "CANT_FIX_ISSUE",
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
                            reason = "CANT_FIX_ISSUE",
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
                            reason = "CANT_FIX_ISSUE",
                            comment = "This is a known issue with the external dependency repository"
                        )
                    )
                )

                val ortRun = createOrtRunWithIssueResolutions(repositoryId, issues, resolutions)

                val result = service.listForOrtRunId(ortRun.id, issuesFilter = IssueFilter(resolved = null))

                result.data.size shouldBe 2
                result.totalCount shouldBe 2

                val resolvedIssue = result.data.single { it.message.contains("dependency not found") }
                resolvedIssue.resolutions.size shouldBe 1

                val unresolvedIssue = result.data.single { it.message.contains("unresolved npm issue") }
                unresolvedIssue.resolutions.size shouldBe 0
            }

            "return purl for issues that are related to packages" {
                val repositoryId = fixtures.createRepository().id

                val pkg1 = fixtures.generatePackage(Identifier("Maven", "com.example", "example-lib", "1.0.0"))
                val pkg2 = fixtures.generatePackage(Identifier("Maven", "com.example", "scanner-test", "2.0.0"))
                val pkg3 = fixtures.generatePackage(Identifier("Maven", "com.example", "unresolved-lib", "3.0.0"))
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
                        identifier = pkg1.identifier
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(2.seconds),
                        source = "NPM",
                        message = "timeout while scanning package",
                        severity = Severity.WARNING,
                        affectedPath = "src/main/kotlin",
                        identifier = pkg2.identifier
                    ),
                    Issue(
                        timestamp = Clock.System.now().plus(3.seconds),
                        source = "Scanner",
                        message = "unresolved npm issue",
                        severity = Severity.WARNING,
                        affectedPath = "src/test/kotlin",
                        identifier = pkg3.identifier
                    )
                )

                val ortRun = createOrtRunWithIssues(repositoryId, issues)

                val analyzerJob = fixtures.createAnalyzerJob(ortRun.id)

                fixtures.createAnalyzerRun(analyzerJob.id, setOf(project), setOf(pkg1, pkg2, pkg3))

                val higherPriorityCurations = ResolvedPackageCurations(
                    provider = PackageCurationProviderConfig("provider1"),
                    curations = listOf(
                        PackageCuration(
                            id = pkg2.identifier,
                            data = PackageCurationData(purl = "curated")
                        ),
                        PackageCuration(
                            id = pkg3.identifier,
                            data = PackageCurationData(purl = "curated-higher")
                        )
                    )
                )

                val lowerPriorityCurations = ResolvedPackageCurations(
                    provider = PackageCurationProviderConfig("provider2"),
                    curations = listOf(
                        PackageCuration(
                            id = pkg3.identifier,
                            data = PackageCurationData(purl = "curated-lower")
                        )
                    )
                )

                fixtures.resolvedConfigurationRepository.addPackageCurations(
                    ortRun.id,
                    listOf(higherPriorityCurations, lowerPriorityCurations)
                )

                val result = service.listForOrtRunId(ortRun.id)

                result.data.size shouldBe 4
                result.totalCount shouldBe 4

                val couldNotResolveIssue = result.data.single { it.message.contains("could not resolve") }
                couldNotResolveIssue.purl shouldBe null

                val dependencyIssue = result.data.single { it.message.contains("dependency not found") }
                dependencyIssue.purl shouldBe pkg1.purl

                val timeoutIssue = result.data.single { it.message.contains("timeout while scanning") }
                timeoutIssue.purl shouldBe "curated"

                val unresolvedIssue = result.data.single { it.message.contains("unresolved npm issue") }
                unresolvedIssue.purl shouldBe "curated-higher"
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
                    reason = "CANT_FIX_ISSUE",
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
                    reason = "CANT_FIX_ISSUE",
                    comment = "Known issue"
                )
                val resolution2 = IssueResolution(
                    message = "timeout error",
                    reason = "CANT_FIX_ISSUE",
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
                    reason = "CANT_FIX_ISSUE",
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
                    reason = "CANT_FIX_ISSUE",
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
                    reason = "CANT_FIX_ISSUE",
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

        "List<Issue>.sort" should {
            "sort issues by timestamp in ascending order" {
                val issues = generateIssues()

                val sortedIssues = issues.sort(listOf(OrderField("timestamp", OrderDirection.ASCENDING)))

                sortedIssues shouldBe issues.sortedWith(compareBy<Issue> { it.timestamp }.thenBy { it.hashCode() })
            }

            "sort issues by timestamp in descending order" {
                val issues = generateIssues()

                val sortedIssues = issues.sort(listOf(OrderField("timestamp", OrderDirection.DESCENDING)))

                sortedIssues shouldBe issues.sortedWith(
                    compareBy<Issue> { it.timestamp }.thenBy { it.hashCode() }
                ).reversed()
            }

            "sort issues by severity in ascending order" {
                val issues = generateIssues()

                val sortedIssues = issues.sort(listOf(OrderField("severity", OrderDirection.ASCENDING)))

                sortedIssues shouldBe issues.sortedWith(compareBy<Issue> { it.severity }.thenBy { it.hashCode() })
            }

            "sort issues by severity in descending order" {
                val issues = generateIssues()

                val sortedIssues = issues.sort(listOf(OrderField("severity", OrderDirection.DESCENDING)))

                sortedIssues shouldBe issues.sortedWith(
                    compareBy<Issue> { it.severity }.thenBy { it.hashCode() }
                ).reversed()
            }

            "sort issues by identifier in ascending order" {
                val issues = generateIssues()

                val sortedIssues = issues.sort(listOf(OrderField("identifier", OrderDirection.ASCENDING)))

                sortedIssues shouldBe issues.sortedWith(
                    compareBy<Issue> { it.identifier?.toConcatenatedString() }.thenBy { it.hashCode() }
                )
            }

            "sort issues by identifier in descending order" {
                val issues = generateIssues()

                val sortedIssues = issues.sort(listOf(OrderField("identifier", OrderDirection.DESCENDING)))

                sortedIssues shouldBe issues.sortedWith(
                    compareBy<Issue> { it.identifier?.toConcatenatedString() }.thenBy { it.hashCode() }
                ).reversed()
            }
        }

        "List<Issue>.paginate" should {
            "apply default pagination when offset and limit are null" {
                val issues = generateIssues()
                val parameters = ListQueryParameters()

                val paginatedIssues = issues.paginate(parameters)

                paginatedIssues.size shouldBe issues.size.coerceAtMost(ListQueryParameters.DEFAULT_LIMIT)
            }

            "return the first issue when offset is 0 and limit is 1" {
                val issues = generateIssues()
                val parameters = ListQueryParameters(offset = 0, limit = 1)

                val paginatedIssues = issues.paginate(parameters)

                paginatedIssues.size shouldBe 1
                paginatedIssues[0] shouldBe issues[0]
            }

            "return the second issue when offset is 1 and limit is 1" {
                val issues = generateIssues()
                val parameters = ListQueryParameters(offset = 1, limit = 1)

                val paginatedIssues = issues.paginate(parameters)

                paginatedIssues.size shouldBe 1
                paginatedIssues[0] shouldBe issues[1]
            }

            "return the second and third issue when offset is 1 and limit is 2" {
                val issues = generateIssues()
                val parameters = ListQueryParameters(offset = 1, limit = 2)

                val paginatedIssues = issues.paginate(parameters)

                paginatedIssues.size shouldBe 2
                paginatedIssues[0] shouldBe issues[1]
                paginatedIssues[1] shouldBe issues[2]
            }

            "return an empty list when offset is equal to the size of the list" {
                val issues = generateIssues()
                val parameters = ListQueryParameters(offset = issues.size.toLong(), limit = 1)

                val paginatedIssues = issues.paginate(parameters)

                paginatedIssues shouldBe emptyList()
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
