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
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest
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

            ortRun.id to listOf { scheduleConfigWorkerJob(ortRun, header) }
        }.onSuccess { (runId, createdJobs) ->
            scheduleCreatedJobs(runId, createdJobs)
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

            ortRun.id to listOf(createAnalyzerJob(ortRun).let { { scheduleAnalyzerJob(ortRun, it, header) } })
        }.onSuccess { (runId, createdJobs) ->
            scheduleCreatedJobs(runId, createdJobs)
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
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val jobId = analyzerWorkerResult.jobId

            val analyzerJob = requireNotNull(workerJobRepositories.analyzerJobRepository.get(jobId)) {
                "Analyzer job '$jobId' not found."
            }

            workerJobRepositories.analyzerJobRepository.update(
                id = analyzerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            val ortRun = requireNotNull(ortRunRepository.get(analyzerJob.ortRunId)) {
                "ORT run '${analyzerJob.ortRunId}' not found."
            }

            val createdJobs = listOfNotNull(
                createAdvisorJob(ortRun)?.let { { scheduleAdvisorJob(ortRun, it, header) } },
                createScannerJob(ortRun)?.let { { scheduleScannerJob(ortRun, it, header) } }
            ).toMutableList()

            // Create an evaluator job only if the advisor and scanner jobs have finished successfully.
            if (createdJobs.isEmpty()) {
                if (getConfig(ortRun).evaluator != null) {
                    // Create an evaluator job if no advisor or scanner job is configured.
                    createEvaluatorJob(ortRun)?.let { job ->
                        createdJobs += { scheduleEvaluatorJob(ortRun, job, header) }
                    }
                } else {
                    // Create a reporter job if no advisor, scanner or evaluator job is configured.
                    createReporterJob(ortRun)?.let { job ->
                        createdJobs += { scheduleReporterJob(ortRun, job, header) }
                    }
                }
            }

            analyzerJob.ortRunId to createdJobs
        }.onSuccess { (runId, createdJobs) ->
            scheduleCreatedJobs(runId, createdJobs)
        }.onFailure {
            log.warn("Failed to handle 'AnalyzerWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAnalyzerWorkerError(analyzerWorkerError: AnalyzerWorkerError) {
        handleWorkerError(workerJobRepositories.analyzerJobRepository, analyzerWorkerError)
    }

    /**
     * Handle messages of the type [AdvisorWorkerResult].
     */
    fun handleAdvisorWorkerResult(header: MessageHeader, advisorWorkerResult: AdvisorWorkerResult) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val jobId = advisorWorkerResult.jobId

            val advisorJob = requireNotNull(workerJobRepositories.advisorJobRepository.get(jobId)) {
                "Advisor job '$jobId' not found."
            }

            workerJobRepositories.advisorJobRepository.update(
                id = advisorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            val ortRun = requireNotNull(ortRunRepository.get(advisorJob.ortRunId)) {
                "ORT run '${advisorJob.ortRunId}' not found."
            }

            val createdJobs = mutableListOf<JobScheduleFunc>()

            // Create an evaluator or reporter job only if both the advisor and scanner jobs have finished successfully
            // or the scanner job is skipped.
            val scannerJobStatus = workerJobRepositories.scannerJobRepository.getForOrtRun(ortRun.id)?.status
                ?: JobStatus.FINISHED
            if (scannerJobStatus == JobStatus.FINISHED) {
                if (getConfig(ortRun).evaluator != null) {
                    createEvaluatorJob(ortRun)?.let { job ->
                        createdJobs += { scheduleEvaluatorJob(ortRun, job, header) }
                    }
                } else {
                    createReporterJob(ortRun)?.let { job ->
                        createdJobs += { scheduleReporterJob(ortRun, job, header) }
                    }
                }
            } else {
                createdJobs += dummyScheduleFunc
            }

            advisorJob.ortRunId to createdJobs
        }.onSuccess { (runId, createdJobs) ->
            scheduleCreatedJobs(runId, createdJobs)
        }.onFailure {
            log.warn("Failed to handle 'AdvisorWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAdvisorWorkerError(advisorWorkerError: AdvisorWorkerError) {
        handleWorkerError(workerJobRepositories.advisorJobRepository, advisorWorkerError)
    }

    /**
     * Handle messages of the type [ScannerWorkerResult].
     */
    fun handleScannerWorkerResult(header: MessageHeader, scannerWorkerResult: ScannerWorkerResult) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val jobId = scannerWorkerResult.jobId

            val scannerJob = requireNotNull(workerJobRepositories.scannerJobRepository.get(jobId)) {
                "Scanner job '$jobId' not found."
            }

            workerJobRepositories.scannerJobRepository.update(
                id = scannerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            val ortRun = requireNotNull(ortRunRepository.get(scannerJob.ortRunId)) {
                "ORT run '${scannerJob.ortRunId}' not found."
            }

            val createdJobs = mutableListOf<JobScheduleFunc>()

            // Create an evaluator or reporter job only if both the advisor and scanner jobs have finished successfully
            // or the advisor job is skipped.
            val advisorJobStatus = workerJobRepositories.advisorJobRepository.getForOrtRun(ortRun.id)?.status
                ?: JobStatus.FINISHED
            if (advisorJobStatus == JobStatus.FINISHED) {
                if (getConfig(ortRun).evaluator != null) {
                    createEvaluatorJob(ortRun)?.let { job ->
                        createdJobs += { scheduleEvaluatorJob(ortRun, job, header) }
                    }
                } else {
                    createReporterJob(ortRun)?.let { job ->
                        createdJobs += { scheduleReporterJob(ortRun, job, header) }
                    }
                }
            } else {
                createdJobs += dummyScheduleFunc
            }

            scannerJob.ortRunId to createdJobs
        }.onSuccess { (runId, createdJobs) ->
            scheduleCreatedJobs(runId, createdJobs)
        }.onFailure {
            log.warn("Failed to handle 'ScannerWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [ScannerWorkerError].
     */
    fun handleScannerWorkerError(scannerWorkerError: ScannerWorkerError) {
        handleWorkerError(workerJobRepositories.scannerJobRepository, scannerWorkerError)
    }

    /**
     * Handle messages of the type [EvaluatorWorkerResult].
     */
    fun handleEvaluatorWorkerResult(header: MessageHeader, evaluatorWorkerResult: EvaluatorWorkerResult) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val jobId = evaluatorWorkerResult.jobId

            val evaluatorJob = requireNotNull(workerJobRepositories.evaluatorJobRepository.get(jobId)) {
                "Evaluator job '$jobId' not found."
            }

            workerJobRepositories.evaluatorJobRepository.update(
                id = evaluatorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            val ortRun = requireNotNull(ortRunRepository.get(evaluatorJob.ortRunId)) {
                "ORT run '${evaluatorJob.ortRunId}' not found."
            }

            evaluatorJob.ortRunId to listOfNotNull(
                createReporterJob(ortRun)?.let { { scheduleReporterJob(ortRun, it, header) } }
            )
        }.onSuccess { (runId, createdJobs) ->
            scheduleCreatedJobs(runId, createdJobs)
        }.onFailure {
            log.warn("Failed to handle 'EvaluatorWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [EvaluatorWorkerError].
     */
    fun handleEvaluatorWorkerError(evaluatorWorkerError: EvaluatorWorkerError) {
        handleWorkerError(workerJobRepositories.evaluatorJobRepository, evaluatorWorkerError)
    }

    /**
     * Handle messages of the type [ReporterWorkerResult].
     */
    fun handleReporterWorkerResult(header: MessageHeader, reporterWorkerResult: ReporterWorkerResult) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val jobId = reporterWorkerResult.jobId

            val reporterJob = requireNotNull(workerJobRepositories.reporterJobRepository.get(jobId)) {
                "Reporter job '$jobId' not found."
            }

            val ortRun = requireNotNull(ortRunRepository.get(reporterJob.ortRunId)) {
                "ORT run '${reporterJob.ortRunId}' not found."
            }

            workerJobRepositories.reporterJobRepository.update(
                id = reporterJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            reporterJob.ortRunId to listOfNotNull(
                createNotifierJob(ortRun)?.let { { scheduleNotifierJob(ortRun, it, header) } }
            )
        }.onSuccess { (jobId, createdJobs) ->
            scheduleCreatedJobs(jobId, createdJobs)
        }.onFailure {
            log.warn("Failed to handle 'ReporterWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [ReporterWorkerError].
     */
    fun handleReporterWorkerError(reporterWorkerError: ReporterWorkerError) {
        handleWorkerError(workerJobRepositories.reporterJobRepository, reporterWorkerError)
    }

    /**
     * Handle messages of the type [NotifierWorkerResult].
     */
    fun handleNotifierWorkerResult(notifierWorkerResult: NotifierWorkerResult) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val jobId = notifierWorkerResult.jobId

            val notifierJob = requireNotNull(workerJobRepositories.notifierJobRepository.get(jobId)) {
                "Notifier job '$jobId' not found."
            }

            workerJobRepositories.notifierJobRepository.update(
                id = notifierJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        }.onSuccess { job ->
            scheduleCreatedJobs(job.ortRunId, emptyList())
        }.onFailure {
            log.warn("Failed to handle 'NotifierWorkerResult' message.", it)
        }
    }

    fun handleNotifierWorkerError(notifierWorkerError: NotifierWorkerError) {
        handleWorkerError(workerJobRepositories.notifierJobRepository, notifierWorkerError)
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
     * Create an [AnalyzerJob].
     */
    private fun createAnalyzerJob(ortRun: OrtRun): AnalyzerJob =
        workerJobRepositories.analyzerJobRepository.create(
            ortRun.id,
            getConfig(ortRun).analyzer
        )

    /**
     * Create an [AdvisorJob] if it is enabled.
     */
    private fun createAdvisorJob(ortRun: OrtRun): AdvisorJob? =
        getConfig(ortRun).advisor?.let { advisorJobConfiguration ->
            workerJobRepositories.advisorJobRepository.create(ortRun.id, advisorJobConfiguration)
        }

    /**
     * Create a [ScannerJob] if it is enabled.
     */
    private fun createScannerJob(ortRun: OrtRun): ScannerJob? =
        getConfig(ortRun).scanner?.let { scannerJobConfiguration ->
            workerJobRepositories.scannerJobRepository.create(ortRun.id, scannerJobConfiguration)
        }

    /**
     * Create an [EvaluatorJob] if it is enabled.
     */
    private fun createEvaluatorJob(ortRun: OrtRun): EvaluatorJob? =
        getConfig(ortRun).evaluator?.let { evaluatorJobConfiguration ->
            workerJobRepositories.evaluatorJobRepository.create(ortRun.id, evaluatorJobConfiguration)
        }

    /**
     * Create a [ReporterJob] if it is enabled.
     */
    private fun createReporterJob(ortRun: OrtRun): ReporterJob? =
        getConfig(ortRun).reporter?.let { reporterJobConfiguration ->
            workerJobRepositories.reporterJobRepository.create(ortRun.id, reporterJobConfiguration)
        }

    /**
     * Create a [NotifierJob] if it is enabled.
     */
    private fun createNotifierJob(ortRun: OrtRun): NotifierJob? =
        getConfig(ortRun).notifier?.let { notifierJobConfiguration ->
            workerJobRepositories.notifierJobRepository.create(ortRun.id, notifierJobConfiguration)
        }

    private fun scheduleCreatedJobs(runId: Long, createdJobs: CreatedJobs) {
        // TODO: Handle errors during job scheduling.

        createdJobs.forEach { it() }

        if (createdJobs.isEmpty()) {
            cleanupJobs(runId)

            ortRunRepository.update(runId, OrtRunStatus.FINISHED.asPresent())
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
     * Publish a message to the [AdvisorEndpoint] and update the [advisorJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleAdvisorJob(run: OrtRun, advisorJob: AdvisorJob, header: MessageHeader) {
        publish(AdvisorEndpoint, run, header, AdvisorRequest(advisorJob.id))

        workerJobRepositories.advisorJobRepository.update(
            advisorJob.id,
            status = JobStatus.SCHEDULED.asPresent()
        )
    }

    /**
     * Publish a message to the [ScannerEndpoint] and update the [scannerJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleScannerJob(run: OrtRun, scannerJob: ScannerJob, header: MessageHeader) {
        publish(ScannerEndpoint, run, header, ScannerRequest(scannerJob.id))

        workerJobRepositories.scannerJobRepository.update(
            id = scannerJob.id,
            status = JobStatus.SCHEDULED.asPresent()
        )
    }

    /**
     * Publish a message to the [EvaluatorEndpoint] and update the [evaluatorJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleEvaluatorJob(run: OrtRun, evaluatorJob: EvaluatorJob, header: MessageHeader) {
        publish(EvaluatorEndpoint, run, header, EvaluatorRequest(evaluatorJob.id))

        workerJobRepositories.evaluatorJobRepository.update(
            id = evaluatorJob.id,
            status = JobStatus.SCHEDULED.asPresent()
        )
    }

    /**
     * Publish a message to the [ReporterEndpoint] and update the [reporterJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleReporterJob(run: OrtRun, reporterJob: ReporterJob, header: MessageHeader) {
        publish(ReporterEndpoint, run, header, ReporterRequest(reporterJob.id))

        workerJobRepositories.reporterJobRepository.update(
            id = reporterJob.id,
            status = JobStatus.SCHEDULED.asPresent()
        )
    }

    /**
     * Publish a message to the [NotifierEndpoint] and update the [notifierJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleNotifierJob(run: OrtRun, notifierJob: NotifierJob, header: MessageHeader) {
        publish(NotifierEndpoint, run, header, NotifierRequest(notifierJob.id))

        workerJobRepositories.notifierJobRepository.update(
            id = notifierJob.id,
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
     * Handle an error notification [message] from a worker job using the given [jobRepository]. Mark both the job and
     * the whole OrtRun as failed.
     */
    private fun <T : WorkerJob> handleWorkerError(jobRepository: WorkerJobRepository<T>, message: WorkerMessage) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val updatedJob = jobRepository.complete(message.jobId, Clock.System.now(), JobStatus.FAILED)

            cleanupJobs(updatedJob.ortRunId)

            // If the worker job failed, the whole OrtRun will be treated as failed.
            failOrtRun(updatedJob.ortRunId)
        }.onFailure {
            log.warn("Failed to handle '${message::class.java.simpleName}' message.", it)
        }
    }

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
 * Type definition for a function that schedules another worker job.
 */
typealias JobScheduleFunc = () -> Unit

/**
 * Type definition to represent a list of jobs that have been created and must be scheduled.
 */
typealias CreatedJobs = List<JobScheduleFunc>

/**
 * A [JobScheduleFunc] that does not schedule any job. This is used if after handling a result message no job can be
 * scheduled, but the ORT run is not yet complete.
 */
private val dummyScheduleFunc: JobScheduleFunc = {}
