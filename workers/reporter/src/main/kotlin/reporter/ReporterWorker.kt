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

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.workers.common.JobIgnoredException
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.mapToOrt

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReporterWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class ReporterWorker(private val runner: ReporterRunner, private val dao: ReporterWorkerDao) {
    fun run(jobId: Long, traceId: String): RunResult = runCatching {
        val (reporterJob, ortResult) = blockingQuery {
            val reporterJob = getValidReporterJob(jobId)
            val ortRun = dao.getOrtRun(reporterJob.ortRunId)
            requireNotNull(ortRun) {
                "ORT run '${reporterJob.ortRunId}' not found."
            }

            val repository = dao.getRepository(ortRun.repositoryId)
            requireNotNull(repository) {
                "Repository '${ortRun.repositoryId}' not found."
            }

            val analyzerRun = dao.getAnalyzerRunForReporterJob(reporterJob)
            val advisorRun = dao.getAdvisorRunForReporterJob(reporterJob)
            val evaluatorRun = dao.getEvaluatorRunForReporterJob(reporterJob)

            // TODO: As soon as ScannerRun is implemented, it should be considered also in the mapping of an OrtResult.
            val ortResult = ortRun.mapToOrt(
                repository = repository.mapToOrt(findResolvedRevision(ortRun, analyzerRun)),
                analyzerRun = analyzerRun?.mapToOrt(),
                advisorRun = advisorRun?.mapToOrt(),
                evaluatorRun = evaluatorRun?.mapToOrt()
            )

            Pair(reporterJob, ortResult)
        }

        runner.run(ortResult, reporterJob.configuration)

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
        dao.getReporterJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The reporter job '$jobId' does not exist.")

    private fun ReporterJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Reporter job '$id' status is already set to '$status'.")
        }
    }
}

/**
 * Obtain the correct resolved revision for the current [ortRun] and [analyzerRun].
 * TODO: This is a work-around. The resolved revision should be stored already when creating the Analyzer result.
 */
internal fun findResolvedRevision(ortRun: OrtRun, analyzerRun: AnalyzerRun?): String =
    analyzerRun?.projects?.find { "/" !in it.definitionFilePath }?.vcsProcessed?.revision ?: ortRun.revision
