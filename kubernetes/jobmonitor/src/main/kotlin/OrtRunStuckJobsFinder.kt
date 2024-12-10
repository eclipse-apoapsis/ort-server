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

import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository

import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class OrtRunStuckJobsFinder(

    /** Analyzer jobs repository. */
    analyzerJobRepository: AnalyzerJobRepository,

    /** Advisor jobs repository. */
    advisorJobRepository: AdvisorJobRepository,

    /** Scanner jobs repository. */
    scannerJobRepository: ScannerJobRepository,

    /** Evaluator jobs repository. */
    evaluatorJobRepository: EvaluatorJobRepository,

    /** Reporter jobs repository. */
    reporterJobRepository: ReporterJobRepository,

    /** Notifier jobs repository. */
    notifierJobRepository: NotifierJobRepository,

    /** Configuration provider object. */
    private val monitorConfig: MonitorConfig,

    /** ORT runs repository. */
    private val ortRunRepository: OrtRunRepository,

    /** Job problems/errors notification sender component. */
    private val failedJobNotifier: FailedJobNotifier
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OrtRunStuckJobsFinder::class.java)
    }

    /** Job repositories list. */
    private val jobRepositories = setOf(
        analyzerJobRepository,
        advisorJobRepository,
        scannerJobRepository,
        evaluatorJobRepository,
        reporterJobRepository,
        notifierJobRepository
    )

    private val finishedStatuses = setOf(
        JobStatus.FINISHED,
        JobStatus.FAILED,
        JobStatus.FINISHED_WITH_ISSUES
    )

    /** Time calculations helper object. */
    private val timeHelper = TimeHelper()

    /**
     * Schedule an action to periodically check for stuck jobs on the given [scheduler] according to the configuration.
     */
    fun run(scheduler: Scheduler) {
        scheduler.schedule(monitorConfig.stuckJobsInterval, this::checkForStuckJobs)
    }

    /**
     * For all active Ort Runs older than configured period check if there are any jobs or if all jobs already finished.
     */
    private fun checkForStuckJobs() {
        var stuckJobOrtRunNumber = 0
        logger.debug("Starting stuck jobs finder run")
        ortRunRepository.listActive(timeHelper.now() - monitorConfig.stuckJobsMinAge)
            .forEach {
                if (checkIfOrtRunHasStuckJobs(it)) {
                    logger.warn("Ort run ${it.id} found as having stuck jobs.")
                    failedJobNotifier.sendStuckJobsNotification(it.id)
                    stuckJobOrtRunNumber++
                }
            }
        logger.debug("stuck jobs finder run finished. Found $stuckJobOrtRunNumber")
    }

    /**
     * Check if there any jobs given
     */
    private fun checkIfOrtRunHasStuckJobs(ortRun: OrtRun): Boolean {
        var jobsTotal = 0
        var jobsFinished = 0

        jobRepositories.forEach { jobRepository ->
            jobRepository.getForOrtRun(ortRun.id)?.status?.let {
                jobsTotal++
                if (it in finishedStatuses) {
                    jobsFinished++
                }
            }
        }

        return (jobsTotal == 0 || jobsTotal == jobsFinished)
    }
}
