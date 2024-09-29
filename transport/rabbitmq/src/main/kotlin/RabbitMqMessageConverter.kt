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

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Delivery

import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer

internal object RabbitMqMessageConverter {
    /**
     * Convert the [message headers][this] to the AMQP compatible [BasicProperties].
     */
    fun MessageHeader.toAmqpProperties(): BasicProperties = BasicProperties.Builder()
        .contentType("application/json")
        .contentEncoding("UTF-8")
        .correlationId(traceId) // TODO: Make separate PR!
        .headers(
            mapOf(
                TRACE_PROPERTY to traceId,
                RUN_ID_PROPERTY to ortRunId
            )
        ).build()

    /**
     * Convert the [message]'s payload into a plain JSON string.
     */
    fun <T> toStringMessage(message: Message<T>, serializer: JsonSerializer<T>) = serializer.toJson(message.payload)

    /**
     * Convert the [delivery] to a [Message] using the [serializer].
     */
    fun <T> toTransportMessage(delivery: Delivery, serializer: JsonSerializer<T>): Message<T> {
        val header = MessageHeader(
            delivery.properties.headers[TRACE_PROPERTY].toString(),
            delivery.properties.headers[RUN_ID_PROPERTY] as Long
        )
        val payload = serializer.fromJson(String(delivery.body, Charsets.UTF_8))

        return Message(header, payload)
    }
}
