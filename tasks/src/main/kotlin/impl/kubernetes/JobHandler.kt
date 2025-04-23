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

package org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod

import java.time.OffsetDateTime
import java.util.TreeMap

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext

import org.slf4j.LoggerFactory

/**
 * An internal helper class providing functionality to deal with jobs.
 */
internal class JobHandler(
    /** The API to access job objects. */
    private val jobApi: BatchV1Api,

    /** The core API. */
    private val api: CoreV1Api,

    /** The object to send notifications about failed jobs. */
    private val notifier: FailedJobNotifier,

    /** The configuration. */
    private val config: MonitorConfig,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobHandler::class.java)

        /** Constant for a condition type that indicates that a job has failed. */
        private const val FAILED_CONDITION = "Failed"

        /** Constant for a condition type that indicates a normal completion of a job. */
        private const val COMPLETE_CONDITION = "Complete"

        /** A set with the condition types that indicate that a job is completed. */
        private val COMPLETED_CONDITIONS = setOf(COMPLETE_CONDITION, FAILED_CONDITION)

        /** The label which stores the ORT run ID. */
        private const val RUN_ID_LABEL = "run-id"

        /** A prefix for the name of a label storing a part of the trace ID. */
        private const val TRACE_LABEL_PREFIX = "trace-id-"

        /**
         * A label selector to find only jobs for ORT Server components. Only those are handled when looking for
         * completed or failed jobs.
         */
        private val workerJobsLabelSelector = "ort-worker in " +
                Endpoint.entries().joinToString(",", prefix = "(", postfix = ")") { it.configPrefix }

        /**
         * Return a flag whether this job has failed. For jobs that are still running, the result is `false`.
         */
        fun V1Job.isFailed(): Boolean = status?.conditions.orEmpty().any { it.type == FAILED_CONDITION }

        /**
         * Return a flag whether this job has completed, either successfully or in failure state.
         * See https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.27/#jobstatus-v1-batch.
         */
        fun V1Job.isCompleted(): Boolean =
            status?.completionTime != null ||
                    status?.conditions.orEmpty().any { it.type in COMPLETED_CONDITIONS }

        /**
         * Return a flag whether this job has run into a timeout according to the given [threshold]. This means that
         * the job was started before the given date and is not yet completed.
         */
        fun V1Job.isTimeout(threshold: OffsetDateTime): Boolean =
            !isCompleted() && status?.startTime?.isBefore(threshold) == true

        /**
         * Obtain the ID of the ORT run from this job from the label used for this purpose. If the label is not set,
         * return *null*.
         */
        val V1Job.ortRunId: Long?
            get() = metadata?.labels?.get(RUN_ID_LABEL)?.toLongOrNull()

        /**
         * Return the trace ID from this job for the labels used for this purpose.
         */
        fun V1Job.traceId(): String {
            val labels = metadata?.labels.orEmpty()
            val traceLabels = labels.filterKeys { it.startsWith(TRACE_LABEL_PREFIX) }.toList()
                .sortedBy { it.first.substringAfter(TRACE_LABEL_PREFIX).toInt() }
            return traceLabels.fold("") { id, label -> "$id${label.second}" }
        }

        /**
         * Return a flag whether this job has completed before the given [time]. Note that a completion time is only
         * available for jobs that have been completed normally; in case of failed jobs, it is undefined. For such
         * jobs, this function returns *true*, since failed jobs need to be handled immediately.
         */
        private fun V1Job.completedBefore(time: OffsetDateTime): Boolean {
            if (!isCompleted()) return false

            val completionTime = status?.completionTime
            return completionTime == null || completionTime.isBefore(time)
        }
    }

    /** A set with the names of the jobs that have been processed recently. */
    private val recentJobNames = mutableSetOf<String>()

    /** Stores the times when jobs have been processed. */
    private val processingTimes = TreeMap<Instant, String>()

    /** A mutex for controlling access to the data structures for recent jobs. */
    private val recentJobsMutex = Mutex()

    /**
     * Return a list with all currently existing jobs that have been completed before the given [time].
     */
    fun findJobsCompletedBefore(time: OffsetDateTime): List<V1Job> =
        listJobs(workerJobsLabelSelector).filter { it.completedBefore(time) }

    /**
     * Return a list with all currently active jobs for the worker defined by the given [endpoint].
     */
    fun findJobsForWorker(endpoint: Endpoint<*>): List<V1Job> {
        val labelSelector = "ort-worker=${endpoint.configPrefix}"

        return listJobs(labelSelector)
    }

    /**
     * Delete the given [job]. Check whether it is a failed job. If so, try sending a corresponding notification
     * using [notifier] and delete the job only if this is successful. This operation is needed by both the reaper
     * and the monitor components when they detect a completed job.
     */
    suspend fun deleteAndNotifyIfFailed(job: V1Job) {
        job.metadata?.name?.takeIf { canProcess(it) }?.let { jobName ->
            withMdcContext(
                "traceId" to (job.traceId().takeIf { it.isNotEmpty() } ?: "unknown"),
                "ortRunId" to (job.ortRunId?.toString() ?: "unknown")
            ) {
                runCatching {
                    if (job.isFailed()) {
                        logger.info("Detected a failed job '{}'.", jobName)
                        logger.debug("Details of the failed job: {}", job)

                        notifier.sendFailedJobNotification(job)
                    }
                }.onFailure { exception ->
                    logger.error("Failed to notify about failed job: '{}'.", jobName, exception)
                }.onSuccess {
                    deleteJob(jobName)
                }
            }
        }
    }

    /**
     * Delete the job with the given [jobName]. Log occurring exceptions, but ignore them otherwise.
     */
    fun deleteJob(jobName: String) {
        runCatching {
            jobApi.deleteNamespacedJob(jobName, config.namespace).execute()
        }.onFailure { e ->
            logger.error("Could not remove job '$jobName': $e.")
        }

        findPodsForJob(jobName).forEach(this::deletePod)
    }

    /**
     * Find all pods that have been created for the job with the specified [jobName].
     */
    private fun findPodsForJob(jobName: String): List<V1Pod> {
        val selector = "job-name=$jobName"

        return api.listNamespacedPod(config.namespace).labelSelector(selector).watch(false).execute().items
    }

    /**
     * Delete the given [pod]. Kubernetes does not automatically remove completed pods. Therefore, this class does the
     * removal when the associated jobs are completed.
     */
    private fun deletePod(pod: V1Pod) {
        pod.metadata?.name?.let { podName ->
            logger.info("Deleting pod $podName.")
            runCatching {
                api.deleteNamespacedPod(podName, config.namespace).execute()
            }.onFailure { e ->
                logger.error("Could not remove pod '$podName': $e.")
            }
        }
    }

    /**
     * Check whether the job with the given [jobName] can be processed. Return *false* if this job has already been
     * processed in the configured time window. Also update the data structures for the recently processed jobs.
     */
    private suspend fun canProcess(jobName: String): Boolean {
        val now = Clock.System.now()
        val recentThreshold = now - config.recentlyProcessedInterval

        return recentJobsMutex.withLock {
            // Remove older entries from the data structures.
            while (processingTimes.isNotEmpty() && processingTimes.firstKey() < recentThreshold) {
                val entry = processingTimes.firstEntry()
                processingTimes -= entry.key
                recentJobNames -= entry.value
            }

            if (jobName in recentJobNames) {
                false
            } else {
                recentJobNames += jobName
                processingTimes[now] = jobName
                true
            }
        }
    }

    /**
     * Return a list with the jobs in the configured namespace. Apply the given [labelSelector] filter.
     */
    private fun listJobs(labelSelector: String?): List<V1Job> =
        jobApi.listNamespacedJob(config.namespace).labelSelector(labelSelector).watch(false).execute().items
}
