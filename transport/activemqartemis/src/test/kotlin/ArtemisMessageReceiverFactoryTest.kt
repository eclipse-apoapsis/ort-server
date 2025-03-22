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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should

import jakarta.jms.Session
import jakarta.jms.TextMessage

import java.util.concurrent.TimeUnit

import org.apache.qpid.jms.JmsConnectionFactory

import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_QUEUE_NAME
import org.eclipse.apoapsis.ortserver.transport.testing.checkMessage
import org.eclipse.apoapsis.ortserver.transport.testing.startReceiver

class ArtemisMessageReceiverFactoryTest : StringSpec({
    "Messages can be received via the Artemis transport" {
        val serializer = JsonSerializer.forType<OrchestratorMessage>()
        val config = startArtemisContainer("orchestrator", "receiver")
        val messageQueue = startReceiver(config)

        val connectionFactory = JmsConnectionFactory(config.getString("orchestrator.receiver.serverUri"))
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(TEST_QUEUE_NAME)
            val producer = session.createProducer(queue)

            val traceId1 = "trace1"
            val runId1 = 1L
            val payload1 = AnalyzerWorkerError(21)
            val traceId2 = "trace2"
            val runId2 = 2L
            val payload2 = AnalyzerWorkerResult(42)

            producer.send(serializer.createMessage(session, traceId1, runId1, payload1))
            producer.send(serializer.createMessage(session, traceId2, runId2, payload2))

            messageQueue.checkMessage(traceId1, runId1, payload1)
            messageQueue.checkMessage(traceId2, runId2, payload2)
        }
    }

    "Exceptions during message receiving are handled" {
        val serializer = JsonSerializer.forType<OrchestratorMessage>()
        val config = startArtemisContainer("orchestrator", "receiver")
        val messageQueue = startReceiver(config)

        val connectionFactory = JmsConnectionFactory(config.getString("orchestrator.receiver.serverUri"))
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(TEST_QUEUE_NAME)
            val producer = session.createProducer(queue)

            // Send an invalid message which cannot be converted.
            val jmsMessage = session.createTextMessage("Not a valid Orchestrator message.")
            producer.send(jmsMessage)

            val traceId = "trace"
            val runId = 3L
            val payload = AnalyzerWorkerResult(42)
            producer.send(serializer.createMessage(session, traceId, runId, payload))

            messageQueue.checkMessage(traceId, runId, payload)
        }
    }

    "Message receiving is stopped when the handler returns STOP" {
        val serializer = JsonSerializer.forType<OrchestratorMessage>()
        val config = startArtemisContainer("orchestrator", "receiver")
        val messageQueue = startReceiver(config, EndpointHandlerResult.STOP)

        val connectionFactory = JmsConnectionFactory(config.getString("orchestrator.receiver.serverUri"))
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(TEST_QUEUE_NAME)
            val producer = session.createProducer(queue)

            val traceId1 = "trace1"
            val runId1 = 1L
            val payload1 = AnalyzerWorkerError(21)
            val traceId2 = "trace2"
            val runId2 = 2L
            val payload2 = AnalyzerWorkerResult(42)

            producer.send(serializer.createMessage(session, traceId1, runId1, payload1))
            producer.send(serializer.createMessage(session, traceId2, runId2, payload2))

            messageQueue.checkMessage(traceId1, runId1, payload1)
            messageQueue.poll(2, TimeUnit.SECONDS) should beNull()
        }
    }
})

/**
 * Create a JMS messaging using this serializer and the given [session] with the provided [traceId], [runId]
 * and [payload].
 */
private fun <T> JsonSerializer<T>.createMessage(
    session: Session,
    traceId: String,
    runId: Long,
    payload: T
): TextMessage =
    session.createTextMessage(toJson(payload)).apply {
        setStringProperty(TRACE_PROPERTY, traceId)
        setLongProperty(RUN_ID_PROPERTY, runId)
    }
