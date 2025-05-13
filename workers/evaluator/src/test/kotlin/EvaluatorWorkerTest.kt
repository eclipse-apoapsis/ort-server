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

package org.eclipse.apoapsis.ortserver.workers.evaluator

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

import java.io.File

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.shared.orttestdata.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

private const val ORT_SERVER_MAPPINGS_FILE = "org.eclipse.apoapsis.ortserver.services.ortrun.OrtServerMappingsKt"

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
            every { generateOrtResult(ortRun, failIfRepoInfoMissing = true) } returns OrtResult.EMPTY
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
            coEvery { resolveProviderPluginConfigSecrets(any()) } returns mockk(relaxed = true)
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(EvaluatorRunner(mockk()))
        // The severity of the rule violation has to be lowered to HINT to avoid a FinishedWithIssues result.
        coEvery { runner.run(any(), any(), any()) } coAnswers {
            val evaluatorRunnerResult = callOriginal()
            evaluatorRunnerResult.copy(
                evaluatorRun = evaluatorRunnerResult.evaluatorRun.copy(
                    violations = evaluatorRunnerResult.evaluatorRun.violations.map { violation ->
                        violation.copy(severity = Severity.HINT)
                    }
                )
            )
        }

        val worker = EvaluatorWorker(mockk(), runner, ortRunService, contextFactory)

        mockkTransaction {
            val result = worker.run(EVALUATOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            coVerify(exactly = 1) {
                ortRunService.storeEvaluatorRun(any())
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

    "A 'finished with issues' result should be returned if the advisor run finished with issues" {
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
            every { generateOrtResult(ortRun, failIfRepoInfoMissing = true) } returns OrtResult.EMPTY
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
            coEvery { resolveProviderPluginConfigSecrets(any()) } returns mockk(relaxed = true)
        }
        val contextFactory = mockContextFactory(context)

        val runner = spyk(EvaluatorRunner(mockk()))
        coEvery { runner.run(any(), any(), any()) } returns EvaluatorRunnerResult(
            OrtTestData.evaluatorRun, emptyList(), Resolutions()
        )
        val worker = EvaluatorWorker(mockk(), runner, ortRunService, contextFactory)

        mockkTransaction {
            val result = worker.run(EVALUATOR_JOB_ID, TRACE_ID)

            result shouldBe RunResult.FinishedWithIssues

            coVerify(exactly = 1) {
                ortRunService.storeEvaluatorRun(any())
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
