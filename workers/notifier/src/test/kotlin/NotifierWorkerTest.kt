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
import kotlinx.datetime.toJavaInstant

import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.runs.notifier.NotifierRun
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory

import org.ossreviewtoolkit.model.NotifierRun as OrtNotifierRun
import org.ossreviewtoolkit.model.OrtResult

private const val ORT_SERVER_MAPPINGS_FILE = "org.eclipse.apoapsis.ortserver.services.ortrun.OrtServerMappingsKt"

private const val NOTIFIER_JOB_ID = 1L
private const val REPOSITORY_ID = 1L
private const val ORT_RUN_ID = 12L
private const val TRACE_ID = "42"

private val notifierJob = NotifierJob(
    id = NOTIFIER_JOB_ID,
    ortRunId = ORT_RUN_ID,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = NotifierJobConfiguration(),
    status = JobStatus.CREATED
)

class NotifierWorkerTest : StringSpec({
    beforeSpec {
        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
    }

    afterSpec {
        unmockkAll()
    }

    "NotifierWorker should run successfully" {
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
        }

        val ortResult = mockk<OrtResult> {
            every { labels } returns emptyMap()
            // Ignore the changed value as the validation is done by checking the parameter.
            every { copy(labels = any()) } returns this
        }

        val resultGenerator = mockk<NotifierOrtResultGenerator> {
            every { generateOrtResult(ortRun, notifierJob) } returns ortResult
        }

        val ortRunService = mockk<OrtRunService> {
            every { getOrtRun(ORT_RUN_ID) } returns ortRun
            every { getNotifierJob(NOTIFIER_JOB_ID) } returns notifierJob
            every { startNotifierJob(NOTIFIER_JOB_ID) } returns notifierJob
            every { storeNotifierRun(any()) } returns mockk()
            every { storeIssues(any(), any()) } just runs
        }

        val context = mockk<WorkerContext> {
            every { this@mockk.ortRun } returns ortRun
        }

        val slot = slot<suspend (WorkerContext) -> RunResult>()
        val contextFactory = mockk<WorkerContextFactory> {
            coEvery { withContext(ORT_RUN_ID, capture(slot)) } coAnswers {
                slot.captured(context)
            }
        }

        val runnerResult = NotifierRunnerResult(
            OrtNotifierRun(
                startTime = Clock.System.now().toJavaInstant(),
                endTime = Clock.System.now().toJavaInstant()
            )
        )

        val runner = mockk<NotifierRunner> {
            every {
                run(ortResult, notifierJob.configuration, context)
            } returns runnerResult
        }

        val worker = NotifierWorker(
            mockk(),
            runner,
            ortRunService,
            contextFactory,
            resultGenerator
        )

        mockkTransaction {
            val result = worker.run(NOTIFIER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success
        }

        val slotNotifierRun = slot<NotifierRun>()
        coVerify {
            ortRunService.storeNotifierRun(capture(slotNotifierRun))
        }

        slotNotifierRun.captured.notifierJobId shouldBe NOTIFIER_JOB_ID
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val ortRunService = mockk<OrtRunService> {
            every { getNotifierJob(NOTIFIER_JOB_ID) } throws testException
        }

        val worker = NotifierWorker(mockk(), NotifierRunner(), ortRunService, mockk(), mockk())

        mockkTransaction {
            when (val result = worker.run(NOTIFIER_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> error("Unexpected result: $result")
            }
        }
    }

    "An ignored result should be returned for an invalid job" {
        val invalidJob = notifierJob.copy(status = JobStatus.FINISHED)
        val ortRunService = mockk<OrtRunService> {
            every { getNotifierJob(NOTIFIER_JOB_ID) } returns invalidJob
        }

        val worker = NotifierWorker(mockk(), NotifierRunner(), ortRunService, mockk(), mockk())

        mockkTransaction {
            val result = worker.run(NOTIFIER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})
