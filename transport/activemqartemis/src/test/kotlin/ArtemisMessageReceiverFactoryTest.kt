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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import jakarta.jms.Session
import jakarta.jms.TextMessage

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import org.apache.qpid.jms.JmsConnectionFactory

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

class ArtemisMessageReceiverFactoryTest : StringSpec({
    "Messages can be received via the Artemis transport" {
        val serializer = JsonSerializer.forType<OrchestratorMessage>()
        val config = startArtemisContainer("receiver")
        val messageQueue = startReceiver(config)

        val connectionFactory = JmsConnectionFactory(config.getString("orchestrator.receiver.serverUri"))
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(config.getString("orchestrator.receiver.queueName"))
            val producer = session.createProducer(queue)

            val token1 = "token1"
            val traceId1 = "trace1"
            val payload1 = AnalyzerWorkerError(21)
            val token2 = "token2"
            val traceId2 = "trace2"
            val payload2 = AnalyzerWorkerResult(42)

            producer.send(serializer.createMessage(session, token1, traceId1, payload1))
            producer.send(serializer.createMessage(session, token2, traceId2, payload2))

            messageQueue.checkMessage(token1, traceId1, payload1)
            messageQueue.checkMessage(token2, traceId2, payload2)
        }
    }

    "Exceptions during message receiving are handled" {
        val serializer = JsonSerializer.forType<OrchestratorMessage>()
        val config = startArtemisContainer("receiver")
        val messageQueue = startReceiver(config)

        val connectionFactory = JmsConnectionFactory(config.getString("orchestrator.receiver.serverUri"))
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(config.getString("orchestrator.receiver.queueName"))
            val producer = session.createProducer(queue)

            // Send an invalid message which cannot be converted.
            val jmsMessage = session.createTextMessage("Not a valid Orchestrator message.")
            producer.send(jmsMessage)

            val token = "token"
            val traceId = "trace"
            val payload = AnalyzerWorkerResult(42)
            producer.send(serializer.createMessage(session, token, traceId, payload))

            messageQueue.checkMessage(token, traceId, payload)
        }
    }
})

/**
 * Start a receiver that is initialized from the given [configManager]. Since the receiver blocks, this has to be done
 * in a separate thread. Return a queue that can be polled to obtain the received messages.
 */
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
 * Create a JMS messaging using this serializer and the given [session] with the provided [token], [traceId], and
 * [payload].
 */
private fun <T> JsonSerializer<T>.createMessage(
    session: Session,
    token: String,
    traceId: String,
    payload: T
): TextMessage =
    session.createTextMessage(toJson(payload)).apply {
        setStringProperty("token", token)
        setStringProperty("traceId", traceId)
    }

/**
 * Check that the next message in this queue has the given [token], [traceId], and [payload].
 */
private fun <T> BlockingQueue<Message<T>>.checkMessage(token: String, traceId: String, payload: T) {
    val message = poll(5, TimeUnit.SECONDS)

    message.shouldNotBeNull()
    message.header.token shouldBe token
    message.header.traceId shouldBe traceId
    message.payload shouldBe payload
}
