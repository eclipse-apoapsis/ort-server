/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1ObjectMeta

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.model.ActiveOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.LostSchedule
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

class FailedJobNotifierTest : WordSpec({
    "sendFailedJobNotification" should {
        "send a failure message to the Orchestrator" {
            val sender = mockk<MessageSender<OrchestratorMessage>>()
            every { sender.send(any()) } just runs

            val meta = V1ObjectMeta().apply {
                name = "analyzer-plus-some-suffix"
                labels = mapOf(
                    "trace-id-0" to "trace1_",
                    "trace-id-1" to "trace2_",
                    "trace-id-2" to "trace3",
                    "run-id" to "1234"
                )
            }

            val job = V1Job().apply {
                metadata = meta
            }

            val notifier = FailedJobNotifier(sender)
            notifier.sendFailedJobNotification(job)

            val slot = slot<Message<OrchestratorMessage>>()
            verify {
                sender.send(capture(slot))
            }

            with(slot.captured) {
                header.traceId shouldBe "trace1_trace2_trace3"
                header.ortRunId shouldBe 1234
                payload shouldBe WorkerError("analyzer")
            }
        }

        "ignore a job without a name in metadata" {
            val sender = mockk<MessageSender<OrchestratorMessage>>()

            val meta = V1ObjectMeta().apply {
                labels = mapOf("trace-id-0" to "someTraceId")
            }

            val job = V1Job().apply {
                metadata = meta
            }

            val notifier = FailedJobNotifier(sender)
            notifier.sendFailedJobNotification(job)

            verify(exactly = 0) {
                sender.send(any())
            }
        }

        "ignore a job without a trace ID in metadata" {
            val sender = mockk<MessageSender<OrchestratorMessage>>()

            val meta = V1ObjectMeta().apply {
                name = "advisor-someFailedJob"
                labels = mapOf("run-id" to "1")
            }

            val job = V1Job().apply {
                metadata = meta
            }

            val notifier = FailedJobNotifier(sender)
            notifier.sendFailedJobNotification(job)

            verify(exactly = 0) {
                sender.send(any())
            }
        }

        "ignore a job without an ORT run ID in metadata" {
            val sender = mockk<MessageSender<OrchestratorMessage>>()

            val meta = V1ObjectMeta().apply {
                name = "advisor-someFailedJob"
                labels = mapOf("trace-id-0" to "someTraceId")
            }

            val job = V1Job().apply {
                metadata = meta
            }

            val notifier = FailedJobNotifier(sender)
            notifier.sendFailedJobNotification(job)

            verify(exactly = 0) {
                sender.send(any())
            }
        }
    }

    "sendLostJobNotification" should {
        "send a notification about a lost job" {
            val ortRunId = 20240315155611L
            val sender = mockk<MessageSender<OrchestratorMessage>>()
            every { sender.send(any()) } just runs

            val notifier = FailedJobNotifier(sender)
            notifier.sendLostJobNotification(ortRunId, ScannerEndpoint)

            val slot = slot<Message<OrchestratorMessage>>()
            verify {
                sender.send(capture(slot))
            }

            with(slot.captured) {
                header.ortRunId shouldBe ortRunId
                payload shouldBe WorkerError("scanner")
            }
        }
    }

    "sendLostScheduleNotification" should {
        "send a notification about a lost schedule" {
            val ortRun = ActiveOrtRun(20241211084817L, Clock.System.now(), "someTraceId")
            val sender = mockk<MessageSender<OrchestratorMessage>>()
            every { sender.send(any()) } just runs

            val notifier = FailedJobNotifier(sender)
            notifier.sendLostScheduleNotification(ortRun)

            val slot = slot<Message<OrchestratorMessage>>()
            verify {
                sender.send(capture(slot))
            }

            with(slot.captured) {
                header.ortRunId shouldBe ortRun.runId
                header.traceId shouldBe ortRun.traceId
                payload shouldBe LostSchedule(ortRun.runId)
            }
        }
    }
})
