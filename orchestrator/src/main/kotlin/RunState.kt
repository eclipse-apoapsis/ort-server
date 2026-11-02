/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus

/**
 * A snapshot of the job execution state of an ORT run. Based on the dependencies declared in [WorkerScheduleInfo],
 * this class decides which jobs can be scheduled next and when the run is finished. It does not depend on any
 * infrastructure, so the scheduling rules can be tested in isolation.
 */
internal class RunState(
    /** The workers that are configured to run for the ORT run. */
    private val configuredWorkers: Set<WorkerScheduleInfo>,

    /** The status of the jobs that have already been created for the ORT run. */
    private val jobStatuses: Map<WorkerScheduleInfo, JobStatus>,

    /**
     * A flag whether the Config worker has failed. The Config worker does not create a job, so its failure cannot be
     * derived from [jobStatuses].
     */
    private val configWorkerFailed: Boolean = false
) {
    /** Create a [RunState] for the given job [configs], the given [jobStatuses] and the [configWorkerFailed] flag. */
    constructor(
        configs: JobConfigurations,
        jobStatuses: Map<WorkerScheduleInfo, JobStatus>,
        configWorkerFailed: Boolean = false
    ) : this(
        configuredWorkers = WorkerScheduleInfo.entries.filterTo(mutableSetOf()) { it.isConfigured(configs) },
        jobStatuses = jobStatuses,
        configWorkerFailed = configWorkerFailed
    )

    /**
     * Return the workers for which jobs can be scheduled now. If the Config worker failed, no jobs can be scheduled at
     * all, as the job configurations could not be resolved.
     */
    fun nextJobs(): Set<WorkerScheduleInfo> =
        if (configWorkerFailed) emptySet() else WorkerScheduleInfo.entries.filterTo(mutableSetOf()) { canRun(it) }

    /**
     * Return the final status of the ORT run if it is finished, i.e., if no job is running anymore and no further job
     * can be scheduled. Otherwise, return *null*.
     */
    fun finalStatus(): OrtRunStatus? =
        when {
            hasRunningJobs() || nextJobs().isNotEmpty() -> null
            isFailed() -> OrtRunStatus.FAILED
            jobStatuses.values.any { it == JobStatus.FINISHED_WITH_ISSUES } -> OrtRunStatus.FINISHED_WITH_ISSUES
            else -> OrtRunStatus.FINISHED
        }

    /** Return a flag whether at least one job has not completed yet. */
    private fun hasRunningJobs(): Boolean = jobStatuses.values.any { !it.final }

    /** Return a flag whether the Config worker or at least one job has failed. */
    private fun isFailed(): Boolean = configWorkerFailed || jobStatuses.values.any { it == JobStatus.FAILED }

    /** Return a flag whether the given [worker] is configured to run. */
    private fun isConfigured(worker: WorkerScheduleInfo): Boolean = worker in configuredWorkers

    /** Return a flag whether a job for the given [worker] was scheduled. It may still be running or have completed. */
    private fun wasScheduled(worker: WorkerScheduleInfo): Boolean = worker in jobStatuses

    /** Return a flag whether the job for the given [worker] has completed. */
    private fun isCompleted(worker: WorkerScheduleInfo): Boolean = jobStatuses[worker]?.final == true

    /**
     * Return a flag whether a job for the given [worker] can be scheduled now. This is the case if the worker is
     * configured, has not been scheduled yet, all the workers it depends on have completed, and no worker it must run
     * after is still pending.
     */
    private fun canRun(worker: WorkerScheduleInfo): Boolean =
        isConfigured(worker) &&
                !wasScheduled(worker) &&
                canRunWithFailureState(worker) &&
                worker.dependsOn.all { isCompleted(it) } &&
                worker.runsAfterTransitively.none { isPending(it) }

    /**
     * Return a flag whether the given [worker] is pending. This means that its job has not yet completed and either
     * was already scheduled or - given the current state - is supposed to run later. A scheduled job is pending
     * independently of the failure state, as it is still running.
     */
    private fun isPending(worker: WorkerScheduleInfo): Boolean =
        isConfigured(worker) &&
                !isCompleted(worker) &&
                (wasScheduled(worker) || isExpectedToRun(worker))

    /**
     * Return a flag whether the given [worker] is supposed to run later. This is the case if it can run in the current
     * failure state and all the workers it depends on were scheduled or are pending.
     */
    private fun isExpectedToRun(worker: WorkerScheduleInfo): Boolean =
        canRunWithFailureState(worker) && worker.dependsOn.all { wasScheduled(it) || isPending(it) }

    /**
     * Return a flag whether the given [worker] can run in the current failure state. A worker can decide whether it
     * always runs or only if all previous workers were successful.
     */
    private fun canRunWithFailureState(worker: WorkerScheduleInfo): Boolean = worker.runAfterFailure || !isFailed()
}
