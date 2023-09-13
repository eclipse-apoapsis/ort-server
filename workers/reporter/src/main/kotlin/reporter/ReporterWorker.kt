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

package org.ossreviewtoolkit.server.workers.reporter

import kotlinx.datetime.Clock

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.runs.reporter.Report
import org.ossreviewtoolkit.server.model.runs.reporter.ReporterRun
import org.ossreviewtoolkit.server.workers.common.JobIgnoredException
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.mapToOrt

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReporterWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class ReporterWorker(
    private val db: Database,
    private val runner: ReporterRunner,
    private val ortRunService: OrtRunService
) {
    fun run(jobId: Long, traceId: String): RunResult = runCatching {
        val reporterJob = getValidReporterJob(jobId)
        val ortRun = ortRunService.getOrtRun(reporterJob.ortRunId)
        requireNotNull(ortRun) {
            "ORT run '${reporterJob.ortRunId}' not found."
        }

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

        val runResults =
            runner.run(reporterJob.ortRunId, ortResult, reporterJob.configuration, evaluatorJob?.configuration)

        val endTime = Clock.System.now()

        val reports = runResults.values
            .flatMap { it.toList() }
            .map { file -> Report(file.name) }
            .toList()

        val reporterRun = ReporterRun(
            id = -1L,
            reporterJobId = jobId,
            startTime = startTime,
            endTime = endTime,
            reports = reports
        )

        ortRunService.storeReporterRun(reporterRun)

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
}
