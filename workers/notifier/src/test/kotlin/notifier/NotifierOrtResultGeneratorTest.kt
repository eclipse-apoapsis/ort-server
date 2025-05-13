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

package org.eclipse.apoapsis.ortserver.workers.notifier

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.MailNotificationConfiguration
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.EvaluatorRun
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt

import org.ossreviewtoolkit.model.OrtResult

class NotifierOrtResultGeneratorTest : StringSpec({
    beforeSpec {
        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
    }

    afterSpec {
        unmockkAll()
    }

    "A correct OrtResult with default properties should be generated" {
        val helper = ResultGeneratorTestHelper()

        val result = helper.runResultGeneratorTest()

        result.labels shouldContainAll originalLabels
    }

    "Labels for the generated reports should be added" {
        val reports = listOf(
            Report(
                filename = "scan-report.html",
                downloadLink = "https://example.com/scan-report.html",
                downloadTokenExpiryDate = Instant.DISTANT_FUTURE
            ),
            Report(
                filename = "another-report.pdf",
                downloadLink = "https://example.com/some/cryptic/path/another-report.pdf",
                downloadTokenExpiryDate = Instant.DISTANT_FUTURE
            )
        )
        val helper = ResultGeneratorTestHelper()

        val result = helper.runResultGeneratorTest(reports = reports)

        reports.forAll {
            result.labels["report_${it.filename}"] shouldBe it.downloadLink
        }
    }

    "A label for email recipients should be added" {
        val recipient1 = "i-am-interested@example.com"
        val recipient2 = "mee2@example.com"
        val emailConfig = NotifierJobConfiguration(
            mail = MailNotificationConfiguration(recipientAddresses = listOf(recipient1, recipient2))
        )

        val helper = ResultGeneratorTestHelper()

        val result = helper.runResultGeneratorTest(job = notifierJob.copy(configuration = emailConfig))

        result.labels["emailRecipients"] shouldBe "$recipient1;$recipient2"
    }

    "Labels for the status of jobs should be added" {
        val helper = ResultGeneratorTestHelper()
        every {
            helper.ortRunService.getAnalyzerJobForOrtRun(ORT_RUN_ID)
        } returns createJob<AnalyzerJob>(JobStatus.FINISHED, 11)
        every {
            helper.ortRunService.getAdvisorJobForOrtRun(ORT_RUN_ID)
        } returns createJob<AdvisorJob>(JobStatus.FAILED, 22)
        every {
            helper.ortRunService.getEvaluatorJobForOrtRun(ORT_RUN_ID)
        } returns createJob<EvaluatorJob>(JobStatus.FINISHED, 33)
        every {
            helper.ortRunService.getScannerJobForOrtRun(ORT_RUN_ID)
        } returns createJob<ScannerJob>(JobStatus.RUNNING, 44)
        every {
            helper.ortRunService.getReporterJobForOrtRun(ORT_RUN_ID)
        } returns createJob<ReporterJob>(JobStatus.FINISHED, 55)

        val result = helper.runResultGeneratorTest()

        val expectedLabels = mapOf(
            "analyzerJobStatus" to "FINISHED",
            "advisorJobStatus" to "FAILED",
            "evaluatorJobStatus" to "FINISHED",
            "scannerJobStatus" to "RUNNING",
            "reporterJobStatus" to "FINISHED"
        )
        expectedLabels.forAll { (key, value) ->
            result.labels[key] shouldBe value
        }
    }
})

private const val ORT_SERVER_MAPPINGS_FILE = "org.eclipse.apoapsis.ortserver.services.ortrun.OrtServerMappingsKt"

private const val ORT_RUN_ID = 20240502084016L
private const val REPOSITORY_ID = 20240502084048L
private const val NOTIFIER_JOB_ID = 20240502084206L
private val originalLabels = mapOf("label1" to "value1", "label2" to "value2")

private val notifierJob = NotifierJob(
    id = NOTIFIER_JOB_ID,
    ortRunId = ORT_RUN_ID,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = NotifierJobConfiguration(),
    status = JobStatus.CREATED
)

/**
 * Return a mock for a [WorkerJob] of the given type that is prepared to report the given [jobId] and [status].
 */
private inline fun <reified J : WorkerJob> createJob(status: JobStatus, jobId: Long): J =
    mockk {
        every { id } returns jobId
        every { this@mockk.status } returns status
    }

/**
 * A test helper class that manages test data and mocks required by test cases.
 */
private class ResultGeneratorTestHelper {
    val repository = mockk<org.ossreviewtoolkit.model.Repository>()
    val analyzerRun = mockk<AnalyzerRun>()
    val advisorRun = mockk<AdvisorRun>()
    val evaluatorRun = mockk<EvaluatorRun>()
    val scannerRun = mockk<ScannerRun>()
    val resolvedConfig = mockk<ResolvedConfiguration>()

    val ortAnalyzerRun = mockk<org.ossreviewtoolkit.model.AnalyzerRun>()
    val ortAdvisorRun = mockk<org.ossreviewtoolkit.model.AdvisorRun>()
    val ortScannerRun = mockk<org.ossreviewtoolkit.model.ScannerRun>()
    val ortEvaluatorRun = mockk<org.ossreviewtoolkit.model.EvaluatorRun>()
    val ortResolvedConfig = mockk<org.ossreviewtoolkit.model.ResolvedConfiguration>()

    val ortRun = mockk<OrtRun> {
        every { id } returns ORT_RUN_ID
        every { repositoryId } returns REPOSITORY_ID
        every { revision } returns "main"
    }

    val ortRunService = mockk<OrtRunService> {
        every { getAdvisorRunForOrtRun(ORT_RUN_ID) } returns advisorRun
        every { getAnalyzerRunForOrtRun(ORT_RUN_ID) } returns analyzerRun
        every { getEvaluatorRunForOrtRun(ORT_RUN_ID) } returns evaluatorRun
        every { getOrtRepositoryInformation(ortRun, failIfMissing = false) } returns repository
        every { getOrtRun(ORT_RUN_ID) } returns ortRun
        every { getNotifierJob(NOTIFIER_JOB_ID) } returns notifierJob
        every { getResolvedConfiguration(ortRun) } returns resolvedConfig
        every { getScannerRunForOrtRun(ORT_RUN_ID) } returns scannerRun
        every { getAnalyzerJobForOrtRun(ORT_RUN_ID) } returns null
        every { getAdvisorJobForOrtRun(ORT_RUN_ID) } returns null
        every { getEvaluatorJobForOrtRun(ORT_RUN_ID) } returns null
        every { getScannerJobForOrtRun(ORT_RUN_ID) } returns null
        every { getReporterJobForOrtRun(ORT_RUN_ID) } returns null
        every { generateOrtResult(ortRun, failIfRepoInfoMissing = false) } returns OrtResult.EMPTY.copy(
            repository = repository,
            analyzer = ortAnalyzerRun,
            advisor = ortAdvisorRun,
            scanner = ortScannerRun,
            evaluator = ortEvaluatorRun,
            labels = originalLabels
        )
    }

    init {
        every { analyzerRun.mapToOrt() } returns ortAnalyzerRun
        every { advisorRun.mapToOrt() } returns ortAdvisorRun
        every { evaluatorRun.mapToOrt() } returns ortEvaluatorRun
        every { scannerRun.mapToOrt() } returns ortScannerRun
        every { resolvedConfig.mapToOrt() } returns ortResolvedConfig
        every {
            ortRun.mapToOrt(
                repository,
                ortAnalyzerRun,
                ortAdvisorRun,
                ortScannerRun,
                ortEvaluatorRun,
                ortResolvedConfig
            )
        } returns OrtResult.EMPTY.copy(
            repository = repository,
            analyzer = ortAnalyzerRun,
            advisor = ortAdvisorRun,
            scanner = ortScannerRun,
            evaluator = ortEvaluatorRun,
            labels = originalLabels
        )
    }

    /**
     * Run a test to generate an [OrtResult] based on the mocks managed by this instance and the given [job] and
     * [reports] list. Return the result.
     */
    fun runResultGeneratorTest(job: NotifierJob = notifierJob, reports: List<Report> = emptyList()): OrtResult {
        every { ortRunService.getDownloadLinksForOrtRun(ORT_RUN_ID) } returns reports

        val generator = NotifierOrtResultGenerator(ortRunService)

        return generator.generateOrtResult(ortRun, job).also { result ->
            result.repository shouldBe repository
            result.analyzer shouldBe ortAnalyzerRun
            result.advisor shouldBe ortAdvisorRun
            result.scanner shouldBe ortScannerRun
            result.evaluator shouldBe ortEvaluatorRun
        }
    }
}
