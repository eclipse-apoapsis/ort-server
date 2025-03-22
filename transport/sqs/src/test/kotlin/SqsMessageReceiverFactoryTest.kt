/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.sqs

import aws.sdk.kotlin.services.sqs.model.CreateQueueRequest
import aws.sdk.kotlin.services.sqs.model.DeleteQueueRequest
import aws.sdk.kotlin.services.sqs.model.GetQueueUrlRequest
import aws.sdk.kotlin.services.sqs.model.GetQueueUrlResponse
import aws.sdk.kotlin.services.sqs.model.SendMessageRequest

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should

import java.util.concurrent.TimeUnit

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_QUEUE_NAME
import org.eclipse.apoapsis.ortserver.transport.testing.checkMessage
import org.eclipse.apoapsis.ortserver.transport.testing.startReceiver

class SqsMessageReceiverFactoryTest : StringSpec({
    val consumerName = "orchestrator"
    val transportType = "receiver"

    val configManager = createSqsConfigManager(consumerName, transportType)
    val client = createSqsClient(SqsConfig.create(configManager.subConfig(Path("$consumerName.$transportType"))))

    val serializer = JsonSerializer.forType<OrchestratorMessage>()

    lateinit var queueResponse: GetQueueUrlResponse

    suspend fun GetQueueUrlResponse.sendMessage(traceId: String, ortRunId: Long, payload: String) {
        val request = SendMessageRequest {
            queueUrl = this@sendMessage.queueUrl
            messageAttributes = MessageHeader(traceId, ortRunId).toMessageAttributes()
            messageBody = payload
        }

        client.sendMessage(request)
    }

    beforeTest {
        client.createQueue(CreateQueueRequest { queueName = TEST_QUEUE_NAME })
        queueResponse = client.getQueueUrl(GetQueueUrlRequest { queueName = TEST_QUEUE_NAME })
    }

    afterTest {
        client.deleteQueue(DeleteQueueRequest { this.queueUrl = queueResponse.queueUrl })
    }

    "Messages can be received via the SQS transport" {
        val messageQueue = startReceiver(configManager)

        val traceId1 = "trace1"
        val ortRunId1 = 1L
        val payload1 = AnalyzerWorkerError(1)

        val traceId2 = "trace2"
        val ortRunId2 = 2L
        val payload2 = AnalyzerWorkerError(2)

        queueResponse.sendMessage(traceId1, ortRunId1, serializer.toJson(payload1))
        queueResponse.sendMessage(traceId2, ortRunId2, serializer.toJson(payload2))

        messageQueue.checkMessage(traceId1, ortRunId1, payload1)
        messageQueue.checkMessage(traceId2, ortRunId2, payload2)
    }

    "Exceptions during message receiving are handled" {
        val messageQueue = startReceiver(configManager)

        val traceId = "validTraceId"
        val ortRunId = 10L
        val payload = AnalyzerWorkerResult(42)

        queueResponse.sendMessage("invalidTraceId", -1, "Invalid payload")
        queueResponse.sendMessage(traceId, ortRunId, serializer.toJson(payload))

        messageQueue.checkMessage(traceId, ortRunId, payload)
    }

    "Messages can have empty trace IDs" {
        val messageQueue = startReceiver(configManager)

        val traceId = EMPTY_VALUE
        val ortRunId = 10L
        val payload = AnalyzerWorkerResult(42)

        queueResponse.sendMessage(traceId, ortRunId, serializer.toJson(payload))

        messageQueue.checkMessage("", ortRunId, payload)
    }

    "Message receiving is stopped when the handler returns STOP" {
        val messageQueue = startReceiver(configManager, EndpointHandlerResult.STOP)

        val traceId1 = "trace1"
        val ortRunId1 = 1L
        val payload1 = AnalyzerWorkerError(1)

        val traceId2 = "trace2"
        val ortRunId2 = 2L
        val payload2 = AnalyzerWorkerError(2)

        queueResponse.sendMessage(traceId1, ortRunId1, serializer.toJson(payload1))
        queueResponse.sendMessage(traceId2, ortRunId2, serializer.toJson(payload2))

        messageQueue.checkMessage(traceId1, ortRunId1, payload1)
        messageQueue.poll(2, TimeUnit.SECONDS) should beNull()
    }
})
