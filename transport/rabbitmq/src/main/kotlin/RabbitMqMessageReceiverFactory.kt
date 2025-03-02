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

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.Envelope

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

import kotlin.coroutines.coroutineContext

import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.job

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * Implementation of the [MessageReceiverFactory] interface for RabbitMQ.
 */
class RabbitMqMessageReceiverFactory : MessageReceiverFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(RabbitMqMessageReceiverFactory::class.java)
    }

    override val name = RabbitMqConfig.TRANSPORT_NAME

    override suspend fun <T : Any> createReceiver(
        from: Endpoint<T>,
        configManager: ConfigManager,
        handler: EndpointHandler<T>
    ) {
        createMessageFlow(from, configManager).collect { handler(it) }
    }

    /**
     * Return a [Flow] with messages received from a RabbitMQ message queue for the given [endpoint][from]. Use the
     * given [configManager] to set up the connection to RabbitMQ.
     */
    private fun <T : Any> createMessageFlow(
        from: Endpoint<T>,
        configManager: ConfigManager
    ): Flow<Message<T>> = callbackFlow {
        val serializer = JsonSerializer.forClass(from.messageClass)
        val rabbitMqConfig = RabbitMqConfig.createConfig(configManager)

        logger.info("Starting RabbitMQ message receiver for endpoint '${from.configPrefix}'.")
        rabbitMqConfig.log(logger)

        val connectionFactory = ConnectionFactory().apply {
            setUri(rabbitMqConfig.serverUri)
            username = rabbitMqConfig.username
            password = rabbitMqConfig.password
            threadFactory = MdcThreadFactory(Executors.defaultThreadFactory())
        }
        connectionFactory.newConnection().use { connection ->
            val channel = connection.createChannel()
            val consumer = createConsumer(channel, serializer)
            channel.basicConsume(
                /* queue = */ rabbitMqConfig.queueName,
                /* autoAck = */ true,
                consumer
            )

            // basicConsume() immediately returns; incoming messages are passed to the consumer's callback.
            // Since the receiver factory is expected to not return until message processing is done, suspend the
            // current thread until the consumer is canceled.
            awaitClose { logger.error("Message consumer was canceled.") }
        }
    }

    /**
     * Create a [Consumer] for processing the messages received via the given [channel]. Use the provided [serializer]
     * to de-serialize messages. Use the functionality of this [ProducerScope] to send the received messages and to
     * cancel message processing when the consumer is canceled.
     */
    private fun <T : Any> ProducerScope<Message<T>>.createConsumer(
        channel: Channel,
        serializer: JsonSerializer<T>
    ): Consumer {
        return object : DefaultConsumer(channel) {
            override fun handleCancelOk(consumerTag: String?) {
                logger.info("handleCancelOk() callback.")
                cancel("handleCancelOk() callback was invoked.")
            }

            override fun handleCancel(consumerTag: String?) {
                logger.info("handleCancel() callback.")
                cancel("handleCancel() callback was invoked.")
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

                    MDC.put("traceId", message.header.traceId)
                    MDC.put("ortRunId", message.header.ortRunId.toString())

                    if (logger.isDebugEnabled) {
                        logger.debug(
                            "Received message '${message.header.traceId}' with payload of type " +
                                    "'${message.payload.javaClass.name}'."
                        )
                    }

                    // Inline kotlinx.coroutines.channels.trySendBlocking as the function calls runBlocking internally
                    // without preserving the MDC context.
                    if (!trySend(message).isSuccess) {
                        runBlocking {
                            send(message)
                        }
                    }
                }.onFailure {
                    logger.error("Error during message processing.", it)
                }
            }
        }
    }
}

/**
 * A wrapper for the provided [delegate] [ThreadFactory] which sets the MDC context map of the current thread to the
 * context map of the thread that is created by the delegate.
 */
class MdcThreadFactory(private val delegate: ThreadFactory) : ThreadFactory {
    private val mdcContext: Map<String, String>? = MDC.getCopyOfContextMap()

    override fun newThread(runnable: Runnable): Thread =
        delegate.newThread {
            try {
                MDC.setContextMap(mdcContext)
                runnable.run()
            } finally {
                MDC.clear()
            }
        }
}
