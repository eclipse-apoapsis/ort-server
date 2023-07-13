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

package org.ossreviewtoolkit.server.workers.scanner

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.ScannerJob
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory
import org.ossreviewtoolkit.server.workers.common.env.EnvironmentService
import org.ossreviewtoolkit.server.workers.common.mapToOrt

private const val ORT_SERVER_MAPPINGS_FILE = "org.ossreviewtoolkit.server.workers.common.OrtServerMappingsKt"

private const val SCANNER_JOB_ID = 1L
private const val REPOSITORY_ID = 1L
private const val ORT_RUN_ID = 12L
private const val TRACE_ID = "42"

private val scannerJob = ScannerJob(
    id = SCANNER_JOB_ID,
    ortRunId = ORT_RUN_ID,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = ScannerJobConfiguration(),
    status = JobStatus.CREATED
)

class ScannerWorkerTest : StringSpec({
    val ortRunService = mockk<OrtRunService> {
        every { getOrtRepositoryInformation(any()) } returns mockk()
    }

    "A project should be scanned successfully" {
        val analyzerRun = mockk<AnalyzerRun>()
        val repository = mockk<Repository>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
        }

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { repository.mapToOrt(any(), any()) } returns mockk()
        every { analyzerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any()) } returns OrtResult.EMPTY

        val dao = mockk<ScannerWorkerDao> {
            every { getAnalyzerRunForScannerJob(any()) } returns analyzerRun
            every { getScannerJob(any()) } returns scannerJob
            every { getOrtRun(any()) } returns ortRun
            every { getRepository(any()) } returns repository
            every { storeScannerRun(any()) } returns mockk()
        }

        val runner = mockk<ScannerRunner> {
            every { run(any(), any()) } returns mockk {
                every { scanner } returns ScannerRun.EMPTY
            }
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(ORT_RUN_ID) } returns context
        }

        val environmentService = mockk<EnvironmentService> {
            coEvery { generateNetRcFileForCurrentRun(context) } just runs
        }

        val worker = ScannerWorker(mockk(), runner, dao, contextFactory, environmentService, ortRunService)

        mockkTransaction {
            val result = worker.run(SCANNER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            verify(exactly = 1) { dao.storeScannerRun(any()) }

            coVerify { environmentService.generateNetRcFileForCurrentRun(context) }
        }
    }

    "A failure result should be returned in case of an error" {
        val textException = IllegalStateException("Test exception")
        val dao = mockk<ScannerWorkerDao> {
            every { getScannerJob(any()) } throws textException
        }

        val worker = ScannerWorker(mockk(), mockk(), dao, mockk(), mockk(), ortRunService)

        mockkTransaction {
            when (val result = worker.run(SCANNER_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe textException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = scannerJob.copy(status = JobStatus.FINISHED)
        val dao = mockk<ScannerWorkerDao> {
            every { getScannerJob(any()) } returns invalidJob
        }

        val worker = ScannerWorker(mockk(), mockk(), dao, mockk(), mockk(), ortRunService)

        mockkTransaction {
            val result = worker.run(SCANNER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})
