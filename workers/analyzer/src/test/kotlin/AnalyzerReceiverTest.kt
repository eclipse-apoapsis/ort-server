/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.analyzer

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.testing.MessageReceiverFactoryForTesting
import org.ossreviewtoolkit.server.transport.testing.MessageSenderFactoryForTesting
import org.ossreviewtoolkit.server.transport.testing.TEST_TRANSPORT_NAME
import org.ossreviewtoolkit.server.workers.common.RunResult

private const val JOB_ID = 1L
private val analyzerRequest = AnalyzerRequest(
    analyzerJobId = JOB_ID
)

private const val TOKEN = "token"
private const val TRACE_ID = "42"
private val header = MessageHeader(token = TOKEN, traceId = TRACE_ID)

private interface Handler {
    fun handle(analyzerJobId: Long, traceId: String): RunResult
}

private val config = ConfigFactory.parseMap(
    mapOf(
        "${AnalyzerEndpoint.configPrefix}.${MessageReceiverFactory.RECEIVER_TYPE_PROPERTY}" to TEST_TRANSPORT_NAME,
        "${OrchestratorEndpoint.configPrefix}.${MessageSenderFactory.SENDER_TYPE_PROPERTY}" to TEST_TRANSPORT_NAME
    )
)

class AnalyzerReceiverTest : WordSpec({

    afterTest {
        MessageReceiverFactoryForTesting.reset()
    }

    "receiver" should {
        "call the handler with the correct job id" {
            val receiver = AnalyzerReceiver(config)
            val handler = createHandler(RunResult.Success)

            receiver.receive(handler::handle)

            MessageReceiverFactoryForTesting.receive(AnalyzerEndpoint, Message(header, analyzerRequest))

            verify(exactly = 1) {
                handler.handle(JOB_ID, any())
            }
        }

        "send a result message if the handler returns success" {
            val receiver = AnalyzerReceiver(config)
            val handler = createHandler(RunResult.Success)

            receiver.receive(handler::handle)

            MessageReceiverFactoryForTesting.receive(AnalyzerEndpoint, Message(header, analyzerRequest))

            val response = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
            response.header shouldBe header
            response.payload shouldBe AnalyzerWorkerResult(JOB_ID)
        }

        "send a failure message if the handler returns a failure" {
            val receiver = AnalyzerReceiver(config)
            val handler = createHandler(RunResult.Failed(Throwable()))

            receiver.receive(handler::handle)

            MessageReceiverFactoryForTesting.receive(AnalyzerEndpoint, Message(header, analyzerRequest))

            val response = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
            response.header shouldBe header
            response.payload shouldBe AnalyzerWorkerError(JOB_ID)
        }

        "send no message if the handler ignores the job" {
            val receiver = AnalyzerReceiver(config)
            val handler = createHandler(RunResult.Ignored)

            receiver.receive(handler::handle)

            MessageReceiverFactoryForTesting.receive(AnalyzerEndpoint, Message(header, analyzerRequest))

            shouldThrow<NoSuchElementException> {
                MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
            }
        }
    }
})

private fun createHandler(result: RunResult) = mockk<Handler> {
    every { handle(any(), any()) } returns result
}
