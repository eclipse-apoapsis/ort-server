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
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

/**
 * Type definition for a function that schedules another worker job.
 */
typealias JobScheduleFunc = () -> Unit

/**
 * An abstract base class that describes if and when a job for a specific worker should be scheduled.
 *
 * This class enables a declarative approach to scheduling worker jobs. Instead of defining conditional logic, an
 * instance defines on which other jobs the represented worker depends. Therefore, the scheduling logic can determine
 * in a generic way when all conditions are met to schedule the job.
 */
internal abstract class WorkerScheduleInfo(
    /** The endpoint of the worker represented by this schedule info. */
    private val endpoint: Endpoint<*>,

    /**
     * A list defining the worker jobs that this job depends on. This job will only be executed after all the
     * dependencies have been successfully completed.
     */
    private val dependsOn: List<Endpoint<*>> = emptyList(),

    /**
     * A list defining the worker jobs that must run before this job. The difference to [dependsOn] is that this job
     * can also run if these other jobs will not be executed. It is only guaranteed that it runs after all of them.
     */
    private val runsAfter: List<Endpoint<*>> = emptyList(),

    /**
     * A flag determining whether the represented worker should be run even if previous workers have already failed.
     */
    private val runAfterFailure: Boolean = false
) {
    /**
     * Check whether a job for the represented worker can be scheduled now based on the given [context]. If so, create
     * the job in the database and return a function that schedules the job.
     */
    fun createAndScheduleJobIfPossible(context: WorkerScheduleContext): JobScheduleFunc? {
        if (!canRun(context)) return null

        return createJob(context)?.let { job ->
            {
                publishJob(context, job)

                context.workerJobRepositories.updateJobStatus(endpoint, job.id, JobStatus.SCHEDULED, finished = false)
            }
        }
    }

    /**
     * Create a new job for this worker based on the information in the given [context].
     */
    protected abstract fun createJob(context: WorkerScheduleContext): WorkerJob?

    /**
     * Publish a message to the worker endpoint to schedule the given [job] based on the information in the given
     * [context].
     */
    protected abstract fun publishJob(context: WorkerScheduleContext, job: WorkerJob)

    /**
     * Return a flag whether this worker is configured to run for the current ORT run based on the given [configs].
     */
    protected abstract fun isConfigured(configs: JobConfigurations): Boolean

    /**
     * Return a flag whether a job for the represented worker can be started now based on the given [context].
     * This function checks whether this worker is configured to run and whether the jobs it depends on have been
     * completed.
     */
    private fun canRun(context: WorkerScheduleContext): Boolean =
        isConfigured(context.jobConfigs()) &&
                !context.wasScheduled(endpoint) &&
                canRunWithFailureState(context) &&
                dependsOn.all { context.isJobCompleted(it) } &&
                runsAfter.none { scheduleInfos.getValue(it.configPrefix).isPending(context) }

    /**
     * Check whether the represented worker is pending for the current ORT run based on the given [context]. This
     * means that the worker has not yet run, but - given the current state - is supposed to run later.
     */
    private fun isPending(context: WorkerScheduleContext): Boolean =
        isConfigured(context.jobConfigs()) &&
                !context.isJobCompleted(endpoint) &&
                canRunWithFailureState(context) &&
                dependsOn.all {
                    context.wasScheduled(it) ||
                            scheduleInfos.getValue(it.configPrefix).isPending(context)
                }

    /**
     * Check whether the represented worker can be executed for the failure state stored in the given [context]. Here
     * a worker can decide whether it can always run or only if all previous workers were successful.
     */
    private fun canRunWithFailureState(context: WorkerScheduleContext) =
        runAfterFailure || !context.isFailed()
}

private val analyzerWorkerScheduleInfo = object : WorkerScheduleInfo(AnalyzerEndpoint) {
    override fun createJob(context: WorkerScheduleContext): WorkerJob =
        context.workerJobRepositories.analyzerJobRepository.create(
            context.ortRun.id,
            context.jobConfigs().analyzer
        )

    override fun publishJob(context: WorkerScheduleContext, job: WorkerJob) {
        context.publish(AnalyzerEndpoint, AnalyzerRequest(job.id))
    }

    override fun isConfigured(configs: JobConfigurations): Boolean = true
}

private val advisorWorkerScheduleInfo =
    object : WorkerScheduleInfo(AdvisorEndpoint, dependsOn = listOf(AnalyzerEndpoint)) {
        override fun createJob(context: WorkerScheduleContext): WorkerJob? =
            context.jobConfigs().advisor?.let { config ->
                context.workerJobRepositories.advisorJobRepository.create(context.ortRun.id, config)
            }

        override fun publishJob(context: WorkerScheduleContext, job: WorkerJob) {
            context.publish(AdvisorEndpoint, AdvisorRequest(job.id))
        }

        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.advisor != null
    }

private val scannerWorkerScheduleInfo =
    object : WorkerScheduleInfo(ScannerEndpoint, dependsOn = listOf(AnalyzerEndpoint)) {
        override fun createJob(context: WorkerScheduleContext): WorkerJob? =
            context.jobConfigs().scanner?.let { config ->
                context.workerJobRepositories.scannerJobRepository.create(context.ortRun.id, config)
            }

        override fun publishJob(context: WorkerScheduleContext, job: WorkerJob) {
            context.publish(ScannerEndpoint, ScannerRequest(job.id))
        }

        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.scanner != null
    }

private val evaluatorWorkerScheduleInfo =
    object : WorkerScheduleInfo(EvaluatorEndpoint, runsAfter = listOf(AdvisorEndpoint, ScannerEndpoint)) {
        override fun createJob(context: WorkerScheduleContext): WorkerJob? =
            context.jobConfigs().evaluator?.let { config ->
                context.workerJobRepositories.evaluatorJobRepository.create(context.ortRun.id, config)
            }

        override fun publishJob(context: WorkerScheduleContext, job: WorkerJob) {
            context.publish(EvaluatorEndpoint, EvaluatorRequest(job.id))
        }

        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.evaluator != null
    }

private val reporterWorkerScheduleInfo =
    object : WorkerScheduleInfo(ReporterEndpoint, runsAfter = listOf(EvaluatorEndpoint), runAfterFailure = true) {
        override fun createJob(context: WorkerScheduleContext): WorkerJob? =
            context.jobConfigs().reporter?.let { config ->
                context.workerJobRepositories.reporterJobRepository.create(context.ortRun.id, config)
            }

        override fun publishJob(context: WorkerScheduleContext, job: WorkerJob) {
            context.publish(ReporterEndpoint, ReporterRequest(job.id))
        }

        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.reporter != null
    }

private val notifierWorkerScheduleInfo =
    object : WorkerScheduleInfo(NotifierEndpoint, dependsOn = listOf(ReporterEndpoint), runAfterFailure = true) {
        override fun createJob(context: WorkerScheduleContext): WorkerJob? =
            context.jobConfigs().notifier?.let { config ->
                context.workerJobRepositories.notifierJobRepository.create(context.ortRun.id, config)
            }

        override fun publishJob(context: WorkerScheduleContext, job: WorkerJob) {
            context.publish(NotifierEndpoint, NotifierRequest(job.id))
        }

        override fun isConfigured(configs: JobConfigurations): Boolean =
            configs.notifier != null
    }

/**
 * A map allowing access to the [WorkerScheduleInfo] objects for the different worker endpoints.
 */
internal val scheduleInfos = mapOf(
    AnalyzerEndpoint.configPrefix to analyzerWorkerScheduleInfo,
    AdvisorEndpoint.configPrefix to advisorWorkerScheduleInfo,
    ScannerEndpoint.configPrefix to scannerWorkerScheduleInfo,
    EvaluatorEndpoint.configPrefix to evaluatorWorkerScheduleInfo,
    ReporterEndpoint.configPrefix to reporterWorkerScheduleInfo,
    NotifierEndpoint.configPrefix to notifierWorkerScheduleInfo
)
