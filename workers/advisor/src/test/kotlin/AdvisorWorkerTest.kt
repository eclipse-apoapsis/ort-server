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
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.shared.orttestdata.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.ossreviewtoolkit.model.AnalyzerRun as OrtAnalyzerRun
import org.ossreviewtoolkit.model.OrtResult

private const val ORT_SERVER_MAPPINGS_FILE = "org.eclipse.apoapsis.ortserver.workers.common.OrtServerMappingsKt"

private const val ANALYZER_JOB_ID = 1L
private const val ADVISOR_JOB_ID = 1L
private const val REPOSITORY_ID = 1L
private const val ORT_RUN_ID = 12L
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
    afterSpec {
        unmockkAll()
    }

    "A project should be advised successfully" {
        val analyzerRun = mockk<AnalyzerRun> {
            every { analyzerJobId } returns ANALYZER_JOB_ID
            every { packages } returns emptySet()
        }
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
            every { resolvedJobConfigContext } returns null
        }
        val ortResult = OrtResult.EMPTY.copy(
            analyzer = OrtAnalyzerRun.EMPTY
        )

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { analyzerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns ortResult

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns advisorJob
            every { getAnalyzerRunForOrtRun(any()) } returns analyzerRun
            every { startAdvisorJob(any()) } returns advisorJob
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { storeAdvisorRun(any()) } just runs
        }

        val context = mockk<WorkerContext> {
            every { this@mockk.ortRun } returns ortRun
            coEvery { resolvePluginConfigSecrets(any()) } returns emptyMap()
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(createRunner())
        val worker = AdvisorWorker(mockk(), runner, ortRunService, contextFactory)

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

        val worker = AdvisorWorker(mockk(), createRunner(), ortRunService, mockContextFactory())

        mockkTransaction {
            when (val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "A 'finished with issues' result should be returned if the advisor run finished with issues" {
        val analyzerRun = mockk<AnalyzerRun> {
            every { analyzerJobId } returns ANALYZER_JOB_ID
            every { packages } returns emptySet()
        }
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
            every { resolvedJobConfigContext } returns null
        }
        val ortResult = OrtResult.EMPTY.copy(
            analyzer = OrtAnalyzerRun.EMPTY
        )

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { analyzerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns ortResult

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorJob(any()) } returns advisorJob
            every { getAnalyzerRunForOrtRun(any()) } returns analyzerRun
            every { startAdvisorJob(any()) } returns advisorJob
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { storeAdvisorRun(any()) } just runs
        }

        val context = mockk<WorkerContext> {
            every { this@mockk.ortRun } returns ortRun
            coEvery { resolvePluginConfigSecrets(any()) } returns emptyMap()
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(createRunner())
        coEvery { runner.run(any(), any(), any()) } coAnswers {
           OrtTestData.result
        }

        val worker = AdvisorWorker(mockk(), runner, ortRunService, contextFactory)

        mockkTransaction {
            val result = worker.run(ADVISOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.FinishedWithIssues

            coVerify(exactly = 1) {
                runner.run(context, ortResult, advisorJob.configuration)
                ortRunService.storeAdvisorRun(withArg { it.advisorJobId shouldBe ADVISOR_JOB_ID })
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
private fun mockContextFactory(context: WorkerContext = mockk()): WorkerContextFactory {
    val slot = slot<suspend (WorkerContext) -> RunResult>()
    return mockk {
        coEvery { withContext(ORT_RUN_ID, capture(slot)) } coAnswers {
            slot.captured(context)
        }
    }
}
