/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.artemis

import jakarta.jms.MessageConsumer
import jakarta.jms.TextMessage

import org.apache.qpid.jms.JmsConnectionFactory

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext

import org.slf4j.LoggerFactory

/**
 * Implementation of the [MessageReceiverFactory] interface for Apache ActiveMQ Artemis.
 */
class ArtemisMessageReceiverFactory : MessageReceiverFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(ArtemisMessageReceiverFactory::class.java)

        /**
         * Receive a [TextMessage] via this consumer and check for an [IllegalStateException], which indicates that
         * the consumer can no longer be used. (An unrecoverable error has happened.)
         */
        private fun MessageConsumer.receiveSave(): TextMessage? =
            try {
                receive() as TextMessage
            } catch (e: jakarta.jms.IllegalStateException) {
                logger.warn("Error when receiving a message. Consumer was probably closed.", e)
                null
            }
    }

    override val name = ArtemisConfig.TRANSPORT_NAME

    override suspend fun <T : Any> createReceiver(
        from: Endpoint<T>,
        configManager: ConfigManager,
        handler: EndpointHandler<T>
    ) {
        val serializer = JsonSerializer.forClass(from.messageClass)
        val artemisConfig = ArtemisConfig.createConfig(configManager)

        logger.info(
            "Starting Artemis message receiver for endpoint '{}' using queue '{}'.",
            from.configPrefix,
            artemisConfig.queueName
        )

        val connectionFactory = JmsConnectionFactory(artemisConfig.serverUri)
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(artemisConfig.queueName)
            val consumer = session.createConsumer(queue)
            connection.start()

            // TODO: To make this a production-ready implementation, error handling needs to be improved.
            loop@ while (true) {
                runCatching {
                    val jmsMessage = consumer.receiveSave()

                    val result = if (jmsMessage != null) {
                        val message = ArtemisMessageConverter.toTransportMessage(jmsMessage, serializer)

                        withMdcContext(
                            "traceId" to message.header.traceId,
                            "ortRunId" to message.header.ortRunId.toString()
                        ) {
                            logger.debug(
                                "Received message '{}' with payload of type {}.",
                                message.header.traceId,
                                message.payload.javaClass.name
                            )

                            handler(message)
                        }
                    } else {
                        // jmsMessage is null when there is an unrecoverable error with the consumer; so exit the loop.
                        EndpointHandlerResult.STOP
                    }

                    if (result == EndpointHandlerResult.STOP) break@loop
                }.onFailure { exception ->
                    logger.error("Error during message processing.", exception)
                }
            }
        }
    }
}
