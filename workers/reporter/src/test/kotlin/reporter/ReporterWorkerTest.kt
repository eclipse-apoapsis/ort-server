/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.reporter

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.repositories.ReporterRunRepository
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.EvaluatorRun
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.reporter.ReporterRun
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.server.workers.common.OptionsTransformerFactory
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.mapToOrt

private const val ORT_SERVER_MAPPINGS_FILE = "org.ossreviewtoolkit.server.workers.common.OrtServerMappingsKt"

private const val REPORTER_JOB_ID = 1L
private const val REPOSITORY_ID = 1L
private const val ORT_RUN_ID = 12L
private const val TRACE_ID = "42"

private val reporterJob = ReporterJob(
    id = REPORTER_JOB_ID,
    ortRunId = ORT_RUN_ID,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = ReporterJobConfiguration(listOf("WebApp")),
    status = JobStatus.CREATED
)

class ReporterWorkerTest : StringSpec({
    val runner = ReporterRunner(mockk(relaxed = true), mockk(relaxed = true), OptionsTransformerFactory())

    val ortRunService = mockk<OrtRunService> {
        every { getOrtRepositoryInformation(any()) } returns mockk()
        every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
    }

    "Reports for a project should be created successfully" {
        val analyzerRun = mockk<AnalyzerRun>()
        val advisorRun = mockk<AdvisorRun>()
        val evaluatorRun = mockk<EvaluatorRun>()
        val scannerRun = mockk<ScannerRun>()
        val repository = mockk<Repository>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
        }

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { analyzerRun.mapToOrt() } returns mockk()
        every { advisorRun.mapToOrt() } returns mockk()
        every { evaluatorRun.mapToOrt() } returns mockk()
        every { scannerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns OrtResult.EMPTY

        val dao = mockk<ReporterWorkerDao> {
            every { getAnalyzerRunForReporterJob(any()) } returns analyzerRun
            every { getAdvisorRunForReporterJob(any()) } returns advisorRun
            every { getEvaluatorRunForReporterJob(any()) } returns evaluatorRun
            every { getScannerRunForReporterJob(any()) } returns scannerRun
            every { getReporterJob(any()) } returns reporterJob
            every { getOrtRun(any()) } returns ortRun
            every { getRepository(any()) } returns repository
        }

        val reporterRunRepository = mockk<ReporterRunRepository> {
            every { create(any(), any(), any(), any()) } returns mockk<ReporterRun>()
        }

        val worker = ReporterWorker(mockk(), runner, dao, reporterRunRepository, ortRunService)

        mockkTransaction {
            val result = worker.run(REPORTER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success
        }

        coVerify {
            reporterRunRepository.create(any(), any(), any(), any())
            ortRunService.getOrtRepositoryInformation(ortRun)
        }
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val dao = mockk<ReporterWorkerDao> {
            every { getReporterJob(any()) } throws testException
        }

        val worker = ReporterWorker(mockk(), runner, dao, mockk(), ortRunService)

        mockkTransaction {
            when (val result = worker.run(REPORTER_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "An ignored result should be returned for an invalid job" {
        val invalidJob = reporterJob.copy(status = JobStatus.FINISHED)
        val dao = mockk<ReporterWorkerDao> {
            every { getReporterJob(any()) } returns invalidJob
        }

        val worker = ReporterWorker(mockk(), runner, dao, mockk(), ortRunService)

        mockkTransaction {
            val result = worker.run(REPORTER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})
