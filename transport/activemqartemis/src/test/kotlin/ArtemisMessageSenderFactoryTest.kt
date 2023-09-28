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

package org.ossreviewtoolkit.server.transport.artemis

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import jakarta.jms.Connection
import jakarta.jms.Session
import jakarta.jms.TextMessage

import org.apache.qpid.jms.JmsConnectionFactory

import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

class ArtemisMessageSenderFactoryTest : StringSpec({
    "Messages can be sent via the sender" {
        val config = startArtemisContainer("sender")

        val payload = AnalyzerWorkerResult(42)
        val header = MessageHeader(token = "1234567890", traceId = "dick.tracy", 11)
        val message = Message(header, payload)

        val connectionFactory = JmsConnectionFactory(config.getString("orchestrator.sender.serverUri"))
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(config.getString("orchestrator.sender.queueName"))
            val consumer = session.createConsumer(queue)

            val sender = MessageSenderFactory.createSender(OrchestratorEndpoint, config)
            sender.send(message)

            connection.start()
            val receivedMessage = consumer.receive(5000) as TextMessage
            receivedMessage.getStringProperty("token") shouldBe header.token
            receivedMessage.getStringProperty("traceId") shouldBe header.traceId
            receivedMessage.getLongProperty("runId") shouldBe header.ortRunId

            val serializer = JsonSerializer.forType<OrchestratorMessage>()
            val payload2 = serializer.fromJson(receivedMessage.text)
            payload2 shouldBe payload
        }
    }

    "The connection is closed by the AutoClosable implementation" {
        val connection = mockk<Connection> {
            every { close() } just runs
        }

        val sender = ArtemisMessageSender(
            connection = connection,
            session = mockk(),
            producer = mockk(),
            endpoint = AnalyzerEndpoint
        )

        sender.close()

        verify { connection.close() }
    }

    "The connection is closed if the sender cannot be created" {
        val exception = IllegalStateException("JMS exception")

        val session = mockk<Session> {
            every { createQueue(any()) } throws exception
        }

        val connection = mockk<Connection> {
            every { createSession() } returns session
            every { close() } just runs
        }

        val jmsFactory = mockk<JmsConnectionFactory> {
            every { createConnection() } returns connection
        }

        val factory = ArtemisMessageSenderFactory()
        val thrownException = shouldThrow<IllegalStateException> {
            factory.createSenderWithConnection(AnalyzerEndpoint, jmsFactory, "someQueue")
        }

        thrownException shouldBe exception
        verify { connection.close() }
    }
})
