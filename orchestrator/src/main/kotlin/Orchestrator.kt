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

import com.typesafe.config.Config

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.model.AnalyzerJobStatus
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint

import org.slf4j.LoggerFactory

class Orchestrator(
    private val config: Config,
    private val schedulerService: SchedulerService,
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val repositoryRepository: RepositoryRepository,
    private val ortRunRepository: OrtRunRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Start the Orchestrator and receive messages from the [MessageReceiverFactory].
     */
    fun start() {
        MessageReceiverFactory.createReceiver(OrchestratorEndpoint, config) { message ->
            log.info("Received '${message.payload::class.simpleName}' message. TraceID: '${message.header.traceId}'.")

            when (message.payload) {
                is CreateOrtRun -> handleCreateOrtRun(message.header, message.payload as CreateOrtRun)

                is AnalyzerWorkerResult -> handleAnalyzerWorkerResult(message.payload as AnalyzerWorkerResult)

                is AnalyzerWorkerError -> handleAnalyzerWorkerError(message.payload as AnalyzerWorkerError)

                else -> TODO("Support for message type '${message.payload::class.simpleName}' not yet implemented.")
            }
        }
    }

    /**
     * Handle messages of the type [CreateOrtRun].
     */
    private fun handleCreateOrtRun(header: MessageHeader, createOrtRun: CreateOrtRun) {
        val ortRun = createOrtRun.ortRun

        val repository = repositoryRepository.get(ortRun.repositoryId)
        if (repository != null) {
            val analyzerJob = analyzerJobRepository.create(
                ortRun.id,
                ortRun.jobs.analyzer
            )

            schedulerService.scheduleAnalyzerJob(
                messageHeader = header,
                repository = repository,
                ortRun = ortRun,
                analyzerJob = analyzerJob
            )

            analyzerJobRepository.update(
                analyzerJob.id,
                startedAt = OptionalValue.Present(Clock.System.now()),
                status = OptionalValue.Present(AnalyzerJobStatus.SCHEDULED)
            )
        } else {
            log.warn("Failed to schedule Analyzer job. Repository '${ortRun.repositoryId}' not found.")
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerResult].
     */
    private fun handleAnalyzerWorkerResult(analyzerWorkerResult: AnalyzerWorkerResult) {
        val jobId = analyzerWorkerResult.jobId

        val analyzerJob = analyzerJobRepository.get(jobId)

        if (analyzerJob != null) {
            analyzerJobRepository.update(
                id = analyzerJob.id,
                finishedAt = OptionalValue.Present(Clock.System.now()),
                status = OptionalValue.Present(AnalyzerJobStatus.FINISHED)
            )
        } else {
            log.warn("Failed to handle 'AnalyzeResult' message. No analyzer job '$jobId' found.")
        }
        // TODO: Retrieve the OrtRun from the DB, and schedule the subsequent jobs.
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    private fun handleAnalyzerWorkerError(analyzerWorkerError: AnalyzerWorkerError) {
        val jobId = analyzerWorkerError.jobId

        val analyzerJob = analyzerJobRepository.get(jobId)

        if (analyzerJob != null) {
            analyzerJobRepository.update(
                id = analyzerJob.id,
                status = OptionalValue.Present(AnalyzerJobStatus.FAILED)
            )

            // If the analyzerJob failed, the whole OrtRun will be treated as failed.
            ortRunRepository.update(
                id = jobId,
                status = OptionalValue.Present(OrtRunStatus.FAILED)
            )
        } else {
            log.warn("Failed to handle 'AnalyzeError' message. No analyzer job ORT run '$jobId' found.")
        }
    }
}
