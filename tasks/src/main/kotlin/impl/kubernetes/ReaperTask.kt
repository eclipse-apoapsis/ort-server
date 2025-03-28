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

import org.eclipse.apoapsis.ortserver.tasks.Task

import org.slf4j.LoggerFactory

/**
 * A task implementation that periodically checks for completed and failed jobs in Kubernetes.
 *
 * This class is used to clean up completed jobs and their pods periodically. It also detects failed jobs and sends
 * corresponding notifications to the Orchestrator.
 */
internal class ReaperTask(
    /** The object to query and manipulate jobs. */
    private val jobHandler: JobHandler,

    /** The configuration. */
    private val config: MonitorConfig,

    /** The object for time calculations. */
    private val timeHelper: TimeHelper
) : Task {
    companion object {
        private val logger = LoggerFactory.getLogger(ReaperTask::class.java)
    }

    override suspend fun execute() {
        val time = timeHelper.before(config.reaperMaxAge)
        logger.info("Starting a Reaper run. Processing completed jobs before {}.", time)

        val completeJobs = jobHandler.findJobsCompletedBefore(time)

        logger.debug("Found {} completed jobs.", completeJobs.size)

        completeJobs.forEach { jobHandler.deleteAndNotifyIfFailed(it) }
    }
}
