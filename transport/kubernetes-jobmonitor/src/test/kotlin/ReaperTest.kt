/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.models.V1Job

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.IOException
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor.JobHandler.Companion.isFailed

/** Constant for the maximum age of a job before it gets deleted. */
private val maxJobAge = 10.minutes

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
            val clock = mockk<Clock>()
            every { clock.now() } returnsMany listOf(
                Instant.parse("2023-05-03T10:18:11Z"),
                Instant.parse("2023-05-03T10:21:15Z")
            )

            val jobHandler = mockk<JobHandler>()
            every { jobHandler.findJobsCompletedBefore(any()) } returns emptyList()

            val reaper = Reaper(jobHandler, mockk(), maxJobAge, clock, zoneOffset)
            reaper.run(tickFlow(2))

            verify {
                jobHandler.findJobsCompletedBefore(
                    OffsetDateTime.of(
                        2023,
                        Month.MAY.value,
                        3,
                        12,
                        8,
                        11,
                        0,
                        zoneOffset
                    )
                )
                jobHandler.findJobsCompletedBefore(
                    OffsetDateTime.of(
                        2023,
                        Month.MAY.value,
                        3,
                        12,
                        11,
                        15,
                        0,
                        zoneOffset
                    )
                )
            }
        }

        "remove completed jobs that have reached the maximum age" {
            val jobs = (1..8).map { createJob() }
            val jobHandler = mockk<JobHandler>()
            every { jobHandler.findJobsCompletedBefore(any()) } returns jobs
            jobs.forEach {
                every { jobHandler.deleteJob(it) } just runs
            }

            val reaper = Reaper(jobHandler, mockk(), maxJobAge)
            reaper.run(tickFlow(1))

            verify {
                jobs.forAll(jobHandler::deleteJob)
            }
        }

        "send notifications about failed jobs" {
            val failedJob = createJob(failed = true)
            val jobs = listOf(failedJob, createJob())
            val jobHandler = mockk<JobHandler>()
            every { jobHandler.findJobsCompletedBefore(any()) } returns jobs
            jobs.forEach {
                every { jobHandler.deleteJob(it) } just runs
            }

            val notifier = mockk<FailedJobNotifier>()
            every { notifier.sendFailedJobNotification(failedJob) } just runs

            val reaper = Reaper(jobHandler, notifier, maxJobAge)
            reaper.run(tickFlow(1))

            verify {
                jobs.forAll(jobHandler::deleteJob)

                notifier.sendFailedJobNotification(failedJob)
            }
        }

        "handle failures when sending notifications" {
            val job1 = createJob()
            val job2 = createJob()
            val failedJob = createJob(failed = true)
            val jobs = listOf(job1, failedJob, job2)
            val jobHandler = mockk<JobHandler>()
            every { jobHandler.findJobsCompletedBefore(any()) } returns jobs
            jobs.forEach {
                every { jobHandler.deleteJob(it) } just runs
            }

            val notifier = mockk<FailedJobNotifier>()
            every { notifier.sendFailedJobNotification(failedJob) } throws IOException("Test exception")

            val reaper = Reaper(jobHandler, notifier, maxJobAge)
            reaper.run(tickFlow(1))

            verify {
                jobHandler.deleteJob(job2)
            }

            verify(exactly = 0) {
                jobHandler.deleteJob(failedJob)
            }
        }
    }

    "tickerFlow" should {
        "produce a flow with tick events" {
            val ticker = tickerFlow(10.milliseconds)
            val queue = LinkedBlockingQueue<String>()

            CoroutineScope(Dispatchers.IO).launch {
                ticker.take(10)
                    .map { "tick" }
                    .onEach(queue::offer)
                    .collect()
            }

            (1..10).toList().forAll {
                queue.poll(100, TimeUnit.MILLISECONDS) shouldBe "tick"
            }
        }

        "take the interval into account" {
            val ticker = tickerFlow(100.milliseconds)
            val latch = CountDownLatch(1)

            CoroutineScope(Dispatchers.IO).launch {
                ticker.onEach { latch.countDown() }.first()
            }

            latch.await(10, TimeUnit.MILLISECONDS) shouldBe false
        }
    }
})

/**
 * Generate a flow simulating ticks with the given [number of elements][count].
 */
private fun tickFlow(count: Int): Flow<Unit> =
    (1..count).asFlow().map { }

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
