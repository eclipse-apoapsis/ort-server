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

package org.ossreviewtoolkit.server.workers.evaluator

import org.ossreviewtoolkit.server.dao.blockingQueryCatching
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.workers.common.JobIgnoredException
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.mapToModel
import org.ossreviewtoolkit.server.workers.common.mapToOrt

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EvaluatorWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class EvaluatorWorker(private val runner: EvaluatorRunner, private val dao: EvaluatorWorkerDao) {
    fun run(jobId: Long, traceId: String): RunResult = runCatching {
        val evaluatorJob = blockingQueryCatching { getValidEvaluatorJob(jobId) }.getOrThrow()
        val ortRun = blockingQueryCatching { dao.getOrtRun(evaluatorJob.ortRunId) }.getOrThrow()
        requireNotNull(ortRun) {
            "ORT run '${evaluatorJob.ortRunId}' not found."
        }

        val repository = blockingQueryCatching { dao.getRepository(ortRun.repositoryId) }.getOrThrow()
        requireNotNull(repository) {
            "Repository '${ortRun.repositoryId}' not found."
        }

        val analyzerRun = blockingQueryCatching { dao.getAnalyzerRunForEvaluatorJob(evaluatorJob) }.getOrThrow()
        val advisorRun = blockingQueryCatching { dao.getAdvisorRunForEvaluatorJob(evaluatorJob) }.getOrThrow()

        // TODO: As soon as ScannerRun is implemented, it should be considered also in the mapping of an OrtResult.
        val ortResult = ortRun.mapToOrt(
            repository = repository.mapToOrt(ortRun.revision),
            analyzerRun = analyzerRun?.mapToOrt(),
            advisorRun = advisorRun?.mapToOrt()
        )

        val evaluatorRun = runner.run(ortResult, evaluatorJob.configuration)

        blockingQueryCatching {
            getValidEvaluatorJob(evaluatorJob.id)
            dao.storeEvaluatorRun(evaluatorRun.mapToModel(evaluatorJob.id))
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
        dao.getEvaluatorJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The evaluator job '$jobId' does not exist.")

    private fun EvaluatorJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Evaluator job '$id' status is already set to '$status'")
        }
    }
}
