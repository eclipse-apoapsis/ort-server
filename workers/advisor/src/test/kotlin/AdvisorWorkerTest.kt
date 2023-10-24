/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.advisor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify

import kotlin.test.fail

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory

private const val ANALYZER_JOB_ID = 1L
private const val ADVISOR_JOB_ID = 1L
private const val TRACE_ID = "42"

private val advisorJob = AdvisorJob(
    id = ADVISOR_JOB_ID,
    ortRunId = 12,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = AdvisorJobConfiguration(),
    status = JobStatus.CREATED
)

/**
 * Return an [AdvisorRunner] to be used by tests.
 */
private fun createRunner(): AdvisorRunner = AdvisorRunner()

class AdvisorWorkerTest : StringSpec({
    "A project should be advised successfully" {
        val analyzerRun = mockk<AnalyzerRun> {
            every { analyzerJobId } returns ANALYZER_JOB_ID
            every { packages } returns emptySet()
        }

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns advisorJob
            every { getAnalyzerRunForOrtRun(any()) } returns analyzerRun
            every { storeAdvisorRun(any()) } just runs
        }

        val context = mockk<WorkerContext> {
            coEvery { resolveConfigSecrets(any()) } returns emptyMap()
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(createRunner())
        val worker = AdvisorWorker(mockk(), runner, ortRunService, contextFactory)

        mockkTransaction {
            val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                runner.run(context, emptySet(), advisorJob.configuration)
                ortRunService.storeAdvisorRun(withArg { it.advisorJobId shouldBe ADVISOR_JOB_ID })
            }
        }
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } throws testException
        }

        val worker = AdvisorWorker(mockk(), createRunner(), ortRunService, mockContextFactory())

        mockkTransaction {
            when (val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = advisorJob.copy(status = JobStatus.FINISHED)
        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns invalidJob
        }

        val worker = AdvisorWorker(mockk(), createRunner(), ortRunService, mockContextFactory())

        mockkTransaction {
            val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})

/**
 * Create a mock [WorkerContextFactory] and prepare it to return the given [context].
 */
private fun mockContextFactory(context: WorkerContext = mockk()): WorkerContextFactory =
    mockk {
        every { createContext(advisorJob.ortRunId) } returns context
    }
