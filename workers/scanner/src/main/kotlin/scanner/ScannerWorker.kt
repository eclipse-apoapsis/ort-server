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

package org.eclipse.apoapsis.ortserver.workers.scanner

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.Severity

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ScannerWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

class ScannerWorker(
    private val db: Database,
    private val runner: ScannerRunner,
    private val ortRunService: OrtRunService,
    private val contextFactory: WorkerContextFactory,
    private val environmentService: EnvironmentService
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        val (scannerJob, ortResult) = db.dbQuery {
            var job = getValidScannerJob(jobId)
            val ortRun = ortRunService.getOrtRun(job.ortRunId)
            requireNotNull(ortRun) {
                "ORT run '${job.ortRunId}' not found."
            }

            job = ortRunService.startScannerJob(job.id)
                ?: throw IllegalArgumentException("The scanner job with id '$jobId' could not be started.")
            logger.debug("Scanner job with id '{}' started at {}.", job.id, job.startedAt)

            val repository = ortRunService.getOrtRepositoryInformation(ortRun)
            val resolvedConfiguration = ortRunService.getResolvedConfiguration(ortRun)

            val analyzerRun = ortRunService.getAnalyzerRunForOrtRun(ortRun.id)

            val ortResult = ortRun.mapToOrt(
                repository = repository,
                analyzerRun = analyzerRun?.mapToOrt(),
                resolvedConfiguration = resolvedConfiguration.mapToOrt()
            )

            Pair(job, ortResult)
        }

        val context = contextFactory.createContext(scannerJob.ortRunId)

        environmentService.generateNetRcFileForCurrentRun(context)

        val scannerRunId = ortRunService.createScannerRun(scannerJob.id).id

        val scannerRun = runner.run(context, ortResult, scannerJob.configuration, scannerRunId).scanner
            ?: throw ScannerException("ORT Scanner failed to create a result.")

        db.dbQuery {
            getValidScannerJob(scannerJob.id)
            ortRunService.finalizeScannerRun(scannerRun.mapToModel(scannerJob.id).copy(id = scannerRunId))
        }

        if (scannerRun.scanResults.flatMap { it.summary.issues }.any { it.severity >= Severity.WARNING }) {
            RunResult.FinishedWithIssues
        } else {
            RunResult.Success
        }
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

    private fun getValidScannerJob(jobId: Long) =
        ortRunService.getScannerJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The scanner job '$jobId' does not exist.")

    private fun ScannerJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Scanner job '$id' status is already set to '$status'.")
        }
    }
}

private class ScannerException(message: String) : Exception(message)
