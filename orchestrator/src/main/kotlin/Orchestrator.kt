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
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
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
 *
 * Each message is processed in two phases: In a serializable transaction, the state of the affected ORT run is
 * updated, the next jobs are determined via its [RunState] and created in the database, resulting in a
 * [SchedulingPlan]. After the transaction has been committed, the plan is executed by sending messages to the workers.
 */
class Orchestrator internal constructor(
    /** The database in which the state of ORT runs is updated. */
    private val db: Database,

    /** The object to access the state of ORT runs and their jobs. */
    private val runStore: RunStore,

    /** The object to send messages to the workers. */
    private val jobDispatcher: JobDispatcher
) {
    private val isolationLevel = Connection.TRANSACTION_SERIALIZABLE

    /** Handle the given [message] by passing it to the handler function for the type of its payload. */
    suspend fun handleMessage(message: Message<OrchestratorMessage>) {
        val header = message.header

        when (val payload = message.payload) {
            is CreateOrtRun -> handleCreateOrtRun(header, payload)
            is ConfigWorkerResult -> handleConfigWorkerResult(header, payload)
            is ConfigWorkerError -> handleConfigWorkerError(payload)
            is AnalyzerWorkerResult -> handleJobResult(AnalyzerEndpoint, header, payload, payload.hasIssues)
            is AnalyzerWorkerError -> handleJobError(AnalyzerEndpoint, header, payload)
            is AdvisorWorkerResult -> handleJobResult(AdvisorEndpoint, header, payload, payload.hasIssues)
            is AdvisorWorkerError -> handleJobError(AdvisorEndpoint, header, payload)
            is ScannerWorkerResult -> handleJobResult(ScannerEndpoint, header, payload, payload.hasIssues)
            is ScannerWorkerError -> handleJobError(ScannerEndpoint, header, payload)
            is EvaluatorWorkerResult -> handleJobResult(EvaluatorEndpoint, header, payload, payload.hasIssues)
            is EvaluatorWorkerError -> handleJobError(EvaluatorEndpoint, header, payload)
            is ReporterWorkerResult -> handleJobResult(ReporterEndpoint, header, payload, payload.hasIssues)
            is ReporterWorkerError -> handleJobError(ReporterEndpoint, header, payload)
            is NotifierWorkerResult -> handleJobResult(NotifierEndpoint, header, payload)
            is NotifierWorkerError -> handleJobError(NotifierEndpoint, header, payload)
            is WorkerError -> handleWorkerError(header, payload)
            is LostSchedule -> handleLostSchedule(header, payload)
        }
    }

    /** Handle messages of the type [CreateOrtRun]. */
    suspend fun handleCreateOrtRun(header: MessageHeader, createOrtRun: CreateOrtRun) {
        processMessage(createOrtRun) {
            val ortRun = createOrtRun.ortRun

            runStore.requireRepository(ortRun.repositoryId)
            runStore.setRunStatus(ortRun.id, OrtRunStatus.ACTIVE)

            SchedulingPlan(ortRun, header, dispatchConfigJob = true)
        }
    }

    /** Handle messages of the type [ConfigWorkerResult]. */
    suspend fun handleConfigWorkerResult(header: MessageHeader, configWorkerResult: ConfigWorkerResult) {
        processMessage(configWorkerResult) {
            planNextJobs(runStore.getOrtRun(configWorkerResult.ortRunId), header)
        }
    }

    /** Handle messages of the type [ConfigWorkerError]. */
    suspend fun handleConfigWorkerError(configWorkerError: ConfigWorkerError) {
        processMessage(configWorkerError) {
            runStore.failRun(configWorkerError.ortRunId, ConfigEndpoint.createErrorIssue(configWorkerError))
            null
        }
    }

    /**
     * Handle the given [result] message with the given [header] from a worker of the given [endpoint]. If
     * [hasIssues] is *true*, the job finished, but with issues over the threshold.
     */
    suspend fun handleJobResult(
        endpoint: Endpoint<*>,
        header: MessageHeader,
        result: WorkerMessage,
        hasIssues: Boolean = false
    ) {
        val status = if (hasIssues) JobStatus.FINISHED_WITH_ISSUES else JobStatus.FINISHED
        handleCompletedJob(endpoint, header, result, status)
    }

    /** Handle the given [error] message with the given [header] from a worker of the given [endpoint]. */
    suspend fun handleJobError(endpoint: Endpoint<*>, header: MessageHeader, error: WorkerMessage) {
        handleCompletedJob(endpoint, header, error, JobStatus.FAILED, listOf(endpoint.createErrorIssue(error)))
    }

    /** Handle messages of the type [WorkerError] with the given [header]. */
    suspend fun handleWorkerError(header: MessageHeader, workerError: WorkerError) {
        val ortRunId = header.ortRunId
        log.info("Handling a worker error of type '{}' for ORT run {}.", workerError.endpointName, ortRunId)

        processMessage(workerError) {
            val worker = WorkerScheduleInfo.forEndpointName(workerError.endpointName)

            when {
                // Only the Config worker does not have jobs.
                worker == null -> planNextJobs(runStore.getOrtRun(ortRunId), header, configWorkerFailed = true)

                runStore.tryFailJob(worker, ortRunId) -> planNextJobs(runStore.getOrtRun(ortRunId), header)

                // If the job was already completed, the error is outdated and must not change the state of the run.
                else -> null
            }
        }
    }

    /**
     * Handle messages of the type [LostSchedule] with the given [header]. Determine the current status of worker jobs
     * for the affected ORT run and schedule the next jobs if possible.
     */
    suspend fun handleLostSchedule(header: MessageHeader, lostSchedule: LostSchedule) {
        log.info("Handling a lost schedule for ORT run {}.", lostSchedule.ortRunId)

        processMessage(lostSchedule) {
            val ortRun = runStore.getOrtRun(lostSchedule.ortRunId)
            val runState = runStore.loadRunState(ortRun)

            if (runState.hasJobs()) {
                planNextJobs(ortRun, header, runState)
            } else {
                SchedulingPlan(ortRun, header, dispatchConfigJob = true)
            }
        }
    }

    /**
     * Handle a [message] with the given [header] about a worker job for the given [endpoint] that completed in the
     * given [status] with optional [issues]. Update the job and plan the next jobs of the affected ORT run.
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

        processMessage(message) {
            val worker = requireNotNull(WorkerScheduleInfo.forEndpointName(endpoint.configPrefix)) {
                "No jobs are created for endpoint '${endpoint.configPrefix}'."
            }

            val job = runStore.completeJob(worker, message.jobId, status, extractErrorMessage(message))
            if (issues.isNotEmpty()) runStore.addIssues(job.ortRunId, issues)

            planNextJobs(runStore.getOrtRun(job.ortRunId), header)
        }
    }

    /**
     * Process the given [message] by running [updateState] in a transaction. If it returns a [SchedulingPlan],
     * execute it after the transaction was committed. Log failures.
     */
    private suspend fun processMessage(message: Any, updateState: suspend () -> SchedulingPlan?) {
        db.transactionCatching(transactionIsolation = isolationLevel) {
            updateState()
        }.onSuccess { plan ->
            plan?.also { executePlan(it) }
        }.onFailure {
            log.warn("Failed to handle '{}' message.", message::class.simpleName, it)
        }
    }

    /**
     * Determine the next jobs for the given [ortRun] based on its [runState] and create them in the database. Return
     * a [SchedulingPlan] to dispatch the jobs using the given [header]. If no jobs can be scheduled anymore, set the
     * final status of the run instead. The [configWorkerFailed] flag is only used if the run state must be loaded.
     */
    private suspend fun planNextJobs(
        ortRun: OrtRun,
        header: MessageHeader,
        runState: RunState? = null,
        configWorkerFailed: Boolean = false
    ): SchedulingPlan {
        val state = runState ?: runStore.loadRunState(ortRun, configWorkerFailed)

        state.finalStatus()?.also { ortRunStatus ->
            log.info("Setting the final status of ORT run {} to '{}'.", ortRun.id, ortRunStatus.name)

            runStore.finishRun(ortRun.id, ortRunStatus)
        }

        val jobs = state.nextJobs().mapNotNull { worker ->
            runStore.createJob(ortRun, worker)?.let { worker to it.id }
        }.toMap()

        return SchedulingPlan(ortRun, header, jobs)
    }

    /** Execute the given [plan] by sending messages to the workers and marking the dispatched jobs as scheduled. */
    private suspend fun executePlan(plan: SchedulingPlan) {
        // TODO: Handle errors during job scheduling.

        if (plan.dispatchConfigJob) jobDispatcher.dispatchConfigJob(plan.ortRun, plan.header)

        plan.jobs.forEach { (worker, jobId) ->
            jobDispatcher.dispatchJob(worker, jobId, plan.ortRun, plan.header)
            runStore.markJobScheduled(worker, jobId)
        }
    }
}

/** Create an [Issue] representing the [error] that occurred in an [Endpoint]. */
fun <T : Any> Endpoint<T>.createErrorIssue(error: Any): Issue = Issue(
    timestamp = Clock.System.now(),
    source = configPrefix,
    message = extractErrorMessage(error) ?: "The $configPrefix worker failed due to an unexpected error.",
    severity = Severity.ERROR
)

private fun extractErrorMessage(message: Any): String? =
    (message as? WorkerErrorMessage)?.errorMessage
