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

package org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor

import com.google.gson.reflect.TypeToken

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.util.Watch

import org.slf4j.LoggerFactory

/**
 * An internal helper class that wraps and simplifies access to the Kubernetes [Watch] API for jobs. An instance
 * filters only for relevant events. It manages the current resource version and creates new [Watch] objects as
 * necessary.
 */
internal class JobWatchHelper(
    /** The object to access the Batch API. */
    private val jobApi: BatchV1Api,

    /** The namespace to watch. */
    private val namespace: String,

    /** The resource version to start watching from. */
    initialResourceVersion: String?
) {
    companion object {
        /** Constant for the type of MODIFIED events. */
        private const val EVENT_TYPE_MODIFIED = "MODIFIED"

        /** Constant for the type of BOOKMARK events. */
        private const val EVENT_TYPE_BOOKMARK = "BOOKMARK"

        private val logger = LoggerFactory.getLogger(JobWatchHelper::class.java)

        /** Constant for the [TypeToken] to passed to the Watch API to monitor jobs. */
        val JOB_TYPE = object : TypeToken<Watch.Response<V1Job>>() {}

        /**
         * Create a new [JobWatchHelper] instance based on the given [config] using the given [jobApi]. Watching starts
         * at the provided [resourceVersion] if it is defined. Otherwise, the initial resource version is obtained by
         * listing the current job state.
         */
        fun create(jobApi: BatchV1Api, config: MonitorConfig, resourceVersion: String? = null): JobWatchHelper =
            JobWatchHelper(jobApi, config.namespace, resourceVersion)

        /**
         * Obtain the current resource version for starting watching on [namespace] by querying the list of current
         * jobs using the given [jobApi].
         */
        private fun fetchResourceVersion(jobApi: BatchV1Api, namespace: String): String? =
            jobApi.listNamespacedJob(namespace, null, false, null, null, null, 1, null, null, null, false)
                .metadata?.resourceVersion
    }

    /** The latest resource version. */
    private var resourceVersion: String? = initialResourceVersion

    /**
     * Stores the resource version that was used to create the latest watch. When creating a new watch, it is checked
     * whether the current resource version is different from this one. Otherwise, watching seems to have stalled,
     * and the list with jobs is to requested anew.
     */
    private var watchResourceVersion: String? = null

    /** The current iterator over watch events. */
    private var watchIterator: Iterator<Watch.Response<V1Job>>? = null

    /**
     * Return the next available watch event. Handle exceptions and create another [Watch] if necessary.
     */
    fun nextEvent(): Watch.Response<V1Job> {
        while (true) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val iterator = watchIterator ?: createWatch().also { watchIterator = it }

                if (iterator.hasNext()) {
                    val event = iterator.next()

                    when (event.type) {
                        EVENT_TYPE_BOOKMARK -> resourceVersion = event.`object`.metadata?.resourceVersion
                        EVENT_TYPE_MODIFIED -> return event
                    }
                } else {
                    watchIterator = null
                }
            } catch (e: Throwable) {
                logger.error("Exception during watch handling.", e)
                watchIterator = null
            }
        }
    }

    /**
     * Create a new [Watch] object to monitor jobs.
     */
    private fun createWatch(): Iterator<Watch.Response<V1Job>> {
        if (resourceVersion == watchResourceVersion) {
            logger.info("Querying jobs to retrieve an updated resource version.")
            resourceVersion = fetchResourceVersion(jobApi, namespace)
        }

        logger.info("Creating new Watch starting at resource version '$resourceVersion'.")

        watchResourceVersion = resourceVersion

        return Watch.createWatch<V1Job?>(
            jobApi.apiClient,
            jobApi.listNamespacedJobCall(
                namespace,
                null,
                true,
                null,
                null,
                null,
                null,
                resourceVersion,
                null,
                null,
                true,
                null
            ),
            JOB_TYPE.type
        ).iterator()
    }
}
