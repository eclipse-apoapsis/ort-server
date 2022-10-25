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

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.testcontainers.TestContainerExtension
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

import org.ossreviewtoolkit.server.model.orchestrator.AnalyzeResult
import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

import org.slf4j.LoggerFactory

import org.testcontainers.containers.output.Slf4jLogConsumer

private const val ARTEMIS_CONTAINER = "quay.io/artemiscloud/activemq-artemis-broker:artemis.2.26.0"
private const val ARTEMIS_PORT = 61616

class ArtemisMessageSenderFactoryTest : StringSpec({
    "Messages can be sent via the sender" {
        val containerEnv = mapOf("AMQ_USER" to "admin", "AMQ_PASSWORD" to "admin")
        val artemisContainer = install(TestContainerExtension(ARTEMIS_CONTAINER)) {
            startupAttempts = 1
            withExposedPorts(ARTEMIS_PORT)
            withEnv(containerEnv)
            withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("artemis")))
        }

        val configMap = mapOf(
            "orchestrator.serverUri" to "amqp://${artemisContainer.host}:${artemisContainer.firstMappedPort}",
            "orchestrator.queueName" to "testQueue",
            "orchestrator.sender.type" to "activeMQ"
        )
        val config = ConfigFactory.parseMap(configMap)

        val payload = AnalyzeResult(42)
        val header = MessageHeader(token = "1234567890", traceId = "dick.tracy")
        val message = Message(header, payload)

        val connectionFactory = JmsConnectionFactory(config.getString("orchestrator.serverUri"))
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(config.getString("orchestrator.queueName"))
            val consumer = session.createConsumer(queue)

            val sender = MessageSenderFactory.createSender(OrchestratorEndpoint, config)
            sender.send(message)

            connection.start()
            val receivedMessage = consumer.receive(5000) as TextMessage
            receivedMessage.getStringProperty("token") shouldBe header.token
            receivedMessage.getStringProperty("traceId") shouldBe header.traceId

            val serializer = JsonSerializer.forType<OrchestratorMessage>()
            val payload2 = serializer.fromJson(receivedMessage.text)
            payload2 shouldBe payload
        }
    }

    "The connection is closed by the AutoClosable implementation" {
        val connection = mockk<Connection>()
        every { connection.close() } just runs
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
        val jmsFactory = mockk<JmsConnectionFactory>()
        val connection = mockk<Connection>()
        val session = mockk<Session>()

        every { jmsFactory.createConnection() } returns connection
        every { connection.createSession() } returns session
        every { session.createQueue(any()) } throws exception
        every { connection.close() } just runs

        val factory = ArtemisMessageSenderFactory()
        val thrownException = shouldThrow<IllegalStateException> {
            factory.createSenderWithConnection(AnalyzerEndpoint, jmsFactory, "someQueue")
        }

        thrownException shouldBe exception
        verify { connection.close() }
    }
})
