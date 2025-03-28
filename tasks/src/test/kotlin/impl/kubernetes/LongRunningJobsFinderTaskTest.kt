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

package org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes

import io.kotest.core.spec.style.WordSpec

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1ObjectMeta

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

import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.JobHandler.Companion.isTimeout
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

class LongRunningJobsFinderTaskTest : WordSpec({
    beforeTest {
        mockkObject(JobHandler)
    }

    afterTest {
        unmockkAll()
    }

    "LongRunningJobsFinder" should {
        "delete long-running Config worker jobs" {
            testLongRunningJobsDetectionForEndpoint(ConfigEndpoint)
        }

        "delete long-running Analyzer worker jobs" {
            testLongRunningJobsDetectionForEndpoint(AnalyzerEndpoint)
        }

        "delete long-running Advisor worker jobs" {
            testLongRunningJobsDetectionForEndpoint(AdvisorEndpoint)
        }

        "delete long-running Scanner worker jobs" {
            testLongRunningJobsDetectionForEndpoint(ScannerEndpoint)
        }

        "delete long-running Evaluator worker jobs" {
            testLongRunningJobsDetectionForEndpoint(EvaluatorEndpoint)
        }

        "delete long-running Reporter worker jobs" {
            testLongRunningJobsDetectionForEndpoint(ReporterEndpoint)
        }

        "delete long-running Notifier worker jobs" {
            testLongRunningJobsDetectionForEndpoint(NotifierEndpoint)
        }
    }
})

/** The name of the job that runs into a timeout. */
private const val TIMEOUT_JOB_NAME = "longRunningJob"

/** The timeout configuration for the different worker types. */
private val testTimeoutConfig = TimeoutConfig(
    configTimeout = 1.minutes,
    analyzerTimeout = 120.minutes,
    advisorTimeout = 2.minutes,
    scannerTimeout = 24.hours,
    evaluatorTimeout = 5.minutes,
    reporterTimeout = 15.minutes,
    notifierTimeout = 11.minutes
)

/**
 * Determine the timeout for this [Endpoint]. This is explicitly done via a `when` construct to make sure that the
 * compiler complains when a new endpoint is added.
 */
private fun TimeoutConfig.forEndpoint(endpoint: Endpoint<*>): Duration =
    when (endpoint) {
        ConfigEndpoint -> configTimeout
        AnalyzerEndpoint -> analyzerTimeout
        AdvisorEndpoint -> advisorTimeout
        ScannerEndpoint -> scannerTimeout
        EvaluatorEndpoint -> evaluatorTimeout
        ReporterEndpoint -> reporterTimeout
        NotifierEndpoint -> notifierTimeout
        else -> fail("Unknown endpoint type.")
    }

private suspend fun testLongRunningJobsDetectionForEndpoint(endpoint: Endpoint<*>) {
    val config = mockk<MonitorConfig> {
        every { timeoutConfig } returns testTimeoutConfig
    }

    val timeout = testTimeoutConfig.forEndpoint(endpoint)
    val threshold = OffsetDateTime.of(2024, Month.AUGUST.value, 13, 10, 18, 11, 0, ZoneOffset.UTC)
    val timeHelper = mockk<TimeHelper> {
        every { before(any()) } returns OffsetDateTime.now()
        every { before(timeout) } returns threshold
    }

    val meta = V1ObjectMeta().apply { name = TIMEOUT_JOB_NAME }
    val timeoutJob = mockk<V1Job> {
        every { isTimeout(threshold) } returns true
        every { metadata } returns meta
    }
    val otherJobs = (1..5).map {
        mockk<V1Job> {
            every { isTimeout(threshold) } returns false
        }
    }

    val jobHandler = mockk<JobHandler> {
        Endpoint.entries().forEach {
            every { findJobsForWorker(it) } returns emptyList()
        }
        every { findJobsForWorker(endpoint) } returns (otherJobs + timeoutJob).shuffled()
        every { deleteJob(TIMEOUT_JOB_NAME) } just runs
    }

    val finder = LongRunningJobsFinderTask(jobHandler, config, timeHelper)
    finder.execute()

    verify {
        jobHandler.deleteJob(TIMEOUT_JOB_NAME)
    }
}
