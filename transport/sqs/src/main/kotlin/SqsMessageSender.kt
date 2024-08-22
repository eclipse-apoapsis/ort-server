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
import aws.sdk.kotlin.services.sqs.model.MessageAttributeValue
import aws.sdk.kotlin.services.sqs.model.SendMessageRequest

import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

/**
 * A [MessageSender] for SQS messages that encodes the [MessageHeader] values via [MessageAttributeValue] and serializes
 * the payload a JSON string.
 */
class SqsMessageSender<T : Any>(
    private val client: SqsClient,
    private val queueUrl: String,
    to: Endpoint<T>
) : MessageSender<T>, AutoCloseable by client {
    private val serializer = JsonSerializer.forClass(to.messageClass)

    override fun send(message: Message<T>) {
        val request = SendMessageRequest {
            queueUrl = this@SqsMessageSender.queueUrl
            messageAttributes = message.header.toMessageAttributes()
            messageBody = serializer.toJson(message.payload)
        }

        runBlocking { client.sendMessage(request) }
    }
}
