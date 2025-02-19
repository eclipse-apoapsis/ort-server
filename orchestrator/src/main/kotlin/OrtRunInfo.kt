/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.JobStatus

/** A class to store the required information to determine which jobs can be run. */
internal class OrtRunInfo(
    /** The ORT run ID. */
    val id: Long,

    /** Whether the config worker has failed. */
    val configWorkerFailed: Boolean,

    /** The jobs configured to run in this ORT run. */
    val configuredJobs: Set<WorkerScheduleInfo>,

    /** Status information for already created jobs. */
    val jobInfos: Map<WorkerScheduleInfo, WorkerJobInfo>
) {
    /** Get the next jobs that can be run. */
    fun getNextJobs(): Set<WorkerScheduleInfo> = WorkerScheduleInfo.entries.filterTo(mutableSetOf()) { canRun(it) }

    /** Return true if the job can be run. */
    private fun canRun(info: WorkerScheduleInfo): Boolean =
        isConfigured(info) &&
                !wasScheduled(info) &&
                canRunIfPreviousJobFailed(info) &&
                info.dependsOn.all { isCompleted(it) } &&
                info.runsAfterTransitively.none { isPending(it) }

    /** Return true if no previous job has failed or if the job is configured to run after a failure. */
    private fun canRunIfPreviousJobFailed(info: WorkerScheduleInfo): Boolean = info.runAfterFailure || !isFailed()

    /** Return true if the job has been completed. */
    private fun isCompleted(info: WorkerScheduleInfo): Boolean = jobInfos[info]?.status?.final == true

    /** Return true if the job is configured to run. */
    private fun isConfigured(info: WorkerScheduleInfo): Boolean = info in configuredJobs

    /** Return true if any job has failed. */
    private fun isFailed(): Boolean = configWorkerFailed || jobInfos.any { it.value.status == JobStatus.FAILED }

    /** Return true if the job is pending execution. */
    private fun isPending(info: WorkerScheduleInfo): Boolean =
        isConfigured(info) &&
                !isCompleted(info) &&
                canRunIfPreviousJobFailed(info) &&
                info.dependsOn.all { wasScheduled(it) || isPending(it) }

    /** Return true if the job has been scheduled. */
    private fun wasScheduled(info: WorkerScheduleInfo): Boolean = jobInfos.containsKey(info)
}

/** A class to store information of a worker job required by [OrtRunInfo]. */
internal class WorkerJobInfo(
    /** The job ID. */
    val id: Long,

    /** The job status. */
    val status: JobStatus
)
