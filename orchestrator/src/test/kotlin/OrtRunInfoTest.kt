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

package org.eclipse.apoapsis.ortserver.orchestrator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import org.eclipse.apoapsis.ortserver.model.JobStatus

class OrtRunInfoTest : WordSpec({
    "getNextJobs() with all jobs configured" should {
        val configuredJobs = WorkerScheduleInfo.entries.toSet()

        "return ANALYZER if no job was created yet" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = emptyMap()
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.ANALYZER)
        }

        "return nothing if ANALYZER is still running" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.RUNNING)
                )
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }

        "return ADVISOR and SCANNER if all previous jobs finished" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.ADVISOR, WorkerScheduleInfo.SCANNER)
        }

        "return nothing if ADVISOR is still running" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.RUNNING),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                )
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }

        "return nothing if SCANNER is still running" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.RUNNING)
                )
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }

        "return EVALUATOR if all previous jobs finished" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.EVALUATOR)
        }

        "return nothing if EVALUATOR is still running" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.EVALUATOR to WorkerJobInfo(id = 1, status = JobStatus.RUNNING)
                )
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }

        "return REPORTER if all previous jobs finished" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.EVALUATOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.REPORTER)
        }

        "return nothing if config worker failed" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = true,
                configuredJobs = configuredJobs,
                jobInfos = emptyMap()
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }

        "return REPORTER if ANALYZER failed" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FAILED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.REPORTER)
        }

        "return REPORTER if ADVISOR failed" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FAILED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.REPORTER)
        }

        "return REPORTER if SCANNER failed" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FAILED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.REPORTER)
        }

        "return REPORTER if EVALUATOR failed" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.EVALUATOR to WorkerJobInfo(id = 1, status = JobStatus.FAILED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.REPORTER)
        }

        "return NOTIFIER if REPORTER finished" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.EVALUATOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.REPORTER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.NOTIFIER)
        }

        "return NOTIFIER if REPORTER failed" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.ADVISOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.SCANNER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.EVALUATOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.REPORTER to WorkerJobInfo(id = 1, status = JobStatus.FAILED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.NOTIFIER)
        }

        "return nothing if all jobs finished" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = WorkerScheduleInfo.entries.associateWith {
                    WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                }
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }
    }

    "getNextJobs() with only ANALYZER and EVALUATOR configured" should {
        val configuredJobs = setOf(WorkerScheduleInfo.ANALYZER, WorkerScheduleInfo.EVALUATOR)

        "return ANALYZER if no job was created yet" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = emptyMap()
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.ANALYZER)
        }

        "return nothing if ANALYZER is still running" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.RUNNING)
                )
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }

        "return nothing if ANALYZER failed" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FAILED)
                )
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }

        "return EVALUATOR if ANALYZER finished" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                )
            )

            ortRunInfo.getNextJobs() should containExactly(WorkerScheduleInfo.EVALUATOR)
        }

        "return nothing if EVALUATOR is still running" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.EVALUATOR to WorkerJobInfo(id = 1, status = JobStatus.RUNNING)
                )
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }

        "return nothing if all jobs finished" {
            val ortRunInfo = OrtRunInfo(
                id = 1,
                configWorkerFailed = false,
                configuredJobs = configuredJobs,
                jobInfos = mapOf(
                    WorkerScheduleInfo.ANALYZER to WorkerJobInfo(id = 1, status = JobStatus.FINISHED),
                    WorkerScheduleInfo.EVALUATOR to WorkerJobInfo(id = 1, status = JobStatus.FINISHED)
                )
            )

            ortRunInfo.getNextJobs() should beEmpty()
        }
    }
})
