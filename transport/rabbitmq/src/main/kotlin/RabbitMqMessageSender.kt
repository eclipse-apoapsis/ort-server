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

package org.ossreviewtoolkit.server.transport.rabbitmq

import com.rabbitmq.client.Channel

import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageSender
import org.ossreviewtoolkit.server.transport.json.JsonSerializer
import org.ossreviewtoolkit.server.transport.rabbitmq.RabbitMqMessageConverter.toAmqpProperties

/**
 * Implementation of the [MessageSender] interface for RabbitMQ.
 */
class RabbitMqMessageSender<T : Any>(
    /** The RabbitMQ channel. */
    private val channel: Channel,

    /** The name of the queue to publish the message to. */
    private val queueName: String,

    /** The endpoint to receive messages from this sender. */
    endpoint: Endpoint<T>
) : MessageSender<T>, AutoCloseable by channel {
    private val serializer = JsonSerializer.forClass(endpoint.messageClass)

    override fun send(message: Message<T>) = channel.basicPublish(
        /* exchange = */ "",
        /* routingKey = */ queueName,
        /* mandatory = */ true,
        /* props = */ message.header.toAmqpProperties(),
        /* body = */ RabbitMqMessageConverter.toStringMessage(message, serializer).toByteArray()
    )
}
