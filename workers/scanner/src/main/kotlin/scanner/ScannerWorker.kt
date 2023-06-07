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

package org.ossreviewtoolkit.server.workers.scanner

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.ScannerJob
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.workers.common.JobIgnoredException
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.mapToModel
import org.ossreviewtoolkit.server.workers.common.mapToOrt

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ScannerWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

class ScannerWorker(
    private val db: Database,
    private val runner: ScannerRunner,
    private val dao: ScannerWorkerDao
) {
    fun run(jobId: Long, traceId: String): RunResult = runCatching {
        val (scannerJob, ortResult) = db.blockingQuery {
            val scannerJob = getValidScannerJob(jobId)
            val ortRun = dao.getOrtRun(scannerJob.ortRunId)
            requireNotNull(ortRun) {
                "ORT run '${scannerJob.ortRunId}' not found."
            }

            val repository = dao.getRepository(ortRun.repositoryId)
            requireNotNull(repository) {
                "Repository '${ortRun.repositoryId}' not found."
            }

            val analyzerRun = dao.getAnalyzerRunForScannerJob(scannerJob)
            val ortResult = ortRun.mapToOrt(
                repository = repository.mapToOrt(findResolvedRevision(ortRun, analyzerRun)),
                analyzerRun = analyzerRun?.mapToOrt()
            )

            Pair(scannerJob, ortResult)
        }

        val scannerRun = runner.run(ortResult, scannerJob.configuration).scanner
            ?: throw ScannerException("ORT Scanner failed to create a result.")

        db.blockingQuery {
            getValidScannerJob(scannerJob.id)
            dao.storeScannerRun(scannerRun.mapToModel(scannerJob.id))
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

    private fun getValidScannerJob(jobId: Long) =
        dao.getScannerJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The scanner job '$jobId' does not exist.")

    private fun ScannerJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Scanner job '$id' status is already set to '$status'.")
        }
    }
}

private class ScannerException(message: String) : Exception(message)

/**
 * Obtain the correct resolved revision for the current [ortRun] and [analyzerRun].
 * TODO: This is a work-around. The resolved revision should be stored already when creating the Analyzer result.
 */
internal fun findResolvedRevision(ortRun: OrtRun, analyzerRun: AnalyzerRun?): String =
    analyzerRun?.projects?.find { "/" !in it.definitionFilePath }?.vcsProcessed?.revision ?: ortRun.revision
