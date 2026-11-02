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

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.transactionCatching
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.LostSchedule
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerErrorMessage
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerMessage
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.runs.Issue
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

import org.jetbrains.exposed.v1.jdbc.Database

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(Orchestrator::class.java)

/**
 * The Orchestrator is the central component that breaks an ORT run into single steps and coordinates their execution.
 * It creates jobs for the single processing steps and passes them to the corresponding workers. It collects the results
 * produced by the workers until the complete ORT result is available or the run has failed.
 */
@Suppress("TooManyFunctions")
class Orchestrator(
    private val db: Database,
    workerJobRepositories: WorkerJobRepositories,
    repositoryRepository: RepositoryRepository,
    ortRunRepository: OrtRunRepository,
    publisher: MessagePublisher
) {
    private val isolationLevel = Connection.TRANSACTION_SERIALIZABLE

    private val runStore = RunStore(workerJobRepositories, repositoryRepository, ortRunRepository)

    private val jobDispatcher = JobDispatcher(publisher)

    /** Handle the given [message] by passing it to the handler function for the type of its payload. */
    suspend fun handleMessage(message: Message<OrchestratorMessage>) {
        val header = message.header

        when (val payload = message.payload) {
            is CreateOrtRun -> handleCreateOrtRun(header, payload)
            is ConfigWorkerResult -> handleConfigWorkerResult(header, payload)
            is ConfigWorkerError -> handleConfigWorkerError(payload)
            is AnalyzerWorkerResult -> handleAnalyzerWorkerResult(header, payload)
            is AnalyzerWorkerError -> handleAnalyzerWorkerError(header, payload)
            is AdvisorWorkerResult -> handleAdvisorWorkerResult(header, payload)
            is AdvisorWorkerError -> handleAdvisorWorkerError(header, payload)
            is ScannerWorkerResult -> handleScannerWorkerResult(header, payload)
            is ScannerWorkerError -> handleScannerWorkerError(header, payload)
            is EvaluatorWorkerResult -> handleEvaluatorWorkerResult(header, payload)
            is EvaluatorWorkerError -> handleEvaluatorWorkerError(header, payload)
            is ReporterWorkerResult -> handleReporterWorkerResult(header, payload)
            is ReporterWorkerError -> handleReporterWorkerError(header, payload)
            is NotifierWorkerResult -> handleNotifierWorkerResult(header, payload)
            is NotifierWorkerError -> handleNotifierWorkerError(header, payload)
            is WorkerError -> handleWorkerError(header, payload)
            is LostSchedule -> handleLostSchedule(header, payload)
        }
    }

    /** Handle messages of the type [CreateOrtRun]. */
    suspend fun handleCreateOrtRun(header: MessageHeader, createOrtRun: CreateOrtRun) {
        db.transactionCatching(transactionIsolation = isolationLevel) {
            val ortRun = createOrtRun.ortRun

            runStore.requireRepository(ortRun.repositoryId)

            val context = createWorkerScheduleContext(ortRun, header, RunState(ortRun.jobConfigs, emptyMap()))
            context to listOf<JobScheduleFunc> { scheduleConfigWorkerJob(ortRun, header, updateRun = true) }
        }.scheduleNextJobs {
            log.warn("Failed to handle 'CreateOrtRun' message.", it)
        }
    }

    /** Handle messages of the type [ConfigWorkerResult]. */
    suspend fun handleConfigWorkerResult(header: MessageHeader, configWorkerResult: ConfigWorkerResult) {
        db.transactionCatching(transactionIsolation = isolationLevel) {
            val ortRun = runStore.getOrtRun(configWorkerResult.ortRunId)

            nextJobsToSchedule(ConfigEndpoint, ortRun.id, header)
        }.scheduleNextJobs {
            log.warn("Failed to handle 'ConfigWorkerResult' message.", it)
        }
    }

    /** Handle messages of the type [ConfigWorkerError]. */
    suspend fun handleConfigWorkerError(configWorkerError: ConfigWorkerError) {
        db.transactionCatching(transactionIsolation = isolationLevel) {
            runStore.failRun(configWorkerError.ortRunId, ConfigEndpoint.createErrorIssue(configWorkerError))
        }.onFailure {
            log.warn("Failed to handle 'ConfigWorkerError' message.", it)
        }
    }

    /** Handle messages of the type [AnalyzerWorkerResult]. */
    suspend fun handleAnalyzerWorkerResult(header: MessageHeader, analyzerWorkerResult: AnalyzerWorkerResult) {
        if (!analyzerWorkerResult.hasIssues) {
            handleWorkerResult(AnalyzerEndpoint, header, analyzerWorkerResult)
        } else {
            handleWorkerResultWithIssues(AnalyzerEndpoint, header, analyzerWorkerResult)
        }
    }

    /** Handle messages of the type [AnalyzerWorkerError]. */
    suspend fun handleAnalyzerWorkerError(header: MessageHeader, analyzerWorkerError: AnalyzerWorkerError) {
        handleWorkerError(AnalyzerEndpoint, header, analyzerWorkerError)
    }

    /** Handle messages of the type [AdvisorWorkerResult]. */
    suspend fun handleAdvisorWorkerResult(header: MessageHeader, advisorWorkerResult: AdvisorWorkerResult) {
        if (!advisorWorkerResult.hasIssues) {
            handleWorkerResult(AdvisorEndpoint, header, advisorWorkerResult)
        } else {
            handleWorkerResultWithIssues(AdvisorEndpoint, header, advisorWorkerResult)
        }
    }

    /** Handle messages of the type [AnalyzerWorkerError]. */
    suspend fun handleAdvisorWorkerError(header: MessageHeader, advisorWorkerError: AdvisorWorkerError) {
        handleWorkerError(AdvisorEndpoint, header, advisorWorkerError)
    }

    /** Handle messages of the type [ScannerWorkerResult]. */
    suspend fun handleScannerWorkerResult(header: MessageHeader, scannerWorkerResult: ScannerWorkerResult) {
        if (!scannerWorkerResult.hasIssues) {
            handleWorkerResult(ScannerEndpoint, header, scannerWorkerResult)
        } else {
            handleWorkerResultWithIssues(ScannerEndpoint, header, scannerWorkerResult)
        }
    }

    /** Handle messages of the type [ScannerWorkerError]. */
    suspend fun handleScannerWorkerError(header: MessageHeader, scannerWorkerError: ScannerWorkerError) {
        handleWorkerError(ScannerEndpoint, header, scannerWorkerError)
    }

    /** Handle messages of the type [EvaluatorWorkerResult]. */
    suspend fun handleEvaluatorWorkerResult(header: MessageHeader, evaluatorWorkerResult: EvaluatorWorkerResult) {
        if (!evaluatorWorkerResult.hasIssues) {
            handleWorkerResult(EvaluatorEndpoint, header, evaluatorWorkerResult)
        } else {
            handleWorkerResultWithIssues(EvaluatorEndpoint, header, evaluatorWorkerResult)
        }
    }

    /** Handle messages of the type [EvaluatorWorkerError]. */
    suspend fun handleEvaluatorWorkerError(header: MessageHeader, evaluatorWorkerError: EvaluatorWorkerError) {
        handleWorkerError(EvaluatorEndpoint, header, evaluatorWorkerError)
    }

    /** Handle messages of the type [ReporterWorkerResult]. */
    suspend fun handleReporterWorkerResult(header: MessageHeader, reporterWorkerResult: ReporterWorkerResult) {
        if (!reporterWorkerResult.hasIssues) {
            handleWorkerResult(ReporterEndpoint, header, reporterWorkerResult)
        } else {
            handleWorkerResultWithIssues(ReporterEndpoint, header, reporterWorkerResult)
        }
    }

    /** Handle messages of the type [ReporterWorkerError]. */
    suspend fun handleReporterWorkerError(header: MessageHeader, reporterWorkerError: ReporterWorkerError) {
        handleWorkerError(ReporterEndpoint, header, reporterWorkerError)
    }

    /** Handle messages of the type [NotifierWorkerResult]. */
    suspend fun handleNotifierWorkerResult(header: MessageHeader, notifierWorkerResult: NotifierWorkerResult) {
        handleWorkerResult(NotifierEndpoint, header, notifierWorkerResult)
    }

    suspend fun handleNotifierWorkerError(header: MessageHeader, notifierWorkerError: NotifierWorkerError) {
        handleWorkerError(NotifierEndpoint, header, notifierWorkerError)
    }

    /** Handle messages of the type [WorkerError] with the given [header]. */
    suspend fun handleWorkerError(header: MessageHeader, workerError: WorkerError) {
        val ortRunId = header.ortRunId
        log.info("Handling a worker error of type '{}' for ORT run {}.", workerError.endpointName, ortRunId)

        db.transactionCatching(transactionIsolation = isolationLevel) {
            val worker = WorkerScheduleInfo.forEndpointName(workerError.endpointName)

            when {
                // Only the Config worker does not have jobs.
                worker == null -> {
                    val ortRun = runStore.getOrtRun(ortRunId)
                    createWorkerScheduleContext(
                        ortRun,
                        header,
                        runStore.loadRunState(ortRun, configWorkerFailed = true)
                    ) to emptyList()
                }

                runStore.tryFailJob(worker, ortRunId) -> nextJobsToSchedule(worker.endpoint, ortRunId, header)

                // If the job was already completed, the error is outdated and must not change the state of the run.
                else -> null
            }
        }.scheduleNextJobs {
            log.warn("Failed to handle 'WorkerError' message.", it)
        }
    }

    /**
     * Handle messages of the type [LostSchedule] with the given [header]. Determine the current status of worker jobs
     * for the affected ORT run and schedule the next jobs if possible.
     */
    suspend fun handleLostSchedule(header: MessageHeader, lostSchedule: LostSchedule) {
        log.info("Handling a lost schedule for ORT run {}.", lostSchedule.ortRunId)

        db.transactionCatching(transactionIsolation = isolationLevel) {
            val ortRun = runStore.getOrtRun(lostSchedule.ortRunId)
            val context = createWorkerScheduleContext(ortRun, header, runStore.loadRunState(ortRun))

            if (context.runState.hasJobs()) {
                fetchNextJobs(context)
            } else {
                context to listOf<JobScheduleFunc> { scheduleConfigWorkerJob(ortRun, header, updateRun = false) }
            }
        }.scheduleNextJobs {
            log.warn("Failed to handle 'LostSchedule' message.", it)
        }
    }

    /** Handle the given [result] message with the given [header] from a worker of the given [endpoint]. */
    private suspend fun handleWorkerResult(endpoint: Endpoint<*>, header: MessageHeader, result: WorkerMessage) {
        handleCompletedJob(endpoint, header, result, JobStatus.FINISHED)
    }

    /**
     * Handle the given [result] message with the given [header] from a worker of the given [endpoint]. The run finished
     * on the worker, but with issues over the threshold.
     */
    private suspend fun handleWorkerResultWithIssues(
        endpoint: Endpoint<*>,
        header: MessageHeader,
        result: WorkerMessage
    ) {
        handleCompletedJob(endpoint, header, result, JobStatus.FINISHED_WITH_ISSUES)
    }

    /** Handle the given [error] message with the given [header] from a worker of the given [endpoint]. */
    private suspend fun handleWorkerError(endpoint: Endpoint<*>, header: MessageHeader, error: WorkerMessage) {
        handleCompletedJob(endpoint, header, error, JobStatus.FAILED, listOf(endpoint.createErrorIssue(error)))
    }

    /**
     * Handle a [message] with the given [header] about a worker job for the given [endpoint] that completed in the
     * given [status] with optional [issues]. This is the central scheduling function. It determines the current job
     * execution state of the affected ORT run, schedules the next job(s) if possible, or decides that the ORT run is
     * now finished.
     */
    private suspend fun handleCompletedJob(
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

        db.transactionCatching(transactionIsolation = isolationLevel) {
            val worker = requireNotNull(WorkerScheduleInfo.forEndpointName(endpoint.configPrefix)) {
                "No jobs are created for endpoint '${endpoint.configPrefix}'."
            }

            val job = runStore.completeJob(worker, message.jobId, status, extractErrorMessage(message))
            if (issues.isNotEmpty()) runStore.addIssues(job.ortRunId, issues)

            nextJobsToSchedule(endpoint, job.ortRunId, header)
        }.scheduleNextJobs {
            log.warn("Failed to handle '{}' message.", message::class.java.simpleName, it)
        }
    }

    /**
     * Determine the next jobs that can be scheduled after a job for the given [endpoint] for the run with the given
     * [ortRunId] has completed. Use the given [header] to send messages to the worker endpoints. Return a list with
     * the new jobs to schedule and the current [WorkerScheduleContext].
     */
    private suspend fun nextJobsToSchedule(
        endpoint: Endpoint<*>,
        ortRunId: Long,
        header: MessageHeader
    ): Pair<WorkerScheduleContext, List<JobScheduleFunc>> {
        log.info("Handling a completed job for endpoint '{}' and ORT run {}.", endpoint.configPrefix, ortRunId)

        val ortRun = runStore.getOrtRun(ortRunId)
        val scheduleContext = createWorkerScheduleContext(ortRun, header, runStore.loadRunState(ortRun))

        return fetchNextJobs(scheduleContext)
    }

    /**
     * Convenience function to evaluate and process this [Result] with information about the next jobs to be scheduled.
     * If the result is successful and not *null*, actually trigger the jobs. Otherwise, call the given [onFailure]
     * function with the exception that occurred.
     */
    private suspend fun Result<Pair<WorkerScheduleContext, List<JobScheduleFunc>>?>.scheduleNextJobs(
        onFailure: (Throwable) -> Unit
    ) {
        onSuccess { result ->
            result?.let { (context, schedules) -> scheduleCreatedJobs(context, schedules) }
        }
        this@scheduleNextJobs.onFailure { onFailure(it) }
    }

    /** Create a [WorkerScheduleContext] for the given [ortRun], message [header], and [runState]. */
    private fun createWorkerScheduleContext(ortRun: OrtRun, header: MessageHeader, runState: RunState) =
        WorkerScheduleContext(ortRun, runStore, jobDispatcher, header, runState)

    /**
     * Trigger the scheduling of the given new [createdJobs] for the ORT run contained in the given [context]. This
     * also includes sending corresponding messages to the worker endpoints.
     */
    private suspend fun scheduleCreatedJobs(context: WorkerScheduleContext, createdJobs: CreatedJobs) {
        // TODO: Handle errors during job scheduling.

        createdJobs.forEach { it() }

        context.runState.finalStatus()?.also { ortRunStatus ->
            log.info("Setting the final status of ORT run {} to '{}'.", context.ortRun.id, ortRunStatus.name)

            runStore.finishRun(context.ortRun.id, ortRunStatus)
        }
    }

    /**
     * Publish a message with the given [header] to the [ConfigEndpoint] to trigger the Config worker job for the given
     * [run]. If [updateRun] is *true*, set the status of the run to ACTIVE.
     */
    private suspend fun scheduleConfigWorkerJob(run: OrtRun, header: MessageHeader, updateRun: Boolean) {
        jobDispatcher.dispatchConfigJob(run, header)

        if (updateRun) {
            runStore.setRunStatus(run.id, OrtRunStatus.ACTIVE)
        }
    }
}

/** Type definition to represent a list of jobs that have been created and must be scheduled. */
typealias CreatedJobs = List<JobScheduleFunc>

/** Create an [Issue] representing the [error] that occurred in an [Endpoint]. */
fun <T : Any> Endpoint<T>.createErrorIssue(error: Any): Issue = Issue(
    timestamp = Clock.System.now(),
    source = configPrefix,
    message = extractErrorMessage(error) ?: "The $configPrefix worker failed due to an unexpected error.",
    severity = Severity.ERROR
)

private fun extractErrorMessage(message: Any): String? =
    (message as? WorkerErrorMessage)?.errorMessage

/**
 * Return a [Pair] with the given [scheduleContext] and the list of jobs that can be scheduled in the current phase
 * of the affected ORT run.
 */
private suspend fun fetchNextJobs(
    scheduleContext: WorkerScheduleContext
): Pair<WorkerScheduleContext, List<JobScheduleFunc>> =
    scheduleContext to scheduleContext.runState.nextJobs().mapNotNull { it.createAndScheduleJob(scheduleContext) }
