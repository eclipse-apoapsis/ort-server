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
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.EvaluatorRun
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.workers.common.OptionsTransformerFactory
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.ossreviewtoolkit.model.OrtResult

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
            every { createContext(any()) } returns context
        }
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
        }

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { analyzerRun.mapToOrt() } returns mockk()
        every { advisorRun.mapToOrt() } returns mockk()
        every { evaluatorRun.mapToOrt() } returns mockk()
        every { scannerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns OrtResult.EMPTY

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorRunForOrtRun(any()) } returns advisorRun
            every { getAnalyzerRunForOrtRun(any()) } returns analyzerRun
            every { getEvaluatorJobForOrtRun(any()) } returns evaluatorJob
            every { getEvaluatorRunForOrtRun(any()) } returns evaluatorRun
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getOrtRun(any()) } returns ortRun
            every { getReporterJob(any()) } returns reporterJob
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { getScannerRunForOrtRun(any()) } returns scannerRun
            every { startReporterJob(any()) } returns reporterJob
            every { storeReporterRun(any()) } just runs
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(ORT_RUN_ID) } returns context
        }

        val configManager = mockk<ConfigManager> {
            every { getFile(any(), any()) } throws ConfigException("", null)
        }

        val environmentService = mockk<EnvironmentService> {
            coEvery { generateNetRcFileForCurrentRun(context) } just runs
        }

        val worker = ReporterWorker(
            contextFactory,
            mockk(),
            environmentService,
            ReporterRunner(
                mockk(relaxed = true),
                mockContextFactory(),
                OptionsTransformerFactory(),
                configManager,
                mockk()
            ),
            ortRunService
        )

        mockkTransaction {
            val result = worker.run(REPORTER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success
        }

        coVerify {
            ortRunService.storeReporterRun(any())
            ortRunService.getOrtRepositoryInformation(ortRun)
            environmentService.generateNetRcFileForCurrentRun(context)
        }
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
            ReporterRunner(mockk(relaxed = true), mockContextFactory(), OptionsTransformerFactory(), mockk(), mockk()),
            ortRunService
        )

        mockkTransaction {
            when (val result = worker.run(REPORTER_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
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
            ReporterRunner(mockk(relaxed = true), mockContextFactory(), OptionsTransformerFactory(), mockk(), mockk()),
            ortRunService
        )

        mockkTransaction {
            val result = worker.run(REPORTER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})
