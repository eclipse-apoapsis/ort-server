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

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.Envelope

import com.typesafe.config.Config

import java.util.concurrent.CountDownLatch

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

    override fun <T : Any> createReceiver(from: Endpoint<T>, config: Config, handler: EndpointHandler<T>) {
        val serializer = JsonSerializer.forClass(from.messageClass)
        val rabbitMqConfig = RabbitMqConfig.createConfig(config)

        logger.info("Starting RabbitMQ message receiver for endpoint '${from.configPrefix}'.")
        rabbitMqConfig.log(logger)

        val connectionFactory = ConnectionFactory().apply {
            setUri(rabbitMqConfig.serverUri)
            username = rabbitMqConfig.username
            password = rabbitMqConfig.password
        }
        connectionFactory.newConnection().use { connection ->
            val channel = connection.createChannel()
            val latchCanceled = CountDownLatch(1)

            val consumer = createConsumer(channel, serializer, handler, latchCanceled)
            channel.basicConsume(
                /* queue = */ rabbitMqConfig.queueName,
                /* autoAck = */ true,
                consumer
            )

            // basicConsume() immediately returns; incoming messages are passed to the consumer's callback.
            // Since the receiver factory is expected to not return until message processing is done, the current
            // thread is blocked until the consumer is canceled.
            latchCanceled.await()

            logger.error("Message consumer was canceled.")
        }
    }

    /**
     * Create a [Consumer] for processing the messages received via the given [channel] by invoking the given
     * [handler]. Use the provided [serializer] to de-serialize messages. Trigger the given [latch][latchCanceled]
     * when the consumer is canceled. The thread that started the consumer waits on this latch until the cancellation.
     */
    private fun <T : Any> createConsumer(
        channel: Channel,
        serializer: JsonSerializer<T>,
        handler: EndpointHandler<T>,
        latchCanceled: CountDownLatch
    ): Consumer {
        return object : DefaultConsumer(channel) {
            override fun handleCancelOk(consumerTag: String?) {
                logger.info("handleCancelOk() callback.")
                latchCanceled.countDown()
            }

            override fun handleCancel(consumerTag: String?) {
                logger.info("handleCancel() callback.")
                latchCanceled.countDown()
            }

            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                runCatching {
                    val delivery = Delivery(envelope, properties, body)
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
            }
        }
    }
}
