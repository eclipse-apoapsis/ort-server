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

import org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor.JobHandler.Companion.isFailed

import org.slf4j.LoggerFactory

/**
 * A class that monitors the set of Kubernetes jobs for failures.
 *
 * This class uses [JobWatchHelper] to track changes on the set of jobs. If a change event indicates a failed job,
 * it sends a corresponding error message to the Orchestrator and removes the job immediately. That way the
 * Orchestrator is notified rather fast if something goes wrong.
 *
 * To avoid that failures are missed if this component is temporarily down, there is also a periodic check that loads
 * the list of current jobs and filters completed and failed ones. This makes sure that all failures are detected,
 * although with a potential delay.
 */
internal class JobMonitor(
    /** The object retrieving job change events. */
    private val watchHelper: JobWatchHelper,

    /** The object for manipulating jobs. */
    private val jobHandler: JobHandler,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobMonitor::class.java)
    }

    /**
     * Process events about changes in the state of jobs in an endless loop.
     */
    suspend fun watch() {
        logger.info("Entering watch loop.")

        while (true) {
            val job = watchHelper.nextEvent().`object`

            if (job.isFailed()) {
                jobHandler.deleteAndNotifyIfFailed(job)
            }
        }
    }
}
