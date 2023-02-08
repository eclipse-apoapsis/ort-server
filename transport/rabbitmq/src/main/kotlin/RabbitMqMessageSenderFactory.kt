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

import com.rabbitmq.client.ConnectionFactory

import com.typesafe.config.Config

import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.MessageSender
import org.ossreviewtoolkit.server.transport.MessageSenderFactory

/**
 * Implementation of the [MessageSenderFactory] interface for RabbitMQ.
 */
class RabbitMqMessageSenderFactory : MessageSenderFactory {
    override val name: String = RabbitMqConfig.TRANSPORT_NAME

    override fun <T : Any> createSender(to: Endpoint<T>, config: Config): MessageSender<T> {
        val rabbitMqConfig = RabbitMqConfig.createConfig(config)

        val connectionFactory = ConnectionFactory().apply {
            setUri(rabbitMqConfig.serverUri)
            username = rabbitMqConfig.username
            password = rabbitMqConfig.password
        }
        return createSenderWithConnection(to, connectionFactory, rabbitMqConfig.queueName)
    }

    /**
     * Create a [MessageSender] for RabbitMQ that sends messages to the given [endpoint][to] via the queue with the
     * given [queueName]. Creates a channel for the [queueName] using the [connectionFactory].
     */
    internal fun <T : Any> createSenderWithConnection(
        to: Endpoint<T>,
        connectionFactory: ConnectionFactory,
        queueName: String
    ): MessageSender<T> {
        val connection = connectionFactory.newConnection()

        return runCatching {
            val channel = connection.createChannel()

            RabbitMqMessageSender(channel, queueName, to)
        }.onFailure {
            connection.close()
        }.getOrThrow()
    }
}
