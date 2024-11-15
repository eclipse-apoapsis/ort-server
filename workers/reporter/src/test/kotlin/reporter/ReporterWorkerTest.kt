/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.reporter

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.EvaluatorRun
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.reporter.ReporterRun
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository

private const val ORT_SERVER_MAPPINGS_FILE = "org.eclipse.apoapsis.ortserver.workers.common.OrtServerMappingsKt"

private const val REPORTER_JOB_ID = 1L
private const val REPOSITORY_ID = 1L
private const val ORT_RUN_ID = 12L
private const val TRACE_ID = "42"

private val evaluatorJob = EvaluatorJob(
    id = 1L,
    ortRunId = ORT_RUN_ID,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = EvaluatorJobConfiguration(),
    status = JobStatus.CREATED
)

private val reporterJob = ReporterJob(
    id = REPORTER_JOB_ID,
    ortRunId = ORT_RUN_ID,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = ReporterJobConfiguration(formats = listOf("WebApp")),
    status = JobStatus.CREATED
)

class ReporterWorkerTest : StringSpec({
    fun mockContextFactory(): WorkerContextFactory {
        val context = mockk<WorkerContext>(relaxed = true) {
            every { createTempDir() } answers { tempdir() }
        }

        return mockk {
            every { createContext(ORT_RUN_ID) } returns context
        }
    }

    beforeSpec {
        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
    }

    afterSpec {
        unmockkAll()
    }

    "Reports for a project should be created successfully" {
        val analyzerRun = mockk<AnalyzerRun>()
        val advisorRun = mockk<AdvisorRun>()
        val evaluatorRun = mockk<EvaluatorRun>()
        val scannerRun = mockk<ScannerRun>()
        val hierarchy = mockk<Hierarchy>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
            every { labels } returns mapOf("projectName" to "Test project")
        }

        val ortResult = mockk<OrtResult> {
            every { copy(any(), any(), any(), any(), any(), any(), any()) } returns this
            every { labels } returns mapOf("projectName" to "Test project")
            every { repository } returns mockk()
        }

        every { analyzerRun.mapToOrt() } returns mockk()
        every { advisorRun.mapToOrt() } returns mockk()
        every { evaluatorRun.mapToOrt() } returns mockk()
        every { scannerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns ortResult

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorRunForOrtRun(ORT_RUN_ID) } returns advisorRun
            every { getAnalyzerRunForOrtRun(ORT_RUN_ID) } returns analyzerRun
            every { getEvaluatorJobForOrtRun(ORT_RUN_ID) } returns evaluatorJob
            every { getEvaluatorRunForOrtRun(ORT_RUN_ID) } returns evaluatorRun
            every { getHierarchyForOrtRun(ORT_RUN_ID) } returns hierarchy
            every { getOrtRepositoryInformation(ortRun) } returns mockk()
            every { getOrtRun(ORT_RUN_ID) } returns ortRun
            every { getReporterJob(REPORTER_JOB_ID) } returns reporterJob
            every { getResolvedConfiguration(ortRun) } returns ResolvedConfiguration()
            every { getScannerRunForOrtRun(ORT_RUN_ID) } returns scannerRun
            every { startReporterJob(REPORTER_JOB_ID) } returns reporterJob
            every { storeReporterRun(any()) } just runs
            every { storeIssues(any(), any()) } just runs
            every { generateOrtResult(ortRun, failIfRepoInfoMissing = false) } returns ortResult
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(ORT_RUN_ID) } returns context
        }

        val environmentService = mockk<EnvironmentService> {
            coEvery { generateNetRcFileForCurrentRun(context) } just runs
        }

        val runnerResult = ReporterRunnerResult(
            reports = mapOf("WebApp" to listOf("report.html")),
            resolvedPackageConfigurations = null,
            resolvedResolutions = null,
            issues = listOf(Issue(Clock.System.now(), "Test issue", "Test message", Severity.HINT))
        )
        val runner = mockk<ReporterRunner> {
            coEvery {
                run(ORT_RUN_ID, ortResult, reporterJob.configuration, evaluatorJob.configuration)
            } returns runnerResult
        }

        val link = ReportDownloadLink("https://report.example.org/ap1/$ORT_RUN_ID/someToken", Instant.DISTANT_FUTURE)
        val linkGenerator = mockk<ReportDownloadLinkGenerator> {
            every { generateLink(ORT_RUN_ID) } returns link
        }

        val worker = ReporterWorker(
            contextFactory,
            mockk(),
            environmentService,
            runner,
            ortRunService,
            linkGenerator
        )

        mockkTransaction {
            val result = worker.run(REPORTER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success
        }

        val slotReporterRun = slot<ReporterRun>()
        coVerify {
            ortRunService.storeReporterRun(capture(slotReporterRun))
            ortRunService.storeIssues(ORT_RUN_ID, runnerResult.issues)
            environmentService.generateNetRcFileForCurrentRun(context)
        }

        slotReporterRun.captured.reports shouldContainExactlyInAnyOrder listOf(
            Report("report.html", link.downloadLink, link.expirationTime)
        )
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val ortRunService = mockk<OrtRunService> {
            every { getReporterJob(any()) } throws testException
        }

        val worker = ReporterWorker(
            mockContextFactory(),
            mockk(),
            mockk(),
            ReporterRunner(mockk(relaxed = true), mockContextFactory(), mockk(), mockk()),
            ortRunService,
            mockk()
        )

        mockkTransaction {
            when (val result = worker.run(REPORTER_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "A 'finished with issues' result should be returned if the reporter run finished with issues" {
        val analyzerRun = mockk<AnalyzerRun>()
        val advisorRun = mockk<AdvisorRun>()
        val evaluatorRun = mockk<EvaluatorRun>()
        val scannerRun = mockk<ScannerRun>()
        val hierarchy = mockk<Hierarchy>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
            every { labels } returns mapOf("projectName" to "Test project")
        }

        val ortResult = mockk<OrtResult> {
            every { copy(any(), any(), any(), any(), any(), any(), any()) } returns this
            every { labels } returns mapOf("projectName" to "Test project")
            every { repository } returns mockk()
        }

        every { analyzerRun.mapToOrt() } returns mockk()
        every { advisorRun.mapToOrt() } returns mockk()
        every { evaluatorRun.mapToOrt() } returns mockk()
        every { scannerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns ortResult

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorRunForOrtRun(ORT_RUN_ID) } returns advisorRun
            every { getAnalyzerRunForOrtRun(ORT_RUN_ID) } returns analyzerRun
            every { getEvaluatorJobForOrtRun(ORT_RUN_ID) } returns evaluatorJob
            every { getEvaluatorRunForOrtRun(ORT_RUN_ID) } returns evaluatorRun
            every { getHierarchyForOrtRun(ORT_RUN_ID) } returns hierarchy
            every { getOrtRepositoryInformation(ortRun) } returns mockk()
            every { getOrtRun(ORT_RUN_ID) } returns ortRun
            every { getReporterJob(REPORTER_JOB_ID) } returns reporterJob
            every { getResolvedConfiguration(ortRun) } returns ResolvedConfiguration()
            every { getScannerRunForOrtRun(ORT_RUN_ID) } returns scannerRun
            every { startReporterJob(REPORTER_JOB_ID) } returns reporterJob
            every { storeReporterRun(any()) } just runs
            every { storeIssues(any(), any()) } just runs
            every { generateOrtResult(ortRun, failIfRepoInfoMissing = false) } returns ortResult
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(ORT_RUN_ID) } returns context
        }

        val environmentService = mockk<EnvironmentService> {
            coEvery { generateNetRcFileForCurrentRun(context) } just runs
        }

        val runnerResult = ReporterRunnerResult(
            reports = mapOf("WebApp" to listOf("report.html")),
            resolvedPackageConfigurations = null,
            resolvedResolutions = null,
            issues = listOf(Issue(Clock.System.now(), "Test issue", "Test message", Severity.ERROR))
        )
        val runner = mockk<ReporterRunner> {
            coEvery {
                run(ORT_RUN_ID, ortResult, reporterJob.configuration, evaluatorJob.configuration)
            } returns runnerResult
        }

        val link = ReportDownloadLink("https://report.example.org/ap1/$ORT_RUN_ID/someToken", Instant.DISTANT_FUTURE)
        val linkGenerator = mockk<ReportDownloadLinkGenerator> {
            every { generateLink(ORT_RUN_ID) } returns link
        }

        val worker = ReporterWorker(
            contextFactory,
            mockk(),
            environmentService,
            runner,
            ortRunService,
            linkGenerator
        )

        mockkTransaction {
            val result = worker.run(REPORTER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.FinishedWithIssues
        }

        val slotReporterRun = slot<ReporterRun>()
        coVerify {
            ortRunService.storeReporterRun(capture(slotReporterRun))
            ortRunService.storeIssues(ORT_RUN_ID, runnerResult.issues)
            environmentService.generateNetRcFileForCurrentRun(context)
        }

        slotReporterRun.captured.reports shouldContainExactlyInAnyOrder listOf(
            Report("report.html", link.downloadLink, link.expirationTime)
        )
    }

    "An ignored result should be returned for an invalid job" {
        val invalidJob = reporterJob.copy(status = JobStatus.FINISHED)
        val ortRunService = mockk<OrtRunService> {
            every { getReporterJob(any()) } returns invalidJob
        }

        val worker = ReporterWorker(
            mockContextFactory(),
            mockk(),
            mockk(),
            ReporterRunner(mockk(relaxed = true), mockContextFactory(), mockk(), mockk()),
            ortRunService,
            mockk()
        )

        mockkTransaction {
            val result = worker.run(REPORTER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }

    "Reporter should run without error even if there is no analyzer result" {
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
            every { labels } returns mapOf("projectName" to "Test project")
        }

        val ortResult = mockk<OrtResult> {
            every { repository } returns Repository.EMPTY
        }

        val ortRunService = mockk<OrtRunService> {
            every { getOrtRun(ORT_RUN_ID) } returns ortRun
            every { getReporterJob(REPORTER_JOB_ID) } returns reporterJob
            every { startReporterJob(REPORTER_JOB_ID) } returns reporterJob
            every { generateOrtResult(ortRun, failIfRepoInfoMissing = false) } returns ortResult
            every { storeReporterRun(any()) } just runs
        }

        val worker = ReporterWorker(
            mockk(),
            mockk(),
            mockk(),
            mockk<ReporterRunner>(),
            ortRunService,
            mockk()
        )

        mockkTransaction {
            val result = worker.run(REPORTER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.FinishedWithIssues
        }

        coVerify {
            ortRunService.storeReporterRun(any())
        }
    }
})
