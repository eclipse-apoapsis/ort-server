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

package org.eclipse.apoapsis.ortserver.transport.rabbitmq

import com.rabbitmq.client.ConnectionFactory

import com.typesafe.config.ConfigFactory

import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.transport.rabbitmq.RabbitMqMessageConverter.toAmqpProperties

import org.testcontainers.containers.RabbitMQContainer

class RabbitMqMessageReceiverFactoryTest : StringSpec() {
    private val queueName = "TEST_QUEUE"
    private val username = "guest"
    private val password = "guest"

    private val rabbitMq = install(
        ContainerExtension(
            RabbitMQContainer("rabbitmq")
        )
    )

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        // Restart the test container after each test to remove all existing receivers.
        rabbitMq.stop()
        rabbitMq.start()
    }

    init {
        "Messages can be received via the RabbitMQ transport" {
            val serializer = JsonSerializer.forType<OrchestratorMessage>()
            val config = createConfig()

            val connectionFactory = ConnectionFactory().apply {
                setUri(config.getString("orchestrator.receiver.serverUri"))
            }
            connectionFactory.newConnection().use { connection ->
                val channel = connection.createChannel().also {
                    it.queueDeclare(
                        /* queue = */ queueName,
                        /* durable = */ false,
                        /* exclusive = */ false,
                        /* autoDelete = */ false,
                        /* arguments = */ emptyMap()
                    )
                }

                val messageQueue = startReceiver(config)

                val token1 = "token1"
                val traceId1 = "trace1"
                val runId1 = 1L
                val payload1 = AnalyzerWorkerError(1)
                val token2 = "token2"
                val traceId2 = "trace2"
                val runId2 = 2L
                val payload2 = AnalyzerWorkerResult(42)

                channel.basicPublish(
                    "",
                    queueName,
                    MessageHeader(token1, traceId1, runId1).toAmqpProperties(),
                    serializer.toJson(payload1).toByteArray()
                )

                channel.basicPublish(
                    "",
                    queueName,
                    MessageHeader(token2, traceId2, runId2).toAmqpProperties(),
                    serializer.toJson(payload2).toByteArray()
                )

                messageQueue.checkMessage(token1, traceId1, runId1, payload1)
                messageQueue.checkMessage(token2, traceId2, runId2, payload2)
            }
        }

        "Exceptions during message receiving are handled" {
            val serializer = JsonSerializer.forType<OrchestratorMessage>()
            val config = createConfig()

            val connectionFactory = ConnectionFactory().apply {
                setUri(config.getString("orchestrator.receiver.serverUri"))
            }
            connectionFactory.newConnection().use { connection ->
                val channel = connection.createChannel().also {
                    it.queueDeclare(
                        /* queue = */ queueName,
                        /* durable = */ false,
                        /* exclusive = */ false,
                        /* autoDelete = */ false,
                        /* arguments = */ emptyMap()
                    )
                }

                val messageQueue = startReceiver(config)

                channel.basicPublish(
                    "",
                    queueName,
                    MessageHeader("tokenInvalid", "traceIdInvalid", -1).toAmqpProperties(),
                    "Invalid payload".toByteArray()
                )

                val token = "validtoken"
                val traceId = "validtrace"
                val runId = 10L
                val payload = AnalyzerWorkerResult(42)
                channel.basicPublish(
                    "",
                    queueName,
                    MessageHeader(token, traceId, runId).toAmqpProperties(),
                    serializer.toJson(payload).toByteArray()
                )

                messageQueue.checkMessage(token, traceId, runId, payload)
            }
        }
    }

    private fun createConfig(): ConfigManager {
        val secretsMap = mapOf(
            "rabbitMqUser" to username,
            "rabbitMqPassword" to password
        )
        val configProvidersMap = mapOf(
            ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
            ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretsMap
        )
        val configMap = mapOf(
            "orchestrator.receiver.serverUri" to "amqp://${rabbitMq.host}:${rabbitMq.firstMappedPort}",
            "orchestrator.receiver.queueName" to queueName,
            "orchestrator.receiver.type" to "rabbitMQ",
            ConfigManager.CONFIG_MANAGER_SECTION to configProvidersMap
        )

        return ConfigManager.create(ConfigFactory.parseMap(configMap))
    }

    private fun startReceiver(configManager: ConfigManager): LinkedBlockingQueue<Message<OrchestratorMessage>> {
        val queue = LinkedBlockingQueue<Message<OrchestratorMessage>>()

        fun handler(message: Message<OrchestratorMessage>) {
            queue.offer(message)
        }

        Thread {
            MessageReceiverFactory.createReceiver(OrchestratorEndpoint, configManager, ::handler)
        }.start()

        return queue
    }

    /**
     * Check that the next message in this queue has the given [token], [traceId], [runId], and [payload].
     */
    private fun <T> BlockingQueue<Message<T>>.checkMessage(token: String, traceId: String, runId: Long, payload: T) {
        val message = poll(5, TimeUnit.SECONDS)

        message.shouldNotBeNull()
        message.header.token shouldBe token
        message.header.traceId shouldBe traceId
        message.header.ortRunId shouldBe runId
        message.payload shouldBe payload
    }
}
