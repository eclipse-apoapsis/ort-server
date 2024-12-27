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
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.LostSchedule
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
import org.eclipse.apoapsis.ortserver.model.runs.Issue
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
            context to listOf { scheduleConfigWorkerJob(ortRun, header, updateRun = true) }
        }.scheduleNextJobs {
            log.warn("Failed to handle 'CreateOrtRun' message.", it)
        }
    }

    /**
     * Handle messages of the type [ConfigWorkerResult].
     */
    fun handleConfigWorkerResult(header: MessageHeader, configWorkerResult: ConfigWorkerResult) {
        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val ortRun = getCurrentOrtRun(configWorkerResult.ortRunId)

            nextJobsToSchedule(ConfigEndpoint, ortRun.id, header, jobs = emptyMap())
        }.scheduleNextJobs {
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
                status = OrtRunStatus.FAILED.asPresent(),
                issues = listOf(ConfigEndpoint.createErrorIssue()).asPresent()
            )
        }.onFailure {
            log.warn("Failed to handle 'ConfigWorkerError' message.", it)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerResult].
     */
    fun handleAnalyzerWorkerResult(header: MessageHeader, analyzerWorkerResult: AnalyzerWorkerResult) {
        if (!analyzerWorkerResult.hasIssues) {
            handleWorkerResult(AnalyzerEndpoint, header, analyzerWorkerResult)
        } else {
            handleWorkerResultWithIssues(AnalyzerEndpoint, header, analyzerWorkerResult)
        }
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
        if (!advisorWorkerResult.hasIssues) {
            handleWorkerResult(AdvisorEndpoint, header, advisorWorkerResult)
        } else {
            handleWorkerResultWithIssues(AdvisorEndpoint, header, advisorWorkerResult)
        }
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
        if (!scannerWorkerResult.hasIssues) {
            handleWorkerResult(ScannerEndpoint, header, scannerWorkerResult)
        } else {
            handleWorkerResultWithIssues(ScannerEndpoint, header, scannerWorkerResult)
        }
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
        if (!evaluatorWorkerResult.hasIssues) {
            handleWorkerResult(EvaluatorEndpoint, header, evaluatorWorkerResult)
        } else {
            handleWorkerResultWithIssues(EvaluatorEndpoint, header, evaluatorWorkerResult)
        }
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
        if (!reporterWorkerResult.hasIssues) {
            handleWorkerResult(ReporterEndpoint, header, reporterWorkerResult)
        } else {
            handleWorkerResultWithIssues(ReporterEndpoint, header, reporterWorkerResult)
        }
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
     * Handle messages of the type [WorkerError] with the given [header].
     */
    fun handleWorkerError(header: MessageHeader, workerError: WorkerError) {
        val ortRunId = header.ortRunId
        log.info("Handling a worker error of type '{}' for ORT run {}.", workerError.endpointName, ortRunId)

        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            workerJobRepositories[workerError.endpointName]?.let { repository ->
                val job = requireNotNull(repository.getForOrtRun(ortRunId)) {
                    "ORT run '$ortRunId' not found."
                }

                repository.tryComplete(job.id, Clock.System.now(), JobStatus.FAILED)?.let {
                    nextJobsToSchedule(Endpoint.fromConfigPrefix(workerError.endpointName), job.ortRunId, header)
                }
            } ?: (createWorkerScheduleContext(getCurrentOrtRun(ortRunId), header, failed = true) to emptyList())
        }.scheduleNextJobs {
            log.warn("Failed to handle 'WorkerError' message.", it)
        }
    }

    /**
     * Handle messages of the type [LostSchedule] with the given [header]. Determine the current status of worker jobs
     * for the affected ORT run and schedule the next jobs if possible.
     */
    fun handleLostSchedule(header: MessageHeader, lostSchedule: LostSchedule) {
        log.info("Handling a lost schedule for ORT run {}.", lostSchedule.ortRunId)

        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val ortRun = getCurrentOrtRun(lostSchedule.ortRunId)
            val context = createWorkerScheduleContext(ortRun, header)

            if (context.jobs.isNotEmpty()) {
                fetchNextJobs(context)
            } else {
                context to listOf { scheduleConfigWorkerJob(ortRun, header, updateRun = false) }
            }
        }.scheduleNextJobs {
            log.warn("Failed to handle 'LostSchedule' message.", it)
        }
    }

    /**
     * Obtain the [OrtRun] with the given [ortRunId] of fail with an exception if it does not exist.
     */
    private fun getCurrentOrtRun(ortRunId: Long): OrtRun =
        requireNotNull(ortRunRepository.get(ortRunId)) {
            "ORT run '$ortRunId' not found."
        }

    /**
     * Handle the given [result] message with the given [header] from a worker of the given [endpoint].
     */
    private fun handleWorkerResult(endpoint: Endpoint<*>, header: MessageHeader, result: WorkerMessage) {
        handleCompletedJob(endpoint, header, result, JobStatus.FINISHED)
    }

    /**
     * Handle the given [result] message with the given [header] from a worker of the given [endpoint]. The run finished
     * on the worker, but with issues over the threshold.
     */
    private fun handleWorkerResultWithIssues(endpoint: Endpoint<*>, header: MessageHeader, result: WorkerMessage) {
        handleCompletedJob(endpoint, header, result, JobStatus.FINISHED_WITH_ISSUES)
    }

    /**
     * Handle the given [error] message with the given [header] from a worker of the given [endpoint].
     */
    private fun handleWorkerError(endpoint: Endpoint<*>, header: MessageHeader, error: WorkerMessage) {
        handleCompletedJob(endpoint, header, error, JobStatus.FAILED, listOf(endpoint.createErrorIssue()))
    }

    /**
     * Handle a [message] with the given [header] about a worker job for the given [endpoint] that completed in the
     * given [status] with optional [issues]. This is the central scheduling function. It determines the current job
     * execution state of the affected ORT run, schedules the next job(s) if possible, or decides that the ORT run is
     * now finished.
     */
    private fun handleCompletedJob(
        endpoint: Endpoint<*>,
        header: MessageHeader,
        message: WorkerMessage,
        status: JobStatus,
        issues: List<Issue> = emptyList()
    ) {
        log.info(
            "Job {} for endpoint '{}' completed in status '{}'.",
            message.jobId,
            endpoint.configPrefix,
            status.name
        )

        db.blockingQueryCatching(transactionIsolation = isolationLevel) {
            val job = workerJobRepositories.updateJobStatus(endpoint, message.jobId, status)
            if (issues.isNotEmpty()) ortRunRepository.update(job.ortRunId, issues = issues.asPresent())

            nextJobsToSchedule(endpoint, job.ortRunId, header)
        }.scheduleNextJobs {
            log.warn("Failed to handle '{}' message.", message::class.java.simpleName, it)
        }
    }

    /**
     * Determine the next jobs that can be scheduled after a job for the given [endpoint] for the run with the given
     * [ortRunId] has completed. Use the given [header] to send messages to the worker endpoints. Optionally,
     * accept a map with the [jobs] that have been run. Return a list with the new jobs to schedule and the current
     * [WorkerScheduleContext].
     */
    private fun nextJobsToSchedule(
        endpoint: Endpoint<*>,
        ortRunId: Long,
        header: MessageHeader,
        jobs: Map<String, WorkerJob>? = null
    ): Pair<WorkerScheduleContext, List<JobScheduleFunc>> {
        log.info("Handling a completed job for endpoint '{}' and ORT run {}.", endpoint.configPrefix, ortRunId)

        val ortRun = getCurrentOrtRun(ortRunId)
        val scheduleContext = createWorkerScheduleContext(ortRun, header, workerJobs = jobs)

        return fetchNextJobs(scheduleContext)
    }

    /**
     * Convenience function to evaluate and process this [Result] with information about the next jobs to be scheduled.
     * If the result is successful, actually trigger the jobs. Otherwise, call the given [onFailure] function with the
     * exception that occurred.
     */
    private fun Result<Pair<WorkerScheduleContext, List<JobScheduleFunc>>>.scheduleNextJobs(
        onFailure: (Throwable) -> Unit
    ) {
        onSuccess { (context, schedules) ->
            scheduleCreatedJobs(context, schedules)
        }
        this@scheduleNextJobs.onFailure { onFailure(it) }
    }

    /**
     * Create a [WorkerScheduleContext] for the given [ortRun] and message [header] with the given [failed] flag.
     * The context is initialized with the status of all jobs for this run, either from the given [workerJobs]
     * parameter or by loading the job status from the database.
     */
    private fun createWorkerScheduleContext(
        ortRun: OrtRun,
        header: MessageHeader,
        failed: Boolean = false,
        workerJobs: Map<String, WorkerJob>? = null
    ): WorkerScheduleContext {
        val jobs = workerJobs ?: workerJobRepositories.jobRepositories.mapNotNull { (endpoint, repository) ->
            repository.getForOrtRun(ortRun.id)?.let { endpoint to it }
        }.toMap()

        return WorkerScheduleContext(ortRun, workerJobRepositories, publisher, header, jobs, failed)
    }

    /**
     * Trigger the scheduling of the given new [createdJobs] for the ORT run contained in the given [context]. This
     * also includes sending corresponding messages to the worker endpoints.
     */
    private fun scheduleCreatedJobs(context: WorkerScheduleContext, createdJobs: CreatedJobs) {
        // TODO: Handle errors during job scheduling.

        createdJobs.forEach { it() }

        if (createdJobs.isEmpty() && !context.hasRunningJobs()) {
            cleanupJobs(context.ortRun.id)

            val ortRunStatus = when {
                context.isFailed() -> OrtRunStatus.FAILED
                context.isFinishedWithIssues() -> OrtRunStatus.FINISHED_WITH_ISSUES
                else -> OrtRunStatus.FINISHED
            }

            log.info("Setting the final status of ORT run {} to '{}'.", context.ortRun.id, ortRunStatus.name)

            ortRunRepository.update(context.ortRun.id, ortRunStatus.asPresent())
        }
    }

    /**
     * Publish a message with the given [header] to the [ConfigEndpoint] to trigger the Config worker job for the given
     * [run]. If [updateRun] is *true*, set the status of the run to ACTIVE.
     */
    private fun scheduleConfigWorkerJob(run: OrtRun, header: MessageHeader, updateRun: Boolean) {
        publish(ConfigEndpoint, run, header, ConfigRequest(run.id))

        if (updateRun) {
            ortRunRepository.update(run.id, OrtRunStatus.ACTIVE.asPresent())
        }
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
}

/**
 * Type definition to represent a list of jobs that have been created and must be scheduled.
 */
typealias CreatedJobs = List<JobScheduleFunc>

/**
 * Create an [Issue] object representing an error that occurred in any [Endpoint].
 */
fun <T : Any> Endpoint<T>.createErrorIssue(): Issue = Issue(
    timestamp = Clock.System.now(),
    source = configPrefix,
    message = "The $configPrefix worker failed due to an unexpected error.",
    severity = Severity.ERROR
)

/**
 * Return a [Pair] with the given [scheduleContext] and the list of jobs that can be scheduled in the current phase
 * of the affected ORT run.
 */
private fun fetchNextJobs(
    scheduleContext: WorkerScheduleContext
): Pair<WorkerScheduleContext, List<JobScheduleFunc>> =
    scheduleContext to WorkerScheduleInfo.entries.mapNotNull { it.createAndScheduleJobIfPossible(scheduleContext) }
