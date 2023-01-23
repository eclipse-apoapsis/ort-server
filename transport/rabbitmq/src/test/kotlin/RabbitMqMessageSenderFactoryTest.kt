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

package org.ossreviewtoolkit.server.transport.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Delivery

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.testcontainers.TestContainerExtension
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify

import java.lang.IllegalStateException

import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

import org.testcontainers.containers.RabbitMQContainer

class RabbitMqMessageSenderFactoryTest : StringSpec() {
    private val queueName = "TEST_QUEUE"

    private val rabbitMq = install(
        TestContainerExtension(
            RabbitMQContainer("rabbitmq")
        )
    )

    init {
        "Messages can be sent via the sender" {
            val config = createConfig()

            val payload = AnalyzerWorkerResult(42)
            val header = MessageHeader(token = "1234567890", traceId = "dick.tracy")
            val message = Message(header, payload)

            val connectionFactory = ConnectionFactory().apply {
                setUri("amqp://${rabbitMq.host}:${rabbitMq.amqpPort}")
            }

            connectionFactory.newConnection().use { connection ->
                val channel = spyk<Channel>(
                    connection.createChannel().also {
                        it.queueDeclare(
                            /* queue = */ queueName,
                            /* durable = */ false,
                            /* exclusive = */ false,
                            /* autoDelete = */ false,
                            /* arguments = */ emptyMap()
                        )
                    }
                )

                val sender = MessageSenderFactory.createSender(OrchestratorEndpoint, config)
                sender.send(message)

                channel.basicConsume(
                    queueName,
                    true,
                    { _: String, delivery: Delivery ->
                        channel.messageCount(queueName) shouldBe 1

                        val receivedMessage = String(delivery.body)

                        delivery.properties.headers["token"] shouldBe header.token
                        delivery.properties.headers["traceId"] shouldBe header.traceId

                        val serializer = JsonSerializer.forType<OrchestratorMessage>()
                        val receivedPayload = serializer.fromJson(receivedMessage)

                        receivedPayload shouldBe payload
                    },
                    { _: String -> true shouldBe false }
                )

                // Ensure that the callback was called.
                verify { channel.messageCount(queueName) }
            }
        }

        "The channel is closed by the AutoClosable implementation" {
            val channel = mockk<Channel> {
                every { close() } just runs
            }

            val sender = RabbitMqMessageSender(
                channel = channel,
                queueName = queueName,
                endpoint = AnalyzerEndpoint
            )

            sender.close()

            verify { channel.close() }
        }

        "The connection is closed if the sender cannot be created" {
            val exceptionMessage = "RabbitMQ exception"
            val exception = IllegalStateException(exceptionMessage)

            val connection = mockk<Connection> {
                every { createChannel() } throws exception
                every { close() } just runs
            }

            val connectionFactory = mockk<ConnectionFactory> {
                every { newConnection() } returns connection
            }

            val factory = RabbitMqMessageSenderFactory()
            shouldThrowWithMessage<IllegalStateException>(exceptionMessage) {
                factory.createSenderWithConnection(AnalyzerEndpoint, connectionFactory, queueName)
            }

            verify { connection.close() }
        }
    }

    private fun createConfig(): Config {
        val configMap = mapOf(
            "orchestrator.sender.serverUri" to "amqp://${rabbitMq.host}:${rabbitMq.firstMappedPort}",
            "orchestrator.sender.queueName" to queueName,
            "orchestrator.sender.type" to "rabbitMQ"
        )

        return ConfigFactory.parseMap(configMap)
    }
}
