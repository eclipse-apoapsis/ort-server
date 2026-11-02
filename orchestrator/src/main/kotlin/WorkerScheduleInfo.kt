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
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

/** Type definition for a function that schedules another worker job. */
typealias JobScheduleFunc = suspend () -> Unit

/**
 * An enumeration class with constants that describe if and when a job for a specific worker should be scheduled.
 *
 * This class enables a declarative approach to scheduling worker jobs. Instead of defining conditional logic, a
 * constant defines on which other jobs the represented worker depends. Therefore, the scheduling logic can determine
 * in a generic way when all conditions are met to schedule the job.
 */
internal enum class WorkerScheduleInfo(
    /** The endpoint of the worker represented by this schedule info. */
    val endpoint: Endpoint<*>,

    /**
     * A list defining the worker jobs that this job depends on. This job will only be executed after all the
     * dependencies have been successfully completed.
     */
    val dependsOn: List<WorkerScheduleInfo> = emptyList(),

    /**
     * A list defining the worker jobs that must run before this job. The difference to [dependsOn] is that this job
     * can also run if these other jobs will not be executed. It is only guaranteed that it runs after all of them.
     */
    val runsAfter: List<WorkerScheduleInfo> = emptyList(),

    /** A flag determining whether the represented worker should be run even if previous workers have already failed. */
    val runAfterFailure: Boolean = false
) {
    ANALYZER(AnalyzerEndpoint) {
        override fun isConfigured(configs: JobConfigurations): Boolean = true
    },

    ADVISOR(AdvisorEndpoint, dependsOn = listOf(ANALYZER)) {
        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.advisor != null
    },

    SCANNER(ScannerEndpoint, dependsOn = listOf(ANALYZER)) {
        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.scanner != null
    },

    EVALUATOR(EvaluatorEndpoint, runsAfter = listOf(ADVISOR, SCANNER)) {
        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.evaluator != null
    },

    REPORTER(ReporterEndpoint, runsAfter = listOf(EVALUATOR), runAfterFailure = true) {
        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.reporter != null
    },

    NOTIFIER(NotifierEndpoint, dependsOn = listOf(REPORTER), runAfterFailure = true) {
        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.notifier != null
    };

    companion object {
        /** Return the [WorkerScheduleInfo] for the worker with the given [endpointName] or *null* if there is none. */
        fun forEndpointName(endpointName: String): WorkerScheduleInfo? =
            entries.find { it.endpoint.configPrefix == endpointName }
    }

    /**
     * Return the transitive set of the workers that must complete before this one can run. This is necessary to
     * determine whether this worker can be started in the current phase of an ORT run. Note that it is assumed that
     * no cycles exist in the dependency graph of workers; otherwise, the scheduler algorithm would have a severe
     * problem.
     */
    val runsAfterTransitively: Set<WorkerScheduleInfo>
        get() = (runsAfter + dependsOn).flatMapTo(mutableSetOf()) { it.runsAfterTransitively + it }

    /**
     * Create the job for the represented worker in the database based on the given [context] and return a function
     * that schedules the job.
     */
    suspend fun createAndScheduleJob(context: WorkerScheduleContext): JobScheduleFunc? =
        context.runStore.createJob(context.ortRun, this)?.let { job ->
            {
                context.jobDispatcher.dispatchJob(this, job.id, context.ortRun, context.header)
                context.runStore.markJobScheduled(this, job.id)
            }
        }

    /** Return a flag whether this worker is configured to run for the current ORT run based on the given [configs]. */
    abstract fun isConfigured(configs: JobConfigurations): Boolean
}
