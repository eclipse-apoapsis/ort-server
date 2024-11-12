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

package org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor

import kotlin.time.Duration

import org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor.JobHandler.Companion.isTimeout
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

import org.slf4j.LoggerFactory

/**
 * A class that periodically checks for jobs that are running longer than a configured timeout.
 *
 * The purpose of this class is to detect jobs that hang or encounter other problems that prevent their proper
 * execution. Such jobs are then terminated. If the [LostJobsFinder] component is active, it will find out that jobs
 * are missing and notify the Orchestrator to mark the ORT run as failed.
 *
 * Since worker jobs of different types typically have different execution times, the timeouts can be configured
 * separately for each worker type.
 */
internal class LongRunningJobsFinder(
    /** The object to query and manipulate jobs. */
    private val jobHandler: JobHandler,

    /** The configuration. */
    private val monitorConfig: MonitorConfig,

    /** The object for time calculations. */
    private val timeHelper: TimeHelper
) {
    companion object {
        private val logger = LoggerFactory.getLogger(LongRunningJobsFinder::class.java)
    }

    /**
     * Schedule an action to periodically check for long-running jobs on the given [scheduler] according to the
     * configuration.
     */
    fun run(scheduler: Scheduler) {
        scheduler.schedule(monitorConfig.longRunningJobsInterval, this::checkForLongRunningJobs)
    }

    /**
     * Perform a check for long-running jobs.
     */
    private fun checkForLongRunningJobs() {
        checkForLongRunningJobsForEndpoint(ConfigEndpoint, monitorConfig.timeoutConfig.configTimeout)
        checkForLongRunningJobsForEndpoint(AnalyzerEndpoint, monitorConfig.timeoutConfig.analyzerTimeout)
        checkForLongRunningJobsForEndpoint(AdvisorEndpoint, monitorConfig.timeoutConfig.advisorTimeout)
        checkForLongRunningJobsForEndpoint(ScannerEndpoint, monitorConfig.timeoutConfig.scannerTimeout)
        checkForLongRunningJobsForEndpoint(EvaluatorEndpoint, monitorConfig.timeoutConfig.evaluatorTimeout)
        checkForLongRunningJobsForEndpoint(ReporterEndpoint, monitorConfig.timeoutConfig.reporterTimeout)
        checkForLongRunningJobsForEndpoint(NotifierEndpoint, monitorConfig.timeoutConfig.notifierTimeout)
    }

    /**
     * Check for long-running worker jobs for the given [endpoint] that have reached the given [timeout]. Delete
     * such jobs.
     */
    private fun checkForLongRunningJobsForEndpoint(endpoint: Endpoint<*>, timeout: Duration) {
        val threshold = timeHelper.before(timeout)
        logger.info(
            "Checking for long-running jobs for endpoint '{}' started before {}.",
            endpoint.configPrefix,
            threshold
        )

        jobHandler.findJobsForWorker(endpoint)
            .filter { it.isTimeout(threshold) }
            .mapNotNull { it.metadata?.name }
            .forEach { job ->
                logger.info("Deleting long-running job '{}'.", job)
                jobHandler.deleteJob(job)
            }
    }
}
