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

package org.ossreviewtoolkit.server.workers.evaluator

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.mapToOrt

private const val ORT_SERVER_MAPPINGS_FILE = "org.ossreviewtoolkit.server.workers.common.OrtServerMappingsKt"

private const val EVALUATOR_JOB_ID = 1L
private const val REPOSITORY_ID = 1L
private const val ORT_RUN_ID = 12L
private const val TRACE_ID = "42"

private val evaluatorJob = EvaluatorJob(
    id = EVALUATOR_JOB_ID,
    ortRunId = ORT_RUN_ID,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = EvaluatorJobConfiguration(SCRIPT_FILE),
    status = JobStatus.CREATED
)

class EvaluatorWorkerTest : StringSpec({
    beforeSpec {
        mockkStatic(::findResolvedRevision)

        every {
            findResolvedRevision(any(), any())
        } returns "some_revision"
    }

    afterSpec {
        unmockkAll()
    }

    "A project should be evaluated successfully" {
        val analyzerRun = mockk<AnalyzerRun>()
        val advisorRun = mockk<AdvisorRun>()
        val repository = mockk<Repository>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
        }

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { repository.mapToOrt(any(), any()) } returns mockk()
        every { analyzerRun.mapToOrt() } returns mockk()
        every { advisorRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any()) } returns OrtResult.EMPTY

        val dao = mockk<EvaluatorWorkerDao> {
            every { getAnalyzerRunForEvaluatorJob(any()) } returns analyzerRun
            every { getAdvisorRunForEvaluatorJob(any()) } returns advisorRun
            every { getEvaluatorJob(any()) } returns evaluatorJob
            every { storeEvaluatorRun(any()) } returns mockk()
            every { getOrtRun(any()) } returns ortRun
            every { getRepository(any()) } returns repository
        }

        val worker = EvaluatorWorker(mockk(), EvaluatorRunner(), dao)

        mockkTransaction {
            val result = worker.run(EVALUATOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            verify(exactly = 1) { dao.storeEvaluatorRun(any()) }
        }
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val dao = mockk<EvaluatorWorkerDao> {
            every { getEvaluatorJob(any()) } throws testException
        }

        val worker = EvaluatorWorker(mockk(), EvaluatorRunner(), dao)

        mockkTransaction {
            when (val result = worker.run(EVALUATOR_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = evaluatorJob.copy(status = JobStatus.FINISHED)
        val dao = mockk<EvaluatorWorkerDao> {
            every { getEvaluatorJob(any()) } returns invalidJob
        }

        val worker = EvaluatorWorker(mockk(), EvaluatorRunner(), dao)

        mockkTransaction {
            val result = worker.run(EVALUATOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})
