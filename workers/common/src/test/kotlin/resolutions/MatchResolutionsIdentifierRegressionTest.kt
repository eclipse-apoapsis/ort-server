/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common.resolutions

import com.github.michaelbull.result.Ok

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.runs.IssueFilter
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution as ServerIssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason as ServerIssueResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.services.ortrun.IssueService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel

import org.jetbrains.exposed.v1.jdbc.Database

import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Issue as OrtIssue
import org.ossreviewtoolkit.model.Severity as OrtSeverity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.Resolutions

/**
 * A regression test for the https://github.com/eclipse-apoapsis/ort-server/issues/5271.
 *
 * Before that fix, [OrtServerResolutionProvider.matchResolutions] mapped resolved issues to keys that never carried
 * an [org.eclipse.apoapsis.ortserver.model.runs.Identifier], no matter which [org.ossreviewtoolkit.model.Identifier]
 * they were associated with. As `DaoResolvedConfigurationRepository.findOrtRunIssueId` (used by
 * [OrtRunService.storeResolvedItems]) matches stored issues by identifier as well, this caused resolutions for
 * issues that do have an identifier to not be attached to the correct issue, so that they kept appearing as
 * unresolved in the UI despite having a matching resolution.
 */
class MatchResolutionsIdentifierRegressionTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures
    lateinit var ortRunService: OrtRunService
    lateinit var issueService: IssueService

    var ortRunId = -1L

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures
        ortRunId = fixtures.ortRun.id

        ortRunService = OrtRunService(
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

        // No resolutions managed by the server are used in these tests, so the resolution service can just return
        // an empty list.
        val issueResolutionService = mockk<IssueResolutionService> {
            every { getResolutionsForRepository(any()) } returns Ok(emptyList())
        }
        issueService = IssueService(db, ortRunService, issueResolutionService)
    }

    val resolutionMessage = "resolvable issue message"

    val provider = OrtServerResolutionProvider(
        globalResolutions = Resolutions(
            issues = listOf(
                IssueResolution(
                    message = resolutionMessage,
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "matching global issue resolution"
                )
            )
        ),
        repositoryConfigurationResolutions = Resolutions(),
        managedIssueResolutions = emptyList(),
        managedRuleViolationResolutions = emptyList(),
        managedVulnerabilityResolutions = emptyList()
    )

    val expectedResolution = ServerIssueResolution(
        message = resolutionMessage,
        reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
        comment = "matching global issue resolution",
        source = ResolutionSource.GLOBAL_FILE
    )

    "matchResolutions combined with storeResolvedItems" should {
        "resolve an issue that has no identifier" {
            val issue = OrtIssue(
                source = "Analyzer",
                message = resolutionMessage,
                severity = OrtSeverity.WARNING
            )

            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                issues = listOf(issue.mapToModel())
            )

            val resolvedItems = provider.matchResolutions(
                issuesByIdentifier = mapOf(OrtIdentifier.EMPTY to listOf(issue)),
                ruleViolations = emptyList(),
                vulnerabilities = emptyList()
            )

            resolvedItems.issues.keys shouldHaveSize 1
            resolvedItems.issues.keys.single().identifier should beNull()

            ortRunService.storeResolvedItems(ortRunId, resolvedItems)

            // Verify that the unique resolution was stored in the resolved configuration.
            val resolvedConfiguration = fixtures.resolvedConfigurationRepository.getForOrtRun(ortRunId)
            resolvedConfiguration.shouldNotBeNull()
            resolvedConfiguration.resolutions.issues should containExactly(expectedResolution)

            // Verify that the issue itself is now reported as resolved (as it would show up in the UI).
            val resolvedIssues = issueService.listForOrtRunId(ortRunId, issuesFilter = IssueFilter(resolved = true))
            resolvedIssues.data shouldHaveSize 1
            with(resolvedIssues.data.single()) {
                message shouldBe resolutionMessage
                identifier should beNull()
            }
        }

        "resolve an issue that has an identifier" {
            val identifier = OrtIdentifier("Maven", "com.example", "package-with-id", "1.0")
            val issue = OrtIssue(
                source = "Analyzer",
                message = resolutionMessage,
                severity = OrtSeverity.WARNING
            )

            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                issues = listOf(issue.mapToModel(identifier = identifier.mapToModel()))
            )

            val resolvedItems = provider.matchResolutions(
                issuesByIdentifier = mapOf(identifier to listOf(issue)),
                ruleViolations = emptyList(),
                vulnerabilities = emptyList()
            )

            resolvedItems.issues.keys shouldHaveSize 1
            resolvedItems.issues.keys.single().identifier shouldBe identifier.mapToModel()

            ortRunService.storeResolvedItems(ortRunId, resolvedItems)

            // Verify that the unique resolution was stored in the resolved configuration.
            val resolvedConfiguration = fixtures.resolvedConfigurationRepository.getForOrtRun(ortRunId)
            resolvedConfiguration.shouldNotBeNull()
            resolvedConfiguration.resolutions.issues should containExactly(expectedResolution)

            // Without the fix, the resolved issue's key does not carry an identifier, so
            // DaoResolvedConfigurationRepository.findOrtRunIssueId() fails to find the stored issue (which does
            // have an identifier), and the issue keeps appearing as unresolved.
            val resolvedIssues = issueService.listForOrtRunId(ortRunId, issuesFilter = IssueFilter(resolved = true))
            resolvedIssues.data shouldHaveSize 1
            with(resolvedIssues.data.single()) {
                message shouldBe resolutionMessage
                this.identifier shouldBe identifier.mapToModel()
            }
        }

        "resolve issues with and without an identifier that share the same message" {
            val identifier = OrtIdentifier("Maven", "com.example", "package-with-id", "1.0")

            // Both issues use the very same message, source, severity and timestamp so that, without the fix, they
            // would be mapped to the very same key by matchResolutions() and only one of them would be resolved.
            val issueWithIdentifier = OrtIssue(
                source = "Analyzer",
                message = resolutionMessage,
                severity = OrtSeverity.WARNING
            )
            val issueWithoutIdentifier = issueWithIdentifier.copy()

            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                issues = listOf(
                    issueWithIdentifier.mapToModel(identifier = identifier.mapToModel()),
                    issueWithoutIdentifier.mapToModel()
                )
            )

            val resolvedItems = provider.matchResolutions(
                issuesByIdentifier = mapOf(
                    identifier to listOf(issueWithIdentifier),
                    OrtIdentifier.EMPTY to listOf(issueWithoutIdentifier)
                ),
                ruleViolations = emptyList(),
                vulnerabilities = emptyList()
            )

            // Both issues must be present as distinct keys in the resolved items result, distinguished by their
            // identifier.
            resolvedItems.issues.keys shouldHaveSize 2
            resolvedItems.issues.keys.mapNotNull { it.identifier } shouldHaveSize 1
            resolvedItems.issues.keys.mapNotNull { it.identifier }.single() shouldBe identifier.mapToModel()

            ortRunService.storeResolvedItems(ortRunId, resolvedItems)

            // Verify that the unique resolution was stored only once in the resolved configuration, even though it
            // applies to both issues.
            val resolvedConfiguration = fixtures.resolvedConfigurationRepository.getForOrtRun(ortRunId)
            resolvedConfiguration.shouldNotBeNull()
            resolvedConfiguration.resolutions.issues should containExactly(expectedResolution)

            // Both issues (the one with and the one without an identifier) must have been resolved.
            val resolvedIssues = issueService.listForOrtRunId(ortRunId, issuesFilter = IssueFilter(resolved = true))
            resolvedIssues.data shouldHaveSize 2
            resolvedIssues.data.mapNotNull { it.identifier } shouldHaveSize 1
            resolvedIssues.data.mapNotNull { it.identifier }.single() shouldBe identifier.mapToModel()
            resolvedIssues.data.map { it.message } shouldBe listOf(resolutionMessage, resolutionMessage)
        }
    }
})
