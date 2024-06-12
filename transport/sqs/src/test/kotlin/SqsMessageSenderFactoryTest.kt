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

import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.CreateQueueRequest
import aws.sdk.kotlin.services.sqs.model.GetQueueUrlRequest
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import aws.smithy.kotlin.runtime.io.IOException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_QUEUE_NAME

class SqsMessageSenderFactoryTest : StringSpec({
    val consumerName = "orchestrator"
    val transportType = "sender"

    val configManager = createSqsConfigManager(consumerName, transportType)
    val client = createSqsClient(SqsConfig.create(configManager.subConfig(Path("$consumerName.$transportType"))))

    val serializer = JsonSerializer.forType<OrchestratorMessage>()

    afterEach {
        // To unmock the static createSqsClient() even in case of exceptions.
        unmockkAll()
    }

    "Messages can be sent via the sender" {
        val header = MessageHeader("traceId", 47)
        val payload = AnalyzerWorkerResult(11)
        val message = Message(header, payload)

        client.createQueue(CreateQueueRequest { queueName = TEST_QUEUE_NAME })

        val sender = MessageSenderFactory.createSender(OrchestratorEndpoint, configManager)
        sender.send(message)

        val queueResponse = client.getQueueUrl(GetQueueUrlRequest { queueName = TEST_QUEUE_NAME })

        val receiveRequest = ReceiveMessageRequest {
            queueUrl = queueResponse.queueUrl

            // Opt-in to the message attributes to receive.
            messageAttributeNames = org.eclipse.apoapsis.ortserver.transport.sqs.messageAttributeNames

            // Enable "long polling" to eliminate empty responses, see
            // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/working-with-messages.html#setting-up-long-polling
            waitTimeSeconds = 1
        }

        val receiveResponse = client.receiveMessage(receiveRequest)

        receiveResponse.messages shouldNotBeNull {
            shouldHaveSize(1)

            first().messageAttributes shouldNotBeNull {
                toMessageHeader() shouldBe header
            }

            first().body shouldNotBeNull {
                serializer.fromJson(this) shouldBe payload
            }
        }
    }

    "The client is closed by the AutoClosable implementation" {
        val mockClient = mockk<SqsClient> {
            every { close() } just runs
        }

        val sender = SqsMessageSender(
            client = mockClient,
            queueUrl = "",
            to = AnalyzerEndpoint
        )

        sender.close()

        verify { mockClient.close() }
    }

    "The client is closed if the sender cannot be created" {
        val mockClient = spyk(client) {
            coEvery { getQueueUrl(any()) } throws IOException()
        }

        mockkStatic(::createSqsClient)
        every { createSqsClient(any()) } returns mockClient

        client.createQueue(CreateQueueRequest { queueName = TEST_QUEUE_NAME })

        shouldThrow<IOException> {
            MessageSenderFactory.createSender(OrchestratorEndpoint, configManager)
        }

        verify { mockClient.close() }
    }
})
