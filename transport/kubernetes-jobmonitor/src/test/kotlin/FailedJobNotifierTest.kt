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

package org.eclipse.apoapsis.ortserver.transport.kubernetes.jobmonitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1ObjectMeta

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageSender

class FailedJobNotifierTest : StringSpec({
    "A message about a failed job is sent to the Orchestrator" {
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

    "A job without a name in metadata is ignored" {
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

    "A job without a trace ID in metadata is ignored" {
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

    "A job without an ORT run ID in metadata is ignored" {
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
})
