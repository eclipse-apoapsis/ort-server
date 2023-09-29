/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod

import java.time.OffsetDateTime

import org.slf4j.LoggerFactory

/**
 * An internal helper class providing functionality to deal with jobs.
 */
internal class JobHandler(
    /** The API to access job objects. */
    private val jobApi: BatchV1Api,

    /** The core API. */
    private val api: CoreV1Api,

    /** The namespace that contains the objects of interest. */
    private val namespace: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobHandler::class.java)

        /** Constant for a condition type that indicates that a job has failed. */
        private const val FAILED_CONDITION = "Failed"

        /** Constant for a condition type that indicates a normal completion of a job. */
        private const val COMPLETE_CONDITION = "Complete"

        /** A set with the condition types that indicate that a job is completed. */
        private val COMPLETED_CONDITIONS = setOf(COMPLETE_CONDITION, FAILED_CONDITION)

        /**
         * Return a flag whether this job has failed. For jobs that are still running the result is *false*.
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

    /**
     * Return a list with all currently existing jobs that have been completed before the given [time].
     */
    fun findJobsCompletedBefore(time: OffsetDateTime): List<V1Job> {
        return jobApi.listNamespacedJob(namespace, null, null, null, null, null, null, null, null, null, false)
            .items.filter { it.completedBefore(time) }
    }

    /**
     * Delete the given [job].
     */
    fun deleteJob(job: V1Job) {
        job.metadata?.name?.let { deleteJob(it) }
    }

    /**
     * Delete the job with the given [jobName]. Log occurring exceptions, but ignore them otherwise.
     */
    fun deleteJob(jobName: String) {
        runCatching {
            jobApi.deleteNamespacedJob(jobName, namespace, null, null, null, null, null, null)
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

        return api.listNamespacedPod(namespace, null, null, null, null, selector, null, null, null, null, false).items
    }

    /**
     * Delete the given [pod]. Kubernetes does not automatically remove completed pods. Therefore, this class does the
     * removal when the associated jobs are completed.
     */
    private fun deletePod(pod: V1Pod) {
        pod.metadata?.name?.let { podName ->
            logger.info("Deleting pod $podName.")
            runCatching {
                api.deleteNamespacedPod(podName, namespace, null, null, null, null, null, null)
            }.onFailure { e ->
                logger.error("Could not remove pod '$podName': $e.")
            }
        }
    }
}
