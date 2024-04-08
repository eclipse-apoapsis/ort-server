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

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.reporter.ReporterRun
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReporterWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class ReporterWorker(
    private val contextFactory: WorkerContextFactory,
    private val db: Database,
    private val environmentService: EnvironmentService,
    private val runner: ReporterRunner,
    private val ortRunService: OrtRunService,
    private val linkGenerator: ReportDownloadLinkGenerator
) {
    fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidReporterJob(jobId)
        val ortRun = ortRunService.getOrtRun(job.ortRunId)
        requireNotNull(ortRun) {
            "ORT run '${job.ortRunId}' not found."
        }

        job = ortRunService.startReporterJob(job.id)
            ?: throw IllegalArgumentException("The reporter job with id '$jobId' could not be started.")
        logger.debug("Reporter job with id '{}' started at {}.", job.id, job.startedAt)

        /**
         * The setup of environment is only needed by a reporter that creates source code bundles.
         * TODO: Find a better solution which would allow to set up environment only for a specific reporter if needed.
         */
        val context = contextFactory.createContext(job.ortRunId)
        runBlocking { environmentService.generateNetRcFileForCurrentRun(context) }

        val repository = ortRunService.getOrtRepositoryInformation(ortRun)
        val resolvedConfiguration = ortRunService.getResolvedConfiguration(ortRun)
        val analyzerRun = ortRunService.getAnalyzerRunForOrtRun(ortRun.id)
        val advisorRun = ortRunService.getAdvisorRunForOrtRun(ortRun.id)
        val evaluatorJob = ortRunService.getEvaluatorJobForOrtRun(ortRun.id)
        val evaluatorRun = ortRunService.getEvaluatorRunForOrtRun(ortRun.id)
        val scannerRun = ortRunService.getScannerRunForOrtRun(ortRun.id)

        val ortResult = ortRun.mapToOrt(
            repository = repository,
            analyzerRun = analyzerRun?.mapToOrt(),
            advisorRun = advisorRun?.mapToOrt(),
            evaluatorRun = evaluatorRun?.mapToOrt(),
            scannerRun = scannerRun?.mapToOrt(),
            resolvedConfiguration = resolvedConfiguration.mapToOrt()
        )

        val startTime = Clock.System.now()

        val reporterRunnerResult = runner.run(
            job.ortRunId,
            ortResult,
            job.configuration,
            evaluatorJob?.configuration
        )

        val endTime = Clock.System.now()

        val reports = reporterRunnerResult.reports.values
            .flatMap { it.toList() }
            .map { toReport(it, job.ortRunId) }
            .toList()

        val reporterRun = ReporterRun(
            id = -1L,
            reporterJobId = jobId,
            startTime = startTime,
            endTime = endTime,
            reports = reports
        )

        db.blockingQuery {
            ortRunService.storeReporterRun(reporterRun)
            reporterRunnerResult.resolvedPackageConfigurations?.let {
                ortRunService.storeResolvedPackageConfigurations(ortRun.id, it)
            }
            reporterRunnerResult.resolvedResolutions?.let {
                ortRunService.storeResolvedResolutions(ortRun.id, it)
            }
            reporterRunnerResult.issues.takeUnless { it.isEmpty() }?.let {
                ortRunService.storeIssues(ortRun.id, it)
            }
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

    private fun getValidReporterJob(jobId: Long) =
        ortRunService.getReporterJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The reporter job '$jobId' does not exist.")

    private fun ReporterJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Reporter job '$id' status is already set to '$status'.")
        }
    }

    /**
     * Create a [Report] for the given [file] and [runId] together with a link to download it.
     */
    private fun toReport(file: String, runId: Long): Report {
        val reportToken = linkGenerator.generateLink(runId)
        return Report(file, reportToken.downloadLink, reportToken.expirationTime)
    }
}
