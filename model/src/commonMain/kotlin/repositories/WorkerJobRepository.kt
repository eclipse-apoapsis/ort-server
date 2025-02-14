/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.repositories

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.asPresent

/**
 * A common interface for repositories that manage worker jobs.
 *
 * This interface defines a number of methods that are needed for all concrete jobs. It allows dealing with jobs in a
 * generic way.
 */
interface WorkerJobRepository<T : WorkerJob> {
    /**
     * Get a job by [id]. Returns null if the job is not found.
     */
    fun get(id: Long): T?

    /**
     * Get the job for an [ORT run][ortRunId].
     */
    fun getForOrtRun(ortRunId: Long): T?

    /**
     * Update a job by [id] with the [present][OptionalValue.Present] values.
     */
    fun update(
        id: Long,
        startedAt: OptionalValue<Instant?> = OptionalValue.Absent,
        finishedAt: OptionalValue<Instant?> = OptionalValue.Absent,
        status: OptionalValue<JobStatus> = OptionalValue.Absent
    ): T

    /**
     * Mark a job by [id] as started by updating the given [startedAt] timestamp and setting the status to
     * [JobStatus.RUNNING].
     */
    fun start(id: Long, startedAt: Instant): T =
        update(id, startedAt = startedAt.asPresent(), status = JobStatus.RUNNING.asPresent())

    /**
     * Mark a job by [id] as started if it is not yet in a started state. Return the updated job if the update is
     * possible and `null` otherwise.
     */
    fun tryStart(id: Long, startedAt: Instant): T? =
        if (getStatus(id) in notStartedJobStates) start(id, startedAt) else null

    /**
     * Mark a job by [id] as completed by updating the given [finishedAt] date and setting the given [status].
     */
    fun complete(id: Long, finishedAt: Instant, status: JobStatus): T {
        require(status in completedJobStates) {
            "complete can only be called with a JobStatus that mark the job as completed: $completedJobStates."
        }

        return update(
            id,
            finishedAt = finishedAt.asPresent(),
            status = status.asPresent()
        )
    }

    /**
     * Mark a job by [id] as completed if it is not yet in a completed state. This function works similar to
     * [complete], but first checks the [JobStatus] of the affected job. It can be used to complete jobs in a safe
     * way, in case multiple completion messages are received. If the update is possible, return the updated entity;
     * otherwise, return *null*.
     */
    fun tryComplete(id: Long, finishedAt: Instant, status: JobStatus): T? =
        if (getStatus(id) !in completedJobStates) complete(id, finishedAt, status) else null

    /**
     * Return a list with all jobs managed by this repository that have not yet been finished. Optionally, a
     * [date][before] can be specified; then only jobs created before this timestamp are returned. This can be used
     * for instance to find jobs that are running for a longer time. If unspecified, all active jobs are returned.
     * A job is considered active if it does not have a finished timestamp. Note that the [JobStatus] is not taken
     * into account here, since the finished timestamp is always set together with a completed status.
     */
    fun listActive(before: Instant? = null): List<T>

    /** Return the status of a job by [id] or throw an [IllegalArgumentException] if the job is not found. */
    private fun getStatus(id: Long): JobStatus =
        requireNotNull(get(id)?.status) { "${this::class.simpleName}: Job '$id' not found." }
}

/**
 * A set with the [JobStatus] values that indicate a completed job.
 */
private val completedJobStates = setOf(JobStatus.FINISHED, JobStatus.FINISHED_WITH_ISSUES, JobStatus.FAILED)

/**
 * A set with the [JobStatus] values that indicate a job that was not started.
 */
private val notStartedJobStates = setOf(JobStatus.CREATED, JobStatus.SCHEDULED)
