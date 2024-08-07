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

package org.eclipse.apoapsis.ortserver.transport.kubernetes.jobmonitor

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.time.Duration

import kotlinx.datetime.Clock

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
     * Run reaper jobs periodically in the given [interval] using the provided [scheduler].
     */
    fun run(scheduler: Scheduler, interval: Duration) {
        scheduler.schedule(interval) { reap() }
    }

    /**
     * Perform a reap run.
     */
    private suspend fun reap() {
        val time = Instant.ofEpochSecond(clock.now().minus(maxJobAge).epochSeconds).atOffset(systemZoneOffset)
        logger.info("Starting a Reaper run. Processing completed jobs before {}.", time)

        val completeJobs = jobHandler.findJobsCompletedBefore(time)

        logger.debug("Found {} completed jobs.", completeJobs.size)

        completeJobs.forEach { jobHandler.deleteAndNotifyIfFailed(it) }
    }
}
