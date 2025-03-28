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

import io.kotest.core.spec.style.StringSpec
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

import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.time.Duration.Companion.minutes

/** Constant for the maximum age of a job before it gets deleted. */
private val maxJobAge = 10.minutes

class ReaperTaskTest : StringSpec({
    beforeSpec {
        mockkObject(JobHandler)
    }

    afterSpec {
        unmockkAll()
    }

    "Completed jobs that have reached the maximum age should be removed" {
        val zoneOffset = ZoneOffset.ofHours(2)
        val date = OffsetDateTime.of(
            /* year = */ 2023,
            /* month = */ Month.MAY.value,
            /* dayOfMonth = */ 3,
            /* hour = */ 12,
            /* minute = */ 8,
            /* second = */ 11,
            /* nanoOfSecond = */ 0,
            zoneOffset
        )

        val timeHelper = mockk<TimeHelper> {
            every { before(maxJobAge) } returns date
        }

        val jobs = (1..8).map { createJob() }
        val jobHandler = mockk<JobHandler>()
        every { jobHandler.findJobsCompletedBefore(date) } returns jobs
        jobs.forEach {
            coEvery { jobHandler.deleteAndNotifyIfFailed(it) } just runs
        }

        val reaper = ReaperTask(jobHandler, createConfig(), timeHelper)
        reaper.execute()

        jobs.forAll { job ->
            coVerify {
                jobHandler.deleteAndNotifyIfFailed(job)
            }
        }
    }
})

/**
 * Create a test configuration for the reaper.
 */
private fun createConfig(): MonitorConfig =
    mockk {
        every { reaperMaxAge } returns maxJobAge
    }

/**
 * Create a dummy job.
 */
private fun createJob(): V1Job =
    mockk<V1Job> {
        every { metadata } returns mockk {
            every { name } returns "someJob"
        }
    }
