/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.orchestrator

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorRequest
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.transport.AdvisorEndpoint
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessagePublisher

import org.slf4j.LoggerFactory

/**
 * The Orchestrator is the central component that breaks an ORT run into single steps and coordinates their execution.
 * It creates jobs for the single processing steps and passes them to the corresponding workers. It collects the results
 * produced by the workers until the complete ORT result is available or the run has failed.
 */
class Orchestrator(
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val advisorJobRepository: AdvisorJobRepository,
    private val repositoryRepository: RepositoryRepository,
    private val ortRunRepository: OrtRunRepository,
    private val publisher: MessagePublisher
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Handle messages of the type [CreateOrtRun].
     */
    fun handleCreateOrtRun(header: MessageHeader, createOrtRun: CreateOrtRun) {
        val ortRun = createOrtRun.ortRun

        val repository = repositoryRepository.get(ortRun.repositoryId)
        if (repository != null) {
            val analyzerJob = analyzerJobRepository.create(
                ortRun.id,
                ortRun.jobs.analyzer
            )

            publisher.publish(
                AnalyzerEndpoint,
                Message(
                    header = header,
                    payload = AnalyzerRequest(analyzerJob.id)
                )
            )

            analyzerJobRepository.update(
                analyzerJob.id,
                startedAt = Clock.System.now().asPresent(),
                status = JobStatus.SCHEDULED.asPresent()
            )
        } else {
            log.warn("Failed to schedule Analyzer job. Repository '${ortRun.repositoryId}' not found.")
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerResult].
     */
    fun handleAnalyzerWorkerResult(header: MessageHeader, analyzerWorkerResult: AnalyzerWorkerResult) {
        val jobId = analyzerWorkerResult.jobId

        val analyzerJob = analyzerJobRepository.get(jobId)

        if (analyzerJob != null) {
            analyzerJobRepository.update(
                id = analyzerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'AnalyzeResult' message. No analyzer job '$jobId' found.")
            return
        }

        val ortRun = ortRunRepository.get(analyzerJob.ortRunId)

        if (ortRun == null) {
            log.warn("Failed to handle 'AnalyzeResult' message. No ORT run '${analyzerJob.ortRunId}' found.")
            return
        }
        val advisorJob = advisorJobRepository.create(analyzerJob.ortRunId, ortRun.jobs.advisor)

        publisher.publish(AdvisorEndpoint, Message(header = header, payload = AdvisorRequest(advisorJob.id)))

        advisorJobRepository.update(
            advisorJob.id,
            startedAt = Clock.System.now().asPresent(),
            status = JobStatus.SCHEDULED.asPresent()
        )
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAnalyzerWorkerError(analyzerWorkerError: AnalyzerWorkerError) {
        val jobId = analyzerWorkerError.jobId

        val analyzerJob = analyzerJobRepository.get(jobId)

        if (analyzerJob != null) {
            analyzerJobRepository.update(
                id = analyzerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FAILED.asPresent()
            )

            // If the analyzerJob failed, the whole OrtRun will be treated as failed.
            ortRunRepository.update(
                id = analyzerJob.ortRunId,
                status = OrtRunStatus.FAILED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'AnalyzeError' message. No analyzer job ORT run '$jobId' found.")
        }
    }

    /**
     * Handle messages of the type [AdvisorWorkerResult].
     */
    fun handleAdvisorWorkerResult(advisorWorkerResult: AdvisorWorkerResult) {
        val jobId = advisorWorkerResult.jobId

        val advisorJob = advisorJobRepository.get(jobId)

        if (advisorJob != null) {
            advisorJobRepository.update(
                id = advisorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'AdviseResult' message. No advisor job '$jobId' found.")
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAdvisorWorkerError(advisorWorkerError: AdvisorWorkerError) {
        val jobId = advisorWorkerError.jobId

        val advisorJob = advisorJobRepository.get(jobId)

        if (advisorJob != null) {
            advisorJobRepository.update(
                id = advisorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FAILED.asPresent()
            )

            // If the advisorJob failed, the whole OrtRun will be treated as failed.
            ortRunRepository.update(
                id = advisorJob.ortRunId,
                status = OrtRunStatus.FAILED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'AdviseError' message. No advisor job ORT run '$jobId' found.")
        }
    }
}
