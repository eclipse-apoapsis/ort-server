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

import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll

import java.io.File

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.server.config.ConfigException
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory
import org.ossreviewtoolkit.server.workers.common.mapToOrt
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

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
    configuration = EvaluatorJobConfiguration(ruleSet = SCRIPT_FILE),
    status = JobStatus.CREATED
)

class EvaluatorWorkerTest : StringSpec({
    afterSpec {
        unmockkAll()
    }

    "A project should be evaluated successfully" {
        val analyzerRun = mockk<AnalyzerRun>()
        val advisorRun = mockk<AdvisorRun>()
        val scannerRun = mockk<ScannerRun>()
        val hierarchy = mockk<Hierarchy>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
            every { resolvedJobConfigContext } returns null
        }

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { analyzerRun.mapToOrt() } returns mockk()
        every { advisorRun.mapToOrt() } returns mockk()
        every { scannerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns OrtResult.EMPTY

        val ortRunService = mockk<OrtRunService> {
            every { getAdvisorRunForOrtRun(any()) } returns advisorRun
            every { getAnalyzerRunForOrtRun(any()) } returns analyzerRun
            every { getEvaluatorJob(any()) } returns evaluatorJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { getScannerRunForOrtRun(any()) } returns scannerRun
            every { startEvaluatorJob(any()) } returns evaluatorJob
            every { storeEvaluatorRun(any()) } returns mockk()
            every { storeResolvedPackageConfigurations(any(), any()) } just runs
            every { storeResolvedResolutions(any(), any()) } just runs
        }

        val configManager = mockk<ConfigManager> {
            every { getFileAsString(any(), Path(SCRIPT_FILE)) } returns
                    File("src/test/resources/example.rules.kts").readText()
            every { getFile(any(), Path(ORT_COPYRIGHT_GARBAGE_FILENAME)) } throws ConfigException("", null)
            every { getFile(any(), Path(ORT_LICENSE_CLASSIFICATIONS_FILENAME)) } returns
                    File("src/test/resources/license-classifications.yml").inputStream()
            every { getFile(any(), Path(ORT_RESOLUTIONS_FILENAME)) } throws ConfigException("", null)
        }

        val context = mockk<WorkerContext> {
            every { this@mockk.ortRun } returns ortRun
            every { this@mockk.configManager } returns configManager
        }
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(ORT_RUN_ID) } returns context
        }

        val worker = EvaluatorWorker(mockk(), EvaluatorRunner(mockk()), ortRunService, contextFactory)

        mockkTransaction {
            val result = worker.run(EVALUATOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            coVerify(exactly = 1) {
                ortRunService.storeEvaluatorRun(any())
                ortRunService.getOrtRepositoryInformation(ortRun)
            }
        }
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val ortRunService = mockk<OrtRunService> {
            every { getEvaluatorJob(any()) } throws testException
        }

        val worker = EvaluatorWorker(mockk(), EvaluatorRunner(mockk()), ortRunService, mockk())

        mockkTransaction {
            when (val result = worker.run(EVALUATOR_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = evaluatorJob.copy(status = JobStatus.FINISHED)
        val ortRunService = mockk<OrtRunService> {
            every { getEvaluatorJob(any()) } returns invalidJob
        }

        val worker = EvaluatorWorker(mockk(), EvaluatorRunner(mockk()), ortRunService, mockk())

        mockkTransaction {
            val result = worker.run(EVALUATOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})
