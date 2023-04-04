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

package org.ossreviewtoolkit.server.workers.advisor

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.workers.common.JobIgnoredException
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.mapToModel
import org.ossreviewtoolkit.server.workers.common.mapToOrt

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AdvisorWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class AdvisorWorker(
    private val runner: AdvisorRunner,
    private val dao: AdvisorWorkerDao
) {
    fun run(advisorJobId: Long, traceId: String): RunResult = runCatching {
        val advisorJob = blockingQuery { getValidAdvisorJob(advisorJobId) }.getOrThrow()
        val analyzerRun = blockingQuery { dao.getAnalyzerRunForAdvisorJob(advisorJob) }.getOrThrow()

        logger.debug("Advisor job with id '${advisorJob.id}' started at ${advisorJob.startedAt}.")

        val advisorRun = runner.run(
            packages = analyzerRun?.packages?.mapTo(mutableSetOf()) { it.mapToOrt() }.orEmpty(),
            config = advisorJob.configuration
        )

        blockingQuery {
            getValidAdvisorJob(advisorJobId)
            dao.storeAdvisorRun(advisorRun.mapToModel(advisorJobId))
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

    private fun getValidAdvisorJob(jobId: Long) =
        dao.getAdvisorJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The advisor job '$jobId' does not exist.")

    private fun AdvisorJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Advisor job '$id' status is already set to '$status'.")
        }
    }
}
