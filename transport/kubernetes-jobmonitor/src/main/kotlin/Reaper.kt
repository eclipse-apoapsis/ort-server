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

import io.kubernetes.client.openapi.models.V1Job

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.time.Duration

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor.JobHandler.Companion.isFailed

import org.slf4j.LoggerFactory

/**
 * A class that periodically checks for completed and failed jobs.
 *
 * This class is used to clean up completed jobs and their pods periodically. It also acts as a safety net for
 * [JobMonitor] if this component is temporarily unavailable and thus events regarding failed jobs are missed. Those
 * jobs are eventually picked up by this class, and corresponding notifications are sent.
 */
internal class Reaper(
    /** The object to query and manipulate jobs. */
    private val jobHandler: JobHandler,

    /** The object to send notifications about failed jobs. */
    private val notifier: FailedJobNotifier,

    /** The maximum age of a completed job before it gets removed. */
    private val maxJobAge: Duration,

    /** The clock to determine the current time and the age of jobs. */
    private val clock: Clock = Clock.System,

    /** The default zone offset for this system. */
    private val systemZoneOffset: ZoneOffset = OffsetDateTime.now().offset
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Reaper::class.java)
    }

    /**
     * Run reaper jobs periodically using the provided [tickFlow] as timer. On each element received from the flow, a
     * check of completed jobs is done.
     */
    suspend fun run(tickFlow: Flow<Unit>) {
        tickFlow.onEach { reap() }.collect()
    }

    /**
     * Perform a reap run.
     */
    private fun reap() {
        val time = Instant.ofEpochSecond(clock.now().minus(maxJobAge).epochSeconds).atOffset(systemZoneOffset)
        logger.info("Starting a Reaper run. Processing completed jobs before {}.", time)

        handleFailedJobs(jobHandler.findJobsCompletedBefore(time)).forEach { job ->
            logger.debug("Removing completed job '{}'.", job.metadata?.name)
            jobHandler.deleteJob(job)
        }
    }

    /**
     * Check whether the list of [completeJobs] contains failed jobs. If so, send corresponding failure notifications
     * for them. Return a list with jobs that can be safely deleted. If sending a failure notification for a job has
     * failed, filter this job out from the list, so that another attempt to send a notification is done the next time
     * the reaper runs.
     */
    private fun handleFailedJobs(completeJobs: List<V1Job>): List<V1Job> {
        val (failedJobs, succeededJobs) = completeJobs.partition { it.isFailed() }

        val notifiedJobs = failedJobs.map { job ->
            logger.info("Detected a failed job '{}'.", job.metadata?.name)
            logger.debug("Details of the failed job: {}", job)

            job to runCatching {
                notifier.sendFailedJobNotification(job)
            }.onFailure { exception ->
                logger.error("Failed to notify about failed job: '{}'.", job.metadata?.name, exception)
            }
        }.filterNot { it.second.isFailure }
            .map { it.first }

        return succeededJobs + notifiedJobs
    }
}

/**
 * Return a [Flow] that produces periodic tick events in the given [interval]. This is used as the timer for triggering
 * [Reaper] runs periodically.
 */
internal fun tickerFlow(interval: Duration): Flow<Unit> = flow {
    while (true) {
        delay(interval)
        emit(Unit)
    }
}
