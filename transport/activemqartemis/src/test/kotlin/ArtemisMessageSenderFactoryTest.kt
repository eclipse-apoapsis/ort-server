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

package org.eclipse.apoapsis.ortserver.transport.artemis

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

import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_QUEUE_NAME

class ArtemisMessageSenderFactoryTest : StringSpec({
    "Messages can be sent via the sender" {
        val config = startArtemisContainer("orchestrator", "sender")

        val payload = AnalyzerWorkerResult(42)
        val header = MessageHeader(traceId = "dick.tracy", 11)
        val message = Message(header, payload)

        val connectionFactory = JmsConnectionFactory(config.getString("orchestrator.sender.serverUri"))
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(TEST_QUEUE_NAME)
            val consumer = session.createConsumer(queue)

            val sender = MessageSenderFactory.createSender(OrchestratorEndpoint, config)
            sender.send(message)

            connection.start()
            val receivedMessage = consumer.receive(5000) as TextMessage
            receivedMessage.getStringProperty(TRACE_PROPERTY) shouldBe header.traceId
            receivedMessage.getLongProperty(RUN_ID_PROPERTY) shouldBe header.ortRunId

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
