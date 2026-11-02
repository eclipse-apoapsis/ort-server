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

package org.eclipse.apoapsis.ortserver.orchestrator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.JobStatus.FAILED
import org.eclipse.apoapsis.ortserver.model.JobStatus.FINISHED
import org.eclipse.apoapsis.ortserver.model.JobStatus.FINISHED_WITH_ISSUES
import org.eclipse.apoapsis.ortserver.model.JobStatus.RUNNING
import org.eclipse.apoapsis.ortserver.model.JobStatus.SCHEDULED
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.ADVISOR
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.ANALYZER
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.EVALUATOR
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.NOTIFIER
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.REPORTER
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.SCANNER

class RunStateTest : WordSpec({
    "nextJobs()" should {
        "return the analyzer if no job was created yet" {
            runState().nextJobs() should containExactlyInAnyOrder(ANALYZER)
        }

        "return nothing while the analyzer is running" {
            runState(ANALYZER to RUNNING).nextJobs() should beEmpty()
        }

        "return the advisor and the scanner after the analyzer finished" {
            runState(ANALYZER to FINISHED).nextJobs() should containExactlyInAnyOrder(ADVISOR, SCANNER)
        }

        "treat a job that finished with issues as successful" {
            runState(ANALYZER to FINISHED_WITH_ISSUES).nextJobs() should containExactlyInAnyOrder(ADVISOR, SCANNER)
        }

        "return nothing while the advisor is running" {
            runState(ANALYZER to FINISHED, ADVISOR to RUNNING, SCANNER to FINISHED).nextJobs() should beEmpty()
        }

        "return nothing while the scanner is running" {
            runState(ANALYZER to FINISHED, ADVISOR to FINISHED, SCANNER to SCHEDULED).nextJobs() should beEmpty()
        }

        "return the evaluator after the advisor and the scanner finished" {
            runState(ANALYZER to FINISHED, ADVISOR to FINISHED, SCANNER to FINISHED)
                .nextJobs() should containExactlyInAnyOrder(EVALUATOR)
        }

        "return nothing while the evaluator is running" {
            runState(ANALYZER to FINISHED, ADVISOR to FINISHED, SCANNER to FINISHED, EVALUATOR to RUNNING)
                .nextJobs() should beEmpty()
        }

        "return the reporter after the evaluator finished" {
            runState(ANALYZER to FINISHED, ADVISOR to FINISHED, SCANNER to FINISHED, EVALUATOR to FINISHED)
                .nextJobs() should containExactlyInAnyOrder(REPORTER)
        }

        "return the notifier after the reporter finished" {
            runState(*allFinishedExcept(NOTIFIER)).nextJobs() should containExactlyInAnyOrder(NOTIFIER)
        }

        "return nothing if all jobs were scheduled" {
            runState(*allFinishedExcept(NOTIFIER), NOTIFIER to RUNNING).nextJobs() should beEmpty()
        }
    }

    "nextJobs() in failure state" should {
        "return nothing if the Config worker failed" {
            runState(configWorkerFailed = true).nextJobs() should beEmpty()
        }

        "return the reporter if the analyzer failed" {
            runState(ANALYZER to FAILED).nextJobs() should containExactlyInAnyOrder(REPORTER)
        }

        "return the reporter if the advisor failed" {
            runState(ANALYZER to FINISHED, ADVISOR to FAILED, SCANNER to FINISHED)
                .nextJobs() should containExactlyInAnyOrder(REPORTER)
        }

        "return nothing if the advisor failed while the scanner is still running" {
            runState(ANALYZER to FINISHED, ADVISOR to FAILED, SCANNER to RUNNING).nextJobs() should beEmpty()
        }

        "return nothing if the scanner failed while the advisor is still running" {
            runState(ANALYZER to FINISHED, ADVISOR to SCHEDULED, SCANNER to FAILED).nextJobs() should beEmpty()
        }

        "return the reporter if the evaluator failed" {
            runState(ANALYZER to FINISHED, ADVISOR to FINISHED, SCANNER to FINISHED, EVALUATOR to FAILED)
                .nextJobs() should containExactlyInAnyOrder(REPORTER)
        }

        "return the notifier if the reporter failed" {
            runState(*allFinishedExcept(NOTIFIER, REPORTER), REPORTER to FAILED)
                .nextJobs() should containExactlyInAnyOrder(NOTIFIER)
        }

        "return the notifier after the reporter finished if the evaluator failed" {
            runState(*allFinishedExcept(NOTIFIER, EVALUATOR), EVALUATOR to FAILED)
                .nextJobs() should containExactlyInAnyOrder(NOTIFIER)
        }
    }

    "nextJobs() with a partial configuration" should {
        "return the reporter after the analyzer if only these are configured" {
            val state = runState(ANALYZER to FINISHED, configured = setOf(ANALYZER, REPORTER))

            state.nextJobs() should containExactlyInAnyOrder(REPORTER)
        }

        "not return the reporter while the analyzer is running if only these are configured" {
            runState(ANALYZER to RUNNING, configured = setOf(ANALYZER, REPORTER)).nextJobs() should beEmpty()
        }

        "return the evaluator after the scanner if no advisor is configured" {
            val state = runState(ANALYZER to FINISHED, SCANNER to FINISHED, configured = allExcept(ADVISOR))

            state.nextJobs() should containExactlyInAnyOrder(EVALUATOR)
        }

        "return the evaluator after the advisor if no scanner is configured" {
            val state = runState(ANALYZER to FINISHED, ADVISOR to FINISHED, configured = allExcept(SCANNER))

            state.nextJobs() should containExactlyInAnyOrder(EVALUATOR)
        }

        "return the reporter after the advisor and the scanner if no evaluator is configured" {
            val state = runState(
                ANALYZER to FINISHED,
                ADVISOR to FINISHED,
                SCANNER to FINISHED,
                configured = allExcept(EVALUATOR)
            )

            state.nextJobs() should containExactlyInAnyOrder(REPORTER)
        }

        "not return the evaluator if the analyzer failed" {
            runState(ANALYZER to FAILED, configured = setOf(ANALYZER, EVALUATOR)).nextJobs() should beEmpty()
        }

        "never return the notifier if no reporter is configured" {
            runState(*allFinishedExcept(NOTIFIER, REPORTER), configured = allExcept(REPORTER))
                .nextJobs() should beEmpty()
        }
    }

    "finalStatus()" should {
        "return null while a job is running" {
            runState(ANALYZER to FINISHED, ADVISOR to RUNNING, configured = setOf(ANALYZER, ADVISOR))
                .finalStatus() should beNull()
        }

        "return null while further jobs can be scheduled" {
            runState(ANALYZER to FINISHED).finalStatus() should beNull()
        }

        "return FINISHED if all jobs finished" {
            runState(*allFinishedExcept()).finalStatus() shouldBe OrtRunStatus.FINISHED
        }

        "return FINISHED_WITH_ISSUES if a job finished with issues" {
            runState(*allFinishedExcept(SCANNER), SCANNER to FINISHED_WITH_ISSUES)
                .finalStatus() shouldBe OrtRunStatus.FINISHED_WITH_ISSUES
        }

        "return FAILED if a job failed" {
            runState(*allFinishedExcept(SCANNER), SCANNER to FAILED, EVALUATOR to FINISHED_WITH_ISSUES)
                .finalStatus() shouldBe OrtRunStatus.FAILED
        }

        "return FAILED if a job failed and the remaining jobs do not run after failures" {
            runState(ANALYZER to FAILED, configured = setOf(ANALYZER, ADVISOR, SCANNER))
                .finalStatus() shouldBe OrtRunStatus.FAILED
        }

        "return FAILED if the Config worker failed" {
            runState(configWorkerFailed = true).finalStatus() shouldBe OrtRunStatus.FAILED
        }
    }

    "The constructor for job configurations" should {
        "determine the configured workers from the job configurations" {
            val configs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(),
                advisor = AdvisorJobConfiguration(),
                reporter = ReporterJobConfiguration()
            )

            val state = RunState(configs, mapOf(ANALYZER to FINISHED, ADVISOR to FINISHED))

            state.nextJobs() should containExactlyInAnyOrder(REPORTER)
        }
    }
})

/**
 * Create a [RunState] with the given [jobStatuses], the [configured] workers, and the [configWorkerFailed] flag. Per
 * default, all workers are configured.
 */
private fun runState(
    vararg jobStatuses: Pair<WorkerScheduleInfo, JobStatus>,
    configured: Set<WorkerScheduleInfo> = WorkerScheduleInfo.entries.toSet(),
    configWorkerFailed: Boolean = false
) = RunState(configured, jobStatuses.toMap(), configWorkerFailed)

/** Return all workers except for the given [excluded] ones. */
private fun allExcept(vararg excluded: WorkerScheduleInfo) = WorkerScheduleInfo.entries.toSet() - excluded.toSet()

/** Return job statuses where all workers except for the given [excluded] ones have finished. */
private fun allFinishedExcept(vararg excluded: WorkerScheduleInfo) =
    allExcept(*excluded).map { it to FINISHED }.toTypedArray()
