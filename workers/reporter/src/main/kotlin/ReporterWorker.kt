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

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.reporter.ReporterRun
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.loadGlobalResolutions
import org.eclipse.apoapsis.ortserver.workers.common.validateForProcessing

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.utils.ort.ORT_VERSION

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReporterWorker::class.java)

internal class ReporterWorker(
    private val contextFactory: WorkerContextFactory,
    private val db: Database,
    private val environmentService: EnvironmentService,
    private val runner: ReporterRunner,
    private val ortRunService: OrtRunService,
    private val linkGenerator: ReportDownloadLinkGenerator,
    private val adminConfigService: AdminConfigService
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidReporterJob(jobId)
        val ortRun = ortRunService.getOrtRun(job.ortRunId)
        requireNotNull(ortRun) {
            "ORT run '${job.ortRunId}' not found."
        }

        job = ortRunService.startReporterJob(job.id)
            ?: throw IllegalArgumentException("The reporter job with id '$jobId' could not be started.")
        logger.debug("Reporter job with id '{}' started at {}.", job.id, job.startedAt)
        logger.info("Using ORT version {}.", ORT_VERSION)

        if (job.configuration.keepAliveWorker) {
            EndpointComponent.generateKeepAliveFile()
        }

        /**
         * The setup of environment is only needed by a reporter that creates source code bundles.
         * TODO: Find a better solution which would allow to set up environment only for a specific reporter if needed.
         */
        val ortResult = ortRunService.generateOrtResult(ortRun, failIfRepoInfoMissing = false)
        val startTime = Clock.System.now()

        if (ortResult.repository == Repository.EMPTY) {
            logger.warn(
                "No repository information found in ORT result for ORT run '${job.ortRunId}'. Most likely, the " +
                        "analyzer worker did not complete successfully."
            )

            val reporterRun = ReporterRun(
                id = -1L,
                reporterJobId = jobId,
                startTime = startTime,
                endTime = startTime,
                reports = emptyList()
            )
            ortRunService.storeReporterRun(reporterRun)
            return RunResult.FinishedWithIssues
        }

        contextFactory.withContext(job.ortRunId) { context ->
            environmentService.setupAuthenticationForCurrentRun(context)

            val reporterRunnerResult = runner.run(
                ortResult,
                job.configuration,
                ortRunService.getEvaluatorJobForOrtRun(ortRun.id)?.configuration,
                context
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

            db.dbQuery {
                ortRunService.storeReporterRun(reporterRun)
                reporterRunnerResult.resolvedPackageConfigurations?.let {
                    ortRunService.storeResolvedPackageConfigurations(ortRun.id, it)
                }
                reporterRunnerResult.resolvedItems?.let {
                    ortRunService.storeResolvedItems(ortRun.id, it)
                }
                reporterRunnerResult.issues.takeUnless { it.isEmpty() }?.let {
                    ortRunService.storeIssues(ortRun.id, it)
                }
            }

            val allIssues = reporterRunnerResult.issues

            val repositoryConfigIssueResolutions = ortResult.repository.config.resolutions.issues
            val globalIssueResolutions = context.loadGlobalResolutions(adminConfigService).issues

            val unresolvedIssues = allIssues
                .map { issue -> issue.mapToOrt() }
                .filter { issue ->
                repositoryConfigIssueResolutions.none { it.matches(issue) } &&
                        globalIssueResolutions.none { it.matches(issue) }
            }

            logger.info(
                "Reporter job ${job.id} finished with ${allIssues.size} total issues" +
                        " and ${unresolvedIssues.size} unresolved issues."
            )

            if (unresolvedIssues.any { it.severity >= Severity.WARNING }) {
                RunResult.FinishedWithIssues
            } else {
                RunResult.Success
            }
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

    private fun getValidReporterJob(jobId: Long) =
        ortRunService.getReporterJob(jobId).validateForProcessing(jobId)

    /**
     * Create a [Report] for the given [file] and [runId] together with a link to download it.
     */
    private fun toReport(file: String, runId: Long): Report {
        val reportToken = linkGenerator.generateLink(runId)
        return Report(file, reportToken.downloadLink, reportToken.expirationTime)
    }
}
