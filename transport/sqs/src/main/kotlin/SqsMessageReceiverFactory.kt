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

import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.GetQueueUrlRequest
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest

import kotlin.coroutines.coroutineContext

import kotlinx.coroutines.isActive

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SqsMessageReceiverFactory::class.java)

internal val messageAttributeNames = listOf(TRACE_PROPERTY, RUN_ID_PROPERTY)

/**
 * A [MessageReceiverFactory] implementation for AWS SQS.
 * Also see https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/kotlin_sqs_code_examples.html.
 */
class SqsMessageReceiverFactory : MessageReceiverFactory {
    override val name: String = SqsConfig.TRANSPORT_NAME

    override suspend fun <T : Any> createReceiver(
        from: Endpoint<T>,
        configManager: ConfigManager,
        handler: EndpointHandler<T>
    ) {
        logger.info("Creating SQS receiver for endpoint '${from.configPrefix}'.")

        val serializer = JsonSerializer.forClass(from.messageClass)
        val config = SqsConfig.create(configManager)
        val client = createSqsClient(config)

        val queueResponse = client.getQueueUrl(GetQueueUrlRequest { queueName = config.queueName })

        val queueUrl = checkNotNull(queueResponse.queueUrl) {
            "The SQS queue URL for queue '$name' must not be null."
        }

        val receiveRequest = ReceiveMessageRequest {
            this.queueUrl = queueUrl

            // Opt-in to the message attributes to receive.
            messageAttributeNames = org.eclipse.apoapsis.ortserver.transport.sqs.messageAttributeNames

            // Repeating the default value here just for visibility. Valid values: 1 to 10.
            maxNumberOfMessages = 1

            // Enable "long polling" to eliminate empty responses. Valid values: 1 to 20. See
            // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/working-with-messages.html#setting-up-long-polling
            waitTimeSeconds = 20
        }

        loop@ while (coroutineContext.isActive) {
            val receiveResponse = client.receiveMessage(receiveRequest)

            receiveResponse.messages?.forEach { sqsMessage ->
                // Delete the message from the queue before it is being processed to not receive it again if an
                // exception is thrown during processing.
                val deleteRequest = DeleteMessageRequest {
                    this.queueUrl = queueUrl
                    receiptHandle = sqsMessage.receiptHandle
                }

                client.deleteMessage(deleteRequest)

                val attrs = checkNotNull(sqsMessage.messageAttributes) {
                    "The message attributes must not be null."
                }

                val body = checkNotNull(sqsMessage.body) {
                    "The message body must not be null."
                }

                runCatching {
                    val ortMessage = Message(attrs.toMessageHeader(), serializer.fromJson(body))

                    withMdcContext(
                        "traceId" to ortMessage.header.traceId,
                        "ortRunId" to ortMessage.header.ortRunId.toString()
                    ) {
                        handler(ortMessage)
                    }
                }.onSuccess {
                    if (it == EndpointHandlerResult.STOP) break@loop
                }.onFailure {
                    logger.error("Error during message body processing.", it)
                }
            }
        }
    }
}
