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

import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.selectByPrefix

/**
 * A class holding context information required when scheduling new worker jobs for an [OrtRun]. This includes the
 * current job execution status of all jobs created for this run. In addition, references to helper objects are
 * available that are needed to create and schedule new jobs.
 */
internal class WorkerScheduleContext(
    /** The object representing the current run. */
    val ortRun: OrtRun,

    /** The object holding the repositories for the worker jobs. */
    val workerJobRepositories: WorkerJobRepositories,

    /** The object for sending messages to the workers to trigger new jobs. */
    val publisher: MessagePublisher,

    /**
     * The header of the message that is currently processed. The header for messages sent to worker endpoints is
     * derived from this header.
      */
    val header: MessageHeader,

    /**
     * A map storing the jobs for the current run that have already been created. Keys are the endpoint names for jobs.
     */
    val jobs: Map<String, WorkerJob>,

    /**
     * A flag indicating that the current [OrtRun] has failed. Per default, the failure state is determined by the
     * jobs that have been run. With this flag, this mechanism can be overridden, which is necessary for workers that
     * do not spawn jobs like the Config worker.
     */
    val failed: Boolean = false
) {
    /**
     * Return the [JobConfigurations] object for the current run. Prefer the resolved configurations if available;
     * otherwise, return the original configurations.
     */
    fun jobConfigs(): JobConfigurations = ortRun.resolvedJobConfigs ?: ortRun.jobConfigs

    /**
     * Create a message based on the given [payload] and publish it to the given [endpoint].
     * Make sure that the header contains the correct transport properties. These are obtained from the labels of the
     * current ORT run. Also make sure that a trace ID is available.
     */
    fun <T : Any> publish(endpoint: Endpoint<T>, payload: T) {
        val traceId = header.traceId.takeUnless { it.isEmpty() } ?: ortRun.traceId.orEmpty()
        val headerWithProperties = header.copy(
            traceId = traceId,
            transportProperties = ortRun.labels.selectByPrefix("transport")
        )

        publisher.publish(to = endpoint, message = Message(headerWithProperties, payload))
    }

    /**
     * Return a flag whether the current [OrtRun] has at least one running job.
     */
    fun hasRunningJobs(): Boolean =
        jobs.values.any { !it.isCompleted() }

    /**
     * Return a flag whether this [OrtRun] has failed, i.e. it has at least one job in failed state.
     */
    fun isFailed(): Boolean =
        failed || jobs.values.any { it.status == JobStatus.FAILED }

    /**
    * Return a flag whether this [OrtRun] has finished with issues, i.e. it has at least one job with this state.
    */
    fun isFinishedWithIssues(): Boolean =
        !isFailed() && jobs.values.any { it.status == JobStatus.FINISHED_WITH_ISSUES }
}

/**
 * Return a flag whether this [WorkerJob] is already completed.
 */
private fun WorkerJob.isCompleted(): Boolean =
    status == JobStatus.FINISHED || status == JobStatus.FAILED || status == JobStatus.FINISHED_WITH_ISSUES
