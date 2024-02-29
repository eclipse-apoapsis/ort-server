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

import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(NotifierWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class NotifierWorker(
    private val runner: NotifierRunner,
    private val ortRunService: OrtRunService,
    private val workerContextFactory: WorkerContextFactory
) {
    fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidNotifierJob(jobId)
        val workerContext = workerContextFactory.createContext(job.ortRunId)
        val ortRun = workerContext.ortRun

        job = ortRunService.startNotifierJob(job.id)
            ?: throw IllegalArgumentException("The notifier job '$jobId' does not exist.")
        logger.debug("Notifier job with id '{}' started at '{}'.", jobId, job.startedAt)

        val repository = ortRunService.getOrtRepositoryInformation(ortRun)
        val resolvedConfiguration = ortRunService.getResolvedConfiguration(ortRun)
        val analyzerRun = ortRunService.getAnalyzerRunForOrtRun(ortRun.id)
        val advisorRun = ortRunService.getAdvisorRunForOrtRun(ortRun.id)
        val scannerRun = ortRunService.getScannerRunForOrtRun(ortRun.id)
        val evaluatorRun = ortRunService.getEvaluatorRunForOrtRun(ortRun.id)

        val ortResult = ortRun.mapToOrt(
            repository = repository,
            analyzerRun = analyzerRun?.mapToOrt(),
            advisorRun = advisorRun?.mapToOrt(),
            scannerRun = scannerRun?.mapToOrt(),
            evaluatorRun = evaluatorRun?.mapToOrt(),
            resolvedConfiguration = resolvedConfiguration.mapToOrt()
        )

        runner.run(ortResult, job.configuration, workerContext)

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

    private fun getValidNotifierJob(jobId: Long) =
        ortRunService.getNotifierJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The notifier job '$jobId' does not exist.")

    private fun NotifierJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Notifier job '$id' status is already set to '$status'")
        }
    }
}
