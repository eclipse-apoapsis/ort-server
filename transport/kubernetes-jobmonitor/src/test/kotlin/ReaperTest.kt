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

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll

import io.kubernetes.client.openapi.models.V1Job

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify

import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.time.Duration.Companion.minutes

import org.eclipse.apoapsis.ortserver.transport.kubernetes.jobmonitor.JobHandler.Companion.isFailed

/** Constant for the maximum age of a job before it gets deleted. */
private val maxJobAge = 10.minutes

/** The interval how frequently the reaper should run. */
private val reaperInterval = 7.minutes

class ReaperTest : WordSpec({
    beforeSpec {
        mockkObject(JobHandler)
    }

    afterSpec {
        unmockkAll()
    }

    coroutineDebugProbes = true

    "Reaper" should {
        "periodically query completed jobs" {
            val zoneOffset = ZoneOffset.ofHours(2)
            val date1 = OffsetDateTime.of(
                2023,
                Month.MAY.value,
                3,
                12,
                8,
                11,
                0,
                zoneOffset
            )
            val date2 = OffsetDateTime.of(
                2023,
                Month.MAY.value,
                3,
                12,
                11,
                15,
                0,
                zoneOffset
            )

            val timeHelper = mockk<TimeHelper> {
                every { before(maxJobAge) } returnsMany listOf(date1, date2)
            }

            val jobHandler = mockk<JobHandler>()
            every { jobHandler.findJobsCompletedBefore(any()) } returns emptyList()

            val reaper = Reaper(jobHandler, maxJobAge, timeHelper)
            val helper = SchedulerTestHelper()
            reaper.run(helper.scheduler, reaperInterval)
            helper.expectSchedule(reaperInterval).triggerAction(times = 2)

            verify {
                jobHandler.findJobsCompletedBefore(date1)
                jobHandler.findJobsCompletedBefore(date2)
            }
        }

        "remove completed jobs that have reached the maximum age" {
            val jobs = (1..8).map { createJob() }
            val jobHandler = mockk<JobHandler>()
            every { jobHandler.findJobsCompletedBefore(any()) } returns jobs
            jobs.forEach {
                coEvery { jobHandler.deleteAndNotifyIfFailed(it) } just runs
            }

            val reaper = Reaper(jobHandler, maxJobAge, TimeHelper())
            val helper = SchedulerTestHelper()
            reaper.run(helper.scheduler, reaperInterval)
            helper.expectSchedule(reaperInterval).triggerAction()

            jobs.forAll { job ->
                coVerify {
                    jobHandler.deleteAndNotifyIfFailed(job)
                }
            }
        }
    }
})

/**
 * Create a job with the given [failure status][failed].
 */
private fun createJob(failed: Boolean = false): V1Job =
    mockk<V1Job> {
        every { isFailed() } returns failed
        every { metadata } returns mockk {
            every { name } returns "someJob"
        }
    }
