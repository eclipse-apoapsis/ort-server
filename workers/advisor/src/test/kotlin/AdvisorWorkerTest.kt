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

package org.eclipse.apoapsis.ortserver.workers.advisor

import io.kotest.assertions.AssertionErrorBuilder
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll

import java.io.File
import java.net.URI

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.shared.orttestdata.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.AnalyzerRun as OrtAnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.utils.common.enumSetOf

private const val ORT_SERVER_MAPPINGS_FILE = "org.eclipse.apoapsis.ortserver.services.ortrun.OrtServerMappingsKt"

private const val ANALYZER_JOB_ID = 1L
private const val ADVISOR_JOB_ID = 1L
private const val ORGANIZATION_ID = 1L
private const val REPOSITORY_ID = 1L
private const val ORT_RUN_ID = 12L
private const val TRACE_ID = "42"
private const val TIME_STAMP_SECONDS = 1678119934L

private val advisorJob = AdvisorJob(
    id = ADVISOR_JOB_ID,
    ortRunId = 12,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = AdvisorJobConfiguration(),
    status = JobStatus.CREATED,
    errorMessage = null
)

/**
 * Return an [AdvisorRunner] to be used by tests.
 */
private fun createRunner(): AdvisorRunner = AdvisorRunner()

class AdvisorWorkerTest : StringSpec({
    afterSpec {
        unmockkAll()
    }

    "A project should be advised successfully" {
        val ortResult = OrtResult.EMPTY.copy(
            analyzer = OrtAnalyzerRun.EMPTY
        )

        val ortRun = mockOrtRun()

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns ortResult

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns advisorJob
            every { getAnalyzerRunForOrtRun(any()) } returns OrtTestData.analyzerRun.mapToModel(ANALYZER_JOB_ID)
            every { startAdvisorJob(any()) } returns advisorJob
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { storeAdvisorRun(any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
        }

        val context = mockk<WorkerContext> {
            every { this@mockk.ortRun } returns ortRun
            every { this@mockk.configManager } returns mockConfigManager()
            coEvery { resolvePluginConfigSecrets(any()) } returns emptyMap()
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(createRunner())
        val worker = AdvisorWorker(mockk(), runner, ortRunService, contextFactory, mockk(relaxed = true))

        mockkTransaction {
            val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            coVerify(exactly = 1) {
                runner.run(context, ortResult, advisorJob.configuration)
                ortRunService.storeAdvisorRun(withArg { it.advisorJobId shouldBe ADVISOR_JOB_ID })
            }
        }
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } throws testException
        }

        val worker = AdvisorWorker(
            mockk(),
            createRunner(),
            ortRunService,
            mockContextFactory(),
            mockk(relaxed = true)
        )

        mockkTransaction {
            when (val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> AssertionErrorBuilder.fail("Unexpected result: $result")
            }
        }
    }

    "A 'success' result should be returned if the advisor run finished with resolved issues" {
        val ortResult = OrtTestData.result

        val ortRun = mockOrtRun()

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns ortResult

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns advisorJob
            every { getAnalyzerRunForOrtRun(any()) } returns OrtTestData.analyzerRun.mapToModel(ANALYZER_JOB_ID)
            every { startAdvisorJob(any()) } returns advisorJob
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { storeAdvisorRun(any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
        }

        val context = mockk<WorkerContext> {
            every { this@mockk.ortRun } returns ortRun
            every { this@mockk.configManager } returns mockConfigManager()
            coEvery { resolvePluginConfigSecrets(any()) } returns emptyMap()
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(createRunner())
        coEvery { runner.run(any(), any(), any()) } coAnswers {
           OrtTestData.result
        }

        val worker = AdvisorWorker(mockk(), runner, ortRunService, contextFactory, mockk(relaxed = true))

        mockkTransaction {
            val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            coVerify(exactly = 1) {
                runner.run(context, ortResult, advisorJob.configuration)
                ortRunService.storeAdvisorRun(withArg { it.advisorJobId shouldBe ADVISOR_JOB_ID })
            }
        }
    }

    "A 'finished with issues' result should be returned if the advisor run finished with unresolved issues" {
        val ortResult = OrtResult.EMPTY.copy(
            analyzer = OrtAnalyzerRun.EMPTY,
            resolvedConfiguration = OrtTestData.resolvedConfiguration.copy(
                resolutions = OrtTestData.result.resolvedConfiguration.resolutions?.copy(
                    issues = emptyList() // Remove any issue resolutions to simulate unresolved issues.
                )
            )
        )

        val ortRun = mockOrtRun()

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns ortResult

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns advisorJob
            every { getAnalyzerRunForOrtRun(any()) } returns OrtTestData.analyzerRun.mapToModel(ANALYZER_JOB_ID)
            every { startAdvisorJob(any()) } returns advisorJob
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { storeAdvisorRun(any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
        }

        val context = mockk<WorkerContext> {
            every { this@mockk.ortRun } returns ortRun
            every { this@mockk.configManager } returns mockConfigManager()
            coEvery { resolvePluginConfigSecrets(any()) } returns emptyMap()
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(createRunner())
        coEvery { runner.run(any(), any(), any()) } coAnswers {
           OrtTestData.result
        }

        val worker = AdvisorWorker(mockk(), runner, ortRunService, contextFactory, mockk(relaxed = true))

        mockkTransaction {
            val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.FinishedWithIssues

            coVerify(exactly = 1) {
                runner.run(context, ortResult, advisorJob.configuration)
                ortRunService.storeAdvisorRun(withArg { it.advisorJobId shouldBe ADVISOR_JOB_ID })
            }
        }
    }

    "Resolved items should be stored for advisor issues and vulnerabilities" {
        val advisorIssue = Issue(
            timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            source = "VulnerableCode",
            message = "Advisor issue",
            severity = Severity.ERROR
        )
        val advisorVulnerability = Vulnerability(
            id = "CVE-2023-0001",
            summary = "Test vulnerability.",
            description = "Test vulnerability description.",
            references = listOf(
                VulnerabilityReference(
                    url = URI.create("http://cve.example.org"),
                    scoringSystem = "CVSS3",
                    severity = "MEDIUM",
                    score = 5.5f,
                    vector = "CVSS:3.0/AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:N/A:H"
                )
            )
        )

        val ortResult = OrtResult.EMPTY.copy(
            analyzer = OrtAnalyzerRun.EMPTY,
            repository = OrtResult.EMPTY.repository.copy(
                config = OrtResult.EMPTY.repository.config.copy(
                    resolutions = Resolutions(
                        issues = listOf(
                            IssueResolution(
                                message = advisorIssue.message,
                                reason = IssueResolutionReason.CANT_FIX_ISSUE,
                                comment = "Advisor issue resolution."
                            )
                        ),
                        vulnerabilities = listOf(
                            VulnerabilityResolution(
                                id = advisorVulnerability.id,
                                reason = VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
                                comment = "Advisor vulnerability resolution."
                            )
                        )
                    )
                )
            )
        )

        val advisorRunResult = OrtResult.EMPTY.copy(
            advisor = AdvisorRun(
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                environment = OrtTestData.environment,
                config = OrtTestData.advisorConfiguration,
                results = sortedMapOf(
                    Identifier("Maven:com.example:package:1.0") to listOf(
                        AdvisorResult(
                            advisor = AdvisorDetails(
                                name = "VulnerableCode",
                                capabilities = enumSetOf(AdvisorCapability.VULNERABILITIES)
                            ),
                            summary = AdvisorSummary(
                                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                                issues = listOf(advisorIssue)
                            ),
                            defects = emptyList(),
                            vulnerabilities = listOf(advisorVulnerability)
                        )
                    )
                )
            )
        )

        val ortRun = mockOrtRun()

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns ortResult

        val resolvedItemsSlot = slot<ResolvedItemsResult>()

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns advisorJob
            every { getAnalyzerRunForOrtRun(any()) } returns OrtTestData.analyzerRun.mapToModel(ANALYZER_JOB_ID)
            every { startAdvisorJob(any()) } returns advisorJob
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { storeAdvisorRun(any()) } just runs
            every { storeResolvedItems(any(), capture(resolvedItemsSlot)) } just runs
        }

        val context = mockk<WorkerContext> {
            every { this@mockk.ortRun } returns ortRun
            every { this@mockk.configManager } returns mockConfigManager()
            coEvery { resolvePluginConfigSecrets(any()) } returns emptyMap()
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(createRunner())
        coEvery { runner.run(any(), any(), any()) } coAnswers { advisorRunResult }

        val worker = AdvisorWorker(mockk(), runner, ortRunService, contextFactory, mockk(relaxed = true))

        mockkTransaction {
            val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            val resolvedIssue = resolvedItemsSlot.captured.issues.keys.single()
            resolvedIssue.message shouldBe advisorIssue.message
            resolvedIssue.source shouldBe advisorIssue.source
            resolvedIssue.severity.name shouldBe advisorIssue.severity.name

            val resolvedVuln = resolvedItemsSlot.captured.vulnerabilities.keys.single()
            resolvedVuln.externalId shouldBe advisorVulnerability.id
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = advisorJob.copy(status = JobStatus.FINISHED)
        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns invalidJob
        }

        val worker = AdvisorWorker(
            mockk(),
            createRunner(),
            ortRunService,
            mockContextFactory(),
            mockk(relaxed = true)
        )

        mockkTransaction {
            val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})

/**
 * Create a mock [WorkerContextFactory] and prepare it to return the given [context].
 */
private fun mockContextFactory(context: WorkerContext = mockk()): WorkerContextFactory {
    val slot = slot<suspend (WorkerContext) -> RunResult>()
    return mockk {
        coEvery { withContext(ORT_RUN_ID, capture(slot)) } coAnswers {
            slot.captured(context)
        }
    }
}

private fun mockOrtRun() = mockk<OrtRun> {
    every { id } returns ORT_RUN_ID
    every { labels } returns emptyMap()
    every { organizationId } returns ORGANIZATION_ID
    every { repositoryId } returns REPOSITORY_ID
    every { resolvedJobConfigContext } returns null
    every { resolvedJobConfigs } returns null
    every { revision } returns "main"
}

private fun mockConfigManager() = mockk<ConfigManager> {
    every { getFile(any(), any()) } returns
            File("src/test/resources/resolutions.yml").inputStream()
}
