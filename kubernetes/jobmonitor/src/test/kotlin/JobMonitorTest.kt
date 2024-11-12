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

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.util.Watch

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor.JobHandler.Companion.isFailed

class JobMonitorTest : StringSpec({
    beforeSpec {
        mockkObject(JobHandler)
    }

    afterSpec {
        unmockkAll()
    }

    "Failed jobs are removed" {
        val job = createJob(failed = true)

        MonitorTestHelper().use { helper ->
            helper.sendJobEvent(job)
                .expectJobRemoved(job)
        }
    }

    "Successful jobs are ignored" {
        val successfulJob = createJob(failed = false)
        val failedJob = createJob(failed = true)

        MonitorTestHelper().use { helper ->
            helper.sendJobEvent(successfulJob)
                .sendJobEvent(failedJob)
                .expectJobRemoved(failedJob)
        }
    }
})

/**
 * A helper class managing a [JobMonitor] test instance and its dependencies. To test the watching loop, it runs in a
 * separate thread. Queues are used to interact with the object under test.
 */
private class MonitorTestHelper : AutoCloseable {
    /** A queue for passing watch events to the watch helper. */
    private val eventQueue = LinkedBlockingQueue<Watch.Response<V1Job>>()

    /** A queue to retrieve jobs removed by the job handler. */
    private val jobRemoveQueue = LinkedBlockingQueue<V1Job>()

    /** Mock for retrieving watch events. */
    private val watchHelperMock = createWatchHelperMock()

    /** Mock for manipulating jobs. */
    private val handlerMock = createHandlerMock()

    /** The thread running the test loop. */
    private val looper = createTestLoop()

    override fun close() {
        looper.interrupt()
    }

    /**
     * Pass the given [job] to the watch helper mock, so that it is returned as next event.
     */
    fun sendJobEvent(job: V1Job): MonitorTestHelper {
        eventQueue.offer(Watch.Response("MODIFIED", job))
        return this
    }

    /**
     * Expect that the given [job] has been passed to the [JobHandler] for removal.
     */
    fun expectJobRemoved(job: V1Job): MonitorTestHelper {
        jobRemoveQueue.next() shouldBe job
        return this
    }

    /**
     * Create a mock for the [JobWatchHelper].
     */
    private fun createWatchHelperMock(): JobWatchHelper {
        val helper = mockk<JobWatchHelper>()
        every { helper.nextEvent() } answers { eventQueue.take() }

        return helper
    }

    /**
     * Create a mock for the [JobHandler].
     */
    private fun createHandlerMock(): JobHandler {
        val handler = mockk<JobHandler>()
        coEvery { handler.deleteAndNotifyIfFailed(any<V1Job>()) } answers {
            jobRemoveQueue.offer(firstArg())
        }

        return handler
    }

    /**
     * Create the thread that runs the watch loop with the test object.
     */
    private fun createTestLoop(): Thread {
        val monitor = JobMonitor(watchHelperMock, handlerMock)

        return Thread {
            try {
                runBlocking { monitor.watch() }
            } catch (_: InterruptedException) {
                // The thread is interrupted to gracefully exit the watch loop.
            }
        }.also { it.start() }
    }
}

/** Timeout for assertions on blocking queues. */
private const val TIMEOUT = 500L

/**
 * Retrieve the next element from this queue, asserting that it exists.
 */
private fun <T> BlockingQueue<T>.next(): T =
    poll(TIMEOUT, TimeUnit.MILLISECONDS).shouldNotBeNull()

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
