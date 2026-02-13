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

package org.eclipse.apoapsis.ortserver.utils.logging

import kotlin.time.Clock

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

import org.slf4j.LoggerFactory

/**
 * An object providing functionality to log structured information about the status of job executions.
 *
 * The purpose of this functionality is to simplify monitoring of periodic jobs by providing a consistent and
 * structured log output that can be easily parsed and analyzed by log management tools. The information included in
 * the log output allows dashboards answering important questions about the job executions, such as
 * - "Did the job run at the scheduled time?"
 * - "Did the job execution succeed or fail?"
 * - "How long did the job execution take?"
 */
object JobStatusLogging {
    /** The name of the logger used for logging the status of a job execution. */
    const val JOB_STATUS_LOGGER_NAME = "jobStatusLogger"

    /** The key used in the job status log to indicate the status of the job execution. */
    const val STATUS_KEY = "status"

    /** The key used in the job status log that contains the error message in case of a failed job execution. */
    const val ERROR_KEY = "error"

    /** The key used in the job status log that contains the execution time of the job (in milliseconds). */
    const val EXECUTION_TIME_KEY = "executionTimeMs"

    /** The key used in the job status log that contains the timestamp of the job execution. */
    const val TIMESTAMP_KEY = "timestamp"

    /**
     * A default key to report the total number of items that has been processed during the job execution. This can be
     * used by jobs doing some kind of batch processing.
     */
    const val PROCESSED_COUNT_KEY = "processedCount"

    /**
     * A default key to report the number of items that has been failed during the job execution. This is also useful
     * information for jobs doing batch processing.
     */
    const val FAILED_COUNT_KEY = "failedCount"

    /** The value for the [STATUS_KEY] property in the job status log indicating a successful job execution. */
    const val STATUS_SUCCESS = "success"

    /** The value for the [STATUS_KEY] property in the job status log indicating a failed job execution. */
    const val STATUS_FAILURE = "failure"

    private val logger = LoggerFactory.getLogger(JOB_STATUS_LOGGER_NAME)

    /**
     * Execute the given [block] with the logic of the job with the given [jobName] and log the status of the job
     * execution in a JSON format. Make sure that the given [mdcElements] are included in the MDC context during the
     * job execution. Return a flag whether the job execution was successful or not.
     *
     * The idea behind this function is that job implementations should wrap their logic in a call to this function to
     * ensure that the status of their execution is always logged in a consistent and structured way. This makes it
     * possible to evaluate and post-process the logs of different jobs homogeneously, e.g. for monitoring purposes.
     * In addition to the standard information logged by this function, the [block] can use the provided
     * [JsonObjectBuilder] to add custom properties to the job status log, e.g. to include information about the number
     * of items it has processed during execution.
     */
    suspend fun runWithStatusLogging(
        jobName: String,
        vararg mdcElements: Pair<MdcKey, String>,
        block: suspend CoroutineScope.(JsonObjectBuilder) -> Unit
    ): Boolean {
        val startTime = System.currentTimeMillis()

        val elementsWithComponent = arrayOf(*mdcElements, StandardMdcKeys.COMPONENT to jobName)
        return withMdcContext(*elementsWithComponent) {
            runCatching {
                buildJsonObject {
                    block(this)
                }
            }.onSuccess { result ->
                val properties = result.toMutableMap()
                properties[STATUS_KEY] = JsonPrimitive(STATUS_SUCCESS)
                properties[EXECUTION_TIME_KEY] = executionTimeField(startTime)
                properties[TIMESTAMP_KEY] = timestampField()

                val jobStatus = JsonObject(properties)
                logger.info(jobStatus.toString())
            }.onFailure { exception ->
                val jobStatus = buildJsonObject {
                    put(EXECUTION_TIME_KEY, executionTimeField(startTime))
                    put(TIMESTAMP_KEY, timestampField())
                    put(STATUS_KEY, JsonPrimitive(STATUS_FAILURE))
                    put(ERROR_KEY, JsonPrimitive("${exception::class.simpleName}: ${exception.message}"))
                }

                logger.error("Execution of job '$jobName' failed.", exception)
                logger.info(jobStatus.toString())
            }
        }.isSuccess
    }

    /**
     * Return the JSON property for the execution time of a job based on the given [startTime] in milliseconds.
     */
    private fun executionTimeField(startTime: Long): JsonPrimitive =
        JsonPrimitive(System.currentTimeMillis() - startTime)

    /**
     * Return the JSON property for the timestamp of a job execution. This is calculated from the current time.
     */
    private fun timestampField(): JsonPrimitive =
        JsonPrimitive(Clock.System.now().toString())
}
