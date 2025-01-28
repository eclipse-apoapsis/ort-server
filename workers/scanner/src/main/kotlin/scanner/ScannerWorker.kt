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
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService.Companion.validateForProcessing
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.Provenance

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ScannerWorker::class.java)

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

        val scannerRunResult = runner.run(context, ortResult, scannerJob.configuration, scannerRunId)

        val issues = scannerRunResult.extractIssues()
        db.dbQuery {
            getValidScannerJob(scannerJob.id)
            ortRunService.finalizeScannerRun(
                scannerRunResult.scannerRun.mapToModel(scannerJob.id).copy(id = scannerRunId),
                issues
            )
        }

        if (issues.any { it.severity >= Severity.WARNING }) {
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
        ortRunService.getScannerJob(jobId).validateForProcessing(jobId)
}

/**
 * Extract all [Issue]s from this [OrtScannerResult] and convert it to the ORT Server model.
 */
private fun OrtScannerResult.extractIssues(): List<Issue> {
    val idsByProvenance = mutableMapOf<Provenance, Identifier>()
    scannerRun.getAllScanResults().forEach { (id, results) ->
        results.forEach { result ->
            idsByProvenance[result.provenance] = id.mapToModel()
        }
    }

    return issues.flatMap { (provenance, issues) ->
        issues.map { issue ->
            issue.mapToModel(identifier = idsByProvenance[provenance], worker = "scanner")
        }
    }
}
