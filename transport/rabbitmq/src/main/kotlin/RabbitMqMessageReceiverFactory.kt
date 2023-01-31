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
import com.rabbitmq.client.Delivery

import com.typesafe.config.Config

import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

import org.slf4j.LoggerFactory

/**
 * Implementation of the [MessageReceiverFactory] interface for RabbitMQ.
 */
class RabbitMqMessageReceiverFactory : MessageReceiverFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    override val name = RabbitMqConfig.TRANSPORT_NAME

    override fun <T : Any> createReceiver(endpoint: Endpoint<T>, config: Config, handler: EndpointHandler<T>) {
        val serializer = JsonSerializer.forClass(endpoint.messageClass)
        val rabbitMqConfig = RabbitMqConfig.createReceiverConfig(endpoint, config)

        logger.info(
            "Starting RabbitMQ message receiver for endpoint '${endpoint.configPrefix}' using queue " +
                    "'${rabbitMqConfig.queueName}'"
        )

        val connectionFactory = ConnectionFactory().apply {
            setUri(rabbitMqConfig.serverUri)
            username = rabbitMqConfig.username
            password = rabbitMqConfig.password
        }
        connectionFactory.newConnection().use { connection ->
            val channel = connection.createChannel()

            var running = true
            do {
                channel.basicConsume(
                    /* queue = */ rabbitMqConfig.queueName,
                    /* autoAck = */ true,
                    /* deliverCallback = */ { _: String, delivery: Delivery ->
                        runCatching {
                            val message = RabbitMqMessageConverter.toTransportMessage(delivery, serializer)

                            if (logger.isDebugEnabled) {
                                logger.debug(
                                    "Received message '${message.header.traceId}' with payload of type " +
                                            "'${message.payload.javaClass.name}'."
                                )
                            }

                            handler(message)
                        }.onFailure {
                            logger.error("Error during message processing.", it)
                        }
                    },
                    /* cancelCallback = */ { _: String ->
                        logger.error("Message retrieval was canceled.")
                        running = false
                    }
                )
            } while (running)
        }
    }
}
