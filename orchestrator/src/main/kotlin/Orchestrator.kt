/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.orchestrator

import java.sql.Connection

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.blockingQueryCatching
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerMessage
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint
import org.eclipse.apoapsis.ortserver.transport.selectByPrefix

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(Orchestrator::class.java)

/**
 * The Orchestrator is the central component that breaks an ORT run into single steps and coordinates their execution.
 * It creates jobs for the single processing steps and passes them to the corresponding workers. It collects the results
 * produced by the workers until the complete ORT result is available or the run has failed.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class Orchestrator(
    private val db: Database,
    private val workerJobRepositories: WorkerJobRepositories,
    private val repositoryRepository: RepositoryRepository,
    private val ortRunRepository: OrtRunRepository,
    private val publisher: MessagePublisher
) {
    private val isolationLevel = Connection.TRANSACTION_SERIALIZABLE

    /**
     * Handle messages of the type [CreateOrtRun].
     */
    fun handleCreateOrtRun(header: MessageHeader, createOrtRun: CreateOrtRun) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val ortRun = createOrtRun.ortRun

            requireNotNull(repositoryRepository.get(ortRun.repositoryId)) {
                "Repository '${ortRun.repositoryId}' not found."
            }

            val context = WorkerScheduleContext(ortRun, workerJobRepositories, publisher, header, emptyMap())
            context to listOf { scheduleConfigWorkerJob(ortRun, header) }
        }.onSuccess { (context, createdJobs) ->
            scheduleCreatedJobs(context, createdJobs)
        }.onFailure {
            log.warn("Failed to handle 'CreateOrtRun' message.", it)
        }
    }

    /**
     * Handle messages of the type [ConfigWorkerResult].
     */
    fun handleConfigWorkerResult(header: MessageHeader, configWorkerResult: ConfigWorkerResult) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val ortRun = requireNotNull(ortRunRepository.get(configWorkerResult.ortRunId)) {
                "ORT run '${configWorkerResult.ortRunId}' not found."
            }

            val context = WorkerScheduleContext(ortRun, workerJobRepositories, publisher, header, emptyMap())
            context to listOf(createAnalyzerJob(ortRun).let { { scheduleAnalyzerJob(ortRun, it, header) } })
        }.onSuccess { (context, createdJobs) ->
            scheduleCreatedJobs(context, createdJobs)
        }.onFailure {
            log.warn("Failed to handle 'ConfigWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [ConfigWorkerError].
     */
    fun handleConfigWorkerError(configWorkerError: ConfigWorkerError) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            ortRunRepository.update(
                id = configWorkerError.ortRunId,
                status = OrtRunStatus.FAILED.asPresent()
            )
        }.onFailure {
            log.warn("Failed to handle 'ConfigWorkerError' message.", it)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerResult].
     */
    fun handleAnalyzerWorkerResult(header: MessageHeader, analyzerWorkerResult: AnalyzerWorkerResult) {
        handleWorkerResult(AnalyzerEndpoint, header, analyzerWorkerResult)
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAnalyzerWorkerError(header: MessageHeader, analyzerWorkerError: AnalyzerWorkerError) {
        handleWorkerError(AnalyzerEndpoint, header, analyzerWorkerError)
    }

    /**
     * Handle messages of the type [AdvisorWorkerResult].
     */
    fun handleAdvisorWorkerResult(header: MessageHeader, advisorWorkerResult: AdvisorWorkerResult) {
        handleWorkerResult(AdvisorEndpoint, header, advisorWorkerResult)
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAdvisorWorkerError(header: MessageHeader, advisorWorkerError: AdvisorWorkerError) {
        handleWorkerError(AdvisorEndpoint, header, advisorWorkerError)
    }

    /**
     * Handle messages of the type [ScannerWorkerResult].
     */
    fun handleScannerWorkerResult(header: MessageHeader, scannerWorkerResult: ScannerWorkerResult) {
        handleWorkerResult(ScannerEndpoint, header, scannerWorkerResult)
    }

    /**
     * Handle messages of the type [ScannerWorkerError].
     */
    fun handleScannerWorkerError(header: MessageHeader, scannerWorkerError: ScannerWorkerError) {
        handleWorkerError(ScannerEndpoint, header, scannerWorkerError)
    }

    /**
     * Handle messages of the type [EvaluatorWorkerResult].
     */
    fun handleEvaluatorWorkerResult(header: MessageHeader, evaluatorWorkerResult: EvaluatorWorkerResult) {
        handleWorkerResult(EvaluatorEndpoint, header, evaluatorWorkerResult)
    }

    /**
     * Handle messages of the type [EvaluatorWorkerError].
     */
    fun handleEvaluatorWorkerError(header: MessageHeader, evaluatorWorkerError: EvaluatorWorkerError) {
        handleWorkerError(EvaluatorEndpoint, header, evaluatorWorkerError)
    }

    /**
     * Handle messages of the type [ReporterWorkerResult].
     */
    fun handleReporterWorkerResult(header: MessageHeader, reporterWorkerResult: ReporterWorkerResult) {
        handleWorkerResult(ReporterEndpoint, header, reporterWorkerResult)
    }

    /**
     * Handle messages of the type [ReporterWorkerError].
     */
    fun handleReporterWorkerError(header: MessageHeader, reporterWorkerError: ReporterWorkerError) {
        handleWorkerError(ReporterEndpoint, header, reporterWorkerError)
    }

    /**
     * Handle messages of the type [NotifierWorkerResult].
     */
    fun handleNotifierWorkerResult(header: MessageHeader, notifierWorkerResult: NotifierWorkerResult) {
        handleWorkerResult(NotifierEndpoint, header, notifierWorkerResult)
    }

    fun handleNotifierWorkerError(header: MessageHeader, notifierWorkerError: NotifierWorkerError) {
        handleWorkerError(NotifierEndpoint, header, notifierWorkerError)
    }

    /**
     * Handle messages of the type [WorkerError] for the given [ortRunId].
     */
    fun handleWorkerError(ortRunId: Long, workerError: WorkerError) {
        log.info("Handling a worker error of type '{}' for ORT run {}.", workerError.endpointName, ortRunId)

        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            workerJobRepositories[workerError.endpointName]?.let { repository ->
                handleWorkerErrorForRepository(ortRunId, repository)
            } ?: failOrtRun(ortRunId)
        }.onFailure {
            log.warn("Failed to handle 'WorkerError' message.", it)
        }
    }

    /**
     * Handle the given [result] message with the given [header] from a worker of the given [endpoint].
     */
    private fun handleWorkerResult(endpoint: Endpoint<*>, header: MessageHeader, result: WorkerMessage) {
        handleCompletedJob(endpoint, header, result, JobStatus.FINISHED)
    }

    /**
     * Handle the given [error] message with the given [header] from a worker of the given [endpoint].
     */
    private fun handleWorkerError(endpoint: Endpoint<*>, header: MessageHeader, error: WorkerMessage) {
        handleCompletedJob(endpoint, header, error, JobStatus.FAILED)
    }

    /**
     * Handle a [message] with the given [header] about a worker job for the given [endpoint] that completed in the
     * given [status]. This is the central scheduling function. It determines the current job execution state of the
     * affected ORT run, schedules the next job(s) if possible, or decides that the ORT run is now finished.
     */
    private fun handleCompletedJob(
        endpoint: Endpoint<*>,
        header: MessageHeader,
        message: WorkerMessage,
        status: JobStatus
    ) {
        log.info(
            "Job {} for endpoint '{}' completed in status '{}'.",
            message.jobId,
            endpoint.configPrefix,
            status.name
        )

        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val job = workerJobRepositories.updateJobStatus(endpoint, message.jobId, status)

            log.info("Handling a completed job for endpoint '{}' and ORT run {}.", endpoint.configPrefix, job.ortRunId)

            val ortRun = requireNotNull(ortRunRepository.get(job.ortRunId)) {
                "ORT run '${job.ortRunId}' not found."
            }

            val jobs = workerJobRepositories.jobRepositories.mapNotNull { (endpoint, repository) ->
                repository.getForOrtRun(ortRun.id)?.let { endpoint to it }
            }.toMap()

            val scheduleContext = WorkerScheduleContext(ortRun, workerJobRepositories, publisher, header, jobs)
            val schedules = if (scheduleContext.isFailed()) {
                emptyList()
            } else {
                scheduleInfos.values.mapNotNull { it.createAndScheduleJobIfPossible(scheduleContext) }
            }

            scheduleContext to schedules
        }.onSuccess { (context, schedules) ->
            scheduleCreatedJobs(context, schedules)
        }.onFailure {
            log.warn("Failed to handle '{}' message.", message::class.java.simpleName, it)
        }
    }

    /**
     * Create an [AnalyzerJob].
     */
    private fun createAnalyzerJob(ortRun: OrtRun): AnalyzerJob =
        workerJobRepositories.analyzerJobRepository.create(
            ortRun.id,
            getConfig(ortRun).analyzer
        )

    /**
     * Trigger the scheduling of the given new [createdJobs] for the ORT run contained in the given [context]. This
     * also includes sending corresponding messages to the worker endpoints.
     */
    private fun scheduleCreatedJobs(context: WorkerScheduleContext, createdJobs: CreatedJobs) {
        // TODO: Handle errors during job scheduling.

        createdJobs.forEach { it() }

        if (createdJobs.isEmpty() && !context.hasRunningJobs()) {
            cleanupJobs(context.ortRun.id)

            val ortRunStatus = if (context.isFailed()) {
                OrtRunStatus.FAILED
            } else {
                OrtRunStatus.FINISHED
            }

            log.info("Setting the final status of ORT run {} to '{}'.", context.ortRun.id, ortRunStatus.name)

            ortRunRepository.update(context.ortRun.id, ortRunStatus.asPresent())
        }
    }

    /**
     * Publish a message to the [ConfigEndpoint] and update the current ORT run to the active state.
     */
    private fun scheduleConfigWorkerJob(run: OrtRun, header: MessageHeader) {
        publish(ConfigEndpoint, run, header, ConfigRequest(run.id))

        ortRunRepository.update(run.id, OrtRunStatus.ACTIVE.asPresent())
    }

    /**
     * Publish a message to the [AnalyzerEndpoint] and update the [analyzerJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleAnalyzerJob(run: OrtRun, analyzerJob: AnalyzerJob, header: MessageHeader) {
        publish(AnalyzerEndpoint, run, header, AnalyzerRequest(analyzerJob.id))

        workerJobRepositories.analyzerJobRepository.update(
            analyzerJob.id,
            status = JobStatus.SCHEDULED.asPresent()
        )
    }

    /**
     * Create a message based on the given [run], [header], and [payload] and publish it to the given [endpoint].
     * Make sure that the header contains the correct transport properties. These are obtained from the labels of the
     * current ORT run.
     */
    private fun <T : Any> publish(endpoint: Endpoint<T>, run: OrtRun, header: MessageHeader, payload: T) {
        val headerWithProperties = header.copy(transportProperties = run.labels.selectByPrefix("transport"))

        publisher.publish(to = endpoint, message = Message(headerWithProperties, payload))
    }

    /**
     * Return the resolved job configurations if available. Otherwise, return the original job configurations.
     */
    private fun getConfig(ortRun: OrtRun) = ortRun.resolvedJobConfigs ?: ortRun.jobConfigs

    /**
     * Cleanup the jobs for the given [ortRunId] by deleting the mail recipients of the corresponding notifier job.
     */
    private fun cleanupJobs(ortRunId: Long) {
        ortRunRepository.get(ortRunId)?.let { ortRun ->
            ortRunRepository.update(
                id = ortRunId,
                jobConfigs = OptionalValue.Present(cleanupJobConfigs(ortRun.jobConfigs)),
                resolvedJobConfigs = ortRun.resolvedJobConfigs?.let {
                    OptionalValue.Present(cleanupJobConfigs(it))
                } ?: OptionalValue.Absent
            )
        }

        workerJobRepositories.notifierJobRepository.getForOrtRun(ortRunId)?.let { notifierJob ->
            workerJobRepositories.notifierJobRepository.deleteMailRecipients(notifierJob.id)
        }
    }

    private fun cleanupJobConfigs(jobConfigs: JobConfigurations) = jobConfigs.copy(
        notifier = jobConfigs.notifier?.copy(
            mail = jobConfigs.notifier?.mail?.copy(recipientAddresses = emptyList())
        ),
        parameters = jobConfigs.parameters - "recipientAddresses"
    )

    /**
     * Handle a fatal worker error for the given [ortRunId] which affects the worker managed by the given [repository].
     */
    private fun <T : WorkerJob> handleWorkerErrorForRepository(ortRunId: Long, repository: WorkerJobRepository<T>) {
        val job = requireNotNull(repository.getForOrtRun(ortRunId)) {
            "ORT run '$ortRunId' not found."
        }

        repository.tryComplete(job.id, Clock.System.now(), JobStatus.FAILED)?.let {
            failOrtRun(ortRunId)
        }
    }

    /**
     * Set the status of the ORT run identified by the given [ortRunId] as failed.
     */
    private fun failOrtRun(ortRunId: Long) {
        ortRunRepository.update(
            id = ortRunId,
            status = OrtRunStatus.FAILED.asPresent()
        )
    }
}

/**
 * Type definition to represent a list of jobs that have been created and must be scheduled.
 */
typealias CreatedJobs = List<JobScheduleFunc>
