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

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.mapToModel

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AdvisorWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class AdvisorWorker(
    private val receiver: AdvisorReceiver,
    private val runner: AdvisorRunner,
    private val dao: AdvisorWorkerDao
) {
    fun start() = receiver.receive(::run)

    private fun run(advisorJobId: Long, traceId: String): RunResult = blockingQuery {
        val advisorJob = dao.getAdvisorJob(advisorJobId)
            ?: return@blockingQuery RunResult.Failed(
                IllegalArgumentException("The advisor job '$advisorJobId' does not exist.")
            )

        if (advisorJob.status in invalidStates) {
            logger.warn(
                "Advisor job '$advisorJobId' status is already set to '${advisorJob.status}. Ignoring messages with " +
                        "traceId '$traceId'."
            )

            return@blockingQuery RunResult.Ignored
        }

        // TODO: Add more arguments to this function/class to retrieve more information for the construction of the
        //       OrtResult (e.g. AnalyzerRunRepository, OrtRunRepository, RepositoryRepository).
        val ortResult = OrtResult(Repository(VcsInfo.EMPTY))

        logger.debug("Advisor job with id '$advisorJobId' started at ${advisorJob.startedAt}.")
        val advisorRun = runner.run(ortResult, advisorJob.configuration).advisor
            ?: throw AdvisorException("ORT Advisor failed to create a result.")

        dao.storeAdvisorRun(advisorRun.mapToModel(advisorJobId))

        RunResult.Success
    }.getOrElse { RunResult.Failed(it) }
}

private class AdvisorException(message: String) : Exception(message)
