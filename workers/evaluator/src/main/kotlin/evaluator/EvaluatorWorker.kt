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

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EvaluatorWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class EvaluatorWorker(
    private val db: Database,
    private val runner: EvaluatorRunner,
    private val ortRunService: OrtRunService,
    private val workerContextFactory: WorkerContextFactory
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidEvaluatorJob(jobId)
        val workerContext = workerContextFactory.createContext(job.ortRunId)
        val ortRun = workerContext.ortRun

        job = ortRunService.startEvaluatorJob(job.id)
            ?: throw IllegalArgumentException("The evaluator job with id '$jobId' could not be started.")
        logger.debug("Evaluator job with id '{}' started at {}.", job.id, job.startedAt)

        val repository = ortRunService.getOrtRepositoryInformation(ortRun)
        val resolvedConfiguration = ortRunService.getResolvedConfiguration(ortRun)
        val analyzerRun = ortRunService.getAnalyzerRunForOrtRun(ortRun.id)
        val advisorRun = ortRunService.getAdvisorRunForOrtRun(ortRun.id)
        val scannerRun = ortRunService.getScannerRunForOrtRun(ortRun.id)

        val ortResult = ortRun.mapToOrt(
            repository = repository,
            analyzerRun = analyzerRun?.mapToOrt(),
            advisorRun = advisorRun?.mapToOrt(),
            scannerRun = scannerRun?.mapToOrt(),
            resolvedConfiguration = resolvedConfiguration.mapToOrt()
        )

        val evaluatorRunnerResult = runner.run(ortResult, job.configuration, workerContext)

        db.blockingQuery {
            getValidEvaluatorJob(job.id)
            ortRunService.storeEvaluatorRun(evaluatorRunnerResult.evaluatorRun.mapToModel(job.id))
            ortRunService.storeResolvedPackageConfigurations(ortRun.id, evaluatorRunnerResult.packageConfigurations)
            ortRunService.storeResolvedResolutions(ortRun.id, evaluatorRunnerResult.resolutions)
        }

        RunResult.Success
    }.getOrElse {
        when (it) {
            is JobIgnoredException -> {
                logger.warn("Message with traceId '$traceId' ignored: ${it.message}")
                RunResult.Ignored
            }

            else -> {
                logger.error("Error while processing message with traceId '$traceId': ${it.message}")
                RunResult.Failed(it)
            }
        }
    }

    private fun getValidEvaluatorJob(jobId: Long) =
        ortRunService.getEvaluatorJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The evaluator job '$jobId' does not exist.")

    private fun EvaluatorJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Evaluator job '$id' status is already set to '$status'")
        }
    }
}
