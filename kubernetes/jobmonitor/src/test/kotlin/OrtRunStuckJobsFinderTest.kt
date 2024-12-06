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

import io.kotest.core.spec.style.StringSpec

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import kotlin.time.Duration.Companion.minutes

import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository

class OrtRunStuckJobsFinderTest : StringSpec(
    {
        "Notification should not be sent if there are no active ort runs" {
            val ortRunRepo = mockk<OrtRunRepository> {
                every { listActive(any()) } returns emptyList()
            }

            val notifier = mockk<FailedJobNotifier> {
                every { sendStuckJobsNotification(any()) } just runs
            }

            val finder = OrtRunStuckJobsFinder(
                emptyJobListRepositoryMock<AnalyzerJob, AnalyzerJobRepository>(),
                emptyJobListRepositoryMock<AdvisorJob, AdvisorJobRepository>(),
                emptyJobListRepositoryMock<ScannerJob, ScannerJobRepository>(),
                emptyJobListRepositoryMock<EvaluatorJob, EvaluatorJobRepository>(),
                emptyJobListRepositoryMock<ReporterJob, ReporterJobRepository>(),
                emptyJobListRepositoryMock<NotifierJob, NotifierJobRepository>(),
                config,
                ortRunRepo,
                notifier
            )

            val scheduleHelper = SchedulerTestHelper()

            // WHEN
            finder.run(scheduleHelper.scheduler)
            scheduleHelper.expectSchedule(runInterval).triggerAction()

            // THEN
            verify(exactly = 0) { notifier.sendStuckJobsNotification(any()) }
        }

        "Notification for run without jobs should be sent" {
            // GIVEN
            val ortRunRepo = mockk<OrtRunRepository> {
                every { listActive(any()) } returns listOf(
                    mockk {
                        every { id } answers { 10 }
                        every { traceId } returns ""
                    },
                    mockk {
                        every { id } answers { 20 }
                        every { traceId } returns ""
                    }
                )
            }

            val notifier = mockk<FailedJobNotifier> {
                every { sendStuckJobsNotification(any()) } just runs
            }

            val finder = OrtRunStuckJobsFinder(
                emptyJobListRepositoryMock<AnalyzerJob, AnalyzerJobRepository>(),
                emptyJobListRepositoryMock<AdvisorJob, AdvisorJobRepository>(),
                emptyJobListRepositoryMock<ScannerJob, ScannerJobRepository>(),
                emptyJobListRepositoryMock<EvaluatorJob, EvaluatorJobRepository>(),
                emptyJobListRepositoryMock<ReporterJob, ReporterJobRepository>(),
                emptyJobListRepositoryMock<NotifierJob, NotifierJobRepository>(),
                config,
                ortRunRepo,
                notifier
            )

            val scheduleHelper = SchedulerTestHelper()

            // WHEN
            finder.run(scheduleHelper.scheduler)
            scheduleHelper.expectSchedule(runInterval).triggerAction()

            // THEN
            verify { notifier.sendStuckJobsNotification(any()) }
        }

        "Notification for run with all jobs finished should be sent" {
            val ortRunId = 10L

            val ortRunRepo = mockk<OrtRunRepository> {
                every { listActive(any()) } returns listOf(
                    mockk {
                        every { id } answers { ortRunId }
                        every { traceId } returns ""
                    }
                )
            }

            val notifier = mockk<FailedJobNotifier> {
                every { sendStuckJobsNotification(any()) } just runs
            }

            val finder = OrtRunStuckJobsFinder(
                existingJobRepositoryMock<AnalyzerJob, AnalyzerJobRepository>(ortRunId, JobStatus.FAILED),
                existingJobRepositoryMock<AdvisorJob, AdvisorJobRepository>(ortRunId, JobStatus.FINISHED_WITH_ISSUES),
                existingJobRepositoryMock<ScannerJob, ScannerJobRepository>(ortRunId, JobStatus.FINISHED),
                existingJobRepositoryMock<EvaluatorJob, EvaluatorJobRepository>(ortRunId, JobStatus.FINISHED),
                existingJobRepositoryMock<ReporterJob, ReporterJobRepository>(ortRunId, JobStatus.FINISHED),
                existingJobRepositoryMock<NotifierJob, NotifierJobRepository>(ortRunId, JobStatus.FINISHED),
                config,
                ortRunRepo,
                notifier
            )

            val scheduleHelper = SchedulerTestHelper()

            finder.run(scheduleHelper.scheduler)
            scheduleHelper.expectSchedule(runInterval).triggerAction()

            verify { notifier.sendStuckJobsNotification(any()) }
        }

        "Notification for run with only finished jobs should be sent" {
            val ortRunId = 10L

            val ortRunRepo = mockk<OrtRunRepository> {
                every { listActive(any()) } returns listOf(
                    mockk {
                        every { id } answers { ortRunId }
                        every { traceId } returns ""
                    }
                )
            }

            val notifier = mockk<FailedJobNotifier> {
                every { sendStuckJobsNotification(any()) } just runs
            }

            val finder = OrtRunStuckJobsFinder(
                existingJobRepositoryMock<AnalyzerJob, AnalyzerJobRepository>(ortRunId, JobStatus.FINISHED),
                existingJobRepositoryMock<AdvisorJob, AdvisorJobRepository>(ortRunId, JobStatus.FINISHED),
                existingJobRepositoryMock<ScannerJob, ScannerJobRepository>(ortRunId, JobStatus.FINISHED_WITH_ISSUES),
                existingJobRepositoryMock<EvaluatorJob, EvaluatorJobRepository>(ortRunId, JobStatus.FAILED),
                emptyJobListRepositoryMock<ReporterJob, ReporterJobRepository>(),
                emptyJobListRepositoryMock<NotifierJob, NotifierJobRepository>(),
                config,
                ortRunRepo,
                notifier
            )

            val scheduleHelper = SchedulerTestHelper()

            finder.run(scheduleHelper.scheduler)
            scheduleHelper.expectSchedule(runInterval).triggerAction()

            verify { notifier.sendStuckJobsNotification(any()) }
        }

        "Notification for run with any of jobs in not finished state should not be sent" {
            val ortRunId = 10L

            val ortRunRepo = mockk<OrtRunRepository> {
                every { listActive(any()) } returns listOf(
                    mockk {
                        every { id } answers { ortRunId }
                        every { traceId } returns ""
                    }
                )
            }

            val notifier = mockk<FailedJobNotifier> {
                every { sendStuckJobsNotification(any()) } just runs
            }

            val finder = OrtRunStuckJobsFinder(
                existingJobRepositoryMock<AnalyzerJob, AnalyzerJobRepository>(ortRunId, JobStatus.FINISHED),
                existingJobRepositoryMock<AdvisorJob, AdvisorJobRepository>(ortRunId, JobStatus.FINISHED),
                existingJobRepositoryMock<ScannerJob, ScannerJobRepository>(ortRunId, JobStatus.FINISHED_WITH_ISSUES),
                existingJobRepositoryMock<EvaluatorJob, EvaluatorJobRepository>(ortRunId, JobStatus.RUNNING),
                emptyJobListRepositoryMock<ReporterJob, ReporterJobRepository>(),
                emptyJobListRepositoryMock<NotifierJob, NotifierJobRepository>(),
                config,
                ortRunRepo,
                notifier
            )

            val scheduleHelper = SchedulerTestHelper()

            finder.run(scheduleHelper.scheduler)
            scheduleHelper.expectSchedule(runInterval).triggerAction()

            verify(exactly = 0) { notifier.sendStuckJobsNotification(any()) }
        }

        "Two notifications should be send for runs with no jobs" {
            val ortRunId = 10L
            val ortRunId2 = 20L

            val ortRunRepo = mockk<OrtRunRepository> {
                every { listActive(any()) } returns listOf(
                    mockk {
                        every { id } answers { ortRunId }
                        every { traceId } returns ""
                    },
                    mockk {
                        every { id } answers { ortRunId2 }
                        every { traceId } returns ""
                    }
                )
            }

            val notifier = mockk<FailedJobNotifier> {
                every { sendStuckJobsNotification(any()) } just runs
            }

            val finder = OrtRunStuckJobsFinder(
                emptyJobListRepositoryMock<AnalyzerJob, AnalyzerJobRepository>(),
                emptyJobListRepositoryMock<AdvisorJob, AdvisorJobRepository>(),
                emptyJobListRepositoryMock<ScannerJob, ScannerJobRepository>(),
                emptyJobListRepositoryMock<EvaluatorJob, EvaluatorJobRepository>(),
                emptyJobListRepositoryMock<ReporterJob, ReporterJobRepository>(),
                emptyJobListRepositoryMock<NotifierJob, NotifierJobRepository>(),
                config,
                ortRunRepo,
                notifier
            )

            val scheduleHelper = SchedulerTestHelper()

            finder.run(scheduleHelper.scheduler)
            scheduleHelper.expectSchedule(runInterval).triggerAction()

            verify(exactly = 2) { notifier.sendStuckJobsNotification(any()) }
        }
    }
)

/** The configuration setting for the minimum ort run age. */
private val ortRunMinAge = 1.minutes

/** The interval in which the lost jobs component should run. */
private val runInterval = 3.minutes

private val config = mockk<MonitorConfig> {
    every { stuckJobsInterval } returns runInterval
    every { stuckJobsMinAge } returns ortRunMinAge
}

/**
 * Create a mock repository for a concrete worker type that returns the given [activeJobs].
 */
private inline fun <J : WorkerJob, reified R : WorkerJobRepository<J>> emptyJobListRepositoryMock(): R =
    mockk {
        every { getForOrtRun(any()) } returns null
    }

private inline fun <reified J : WorkerJob, reified R : WorkerJobRepository<J>> existingJobRepositoryMock(
    ortRunId: Long,
    jobStatus: JobStatus
): R =
    mockk {
        every { getForOrtRun(ortRunId) } returns mockk {
            every { status } returns jobStatus
        }
    }
