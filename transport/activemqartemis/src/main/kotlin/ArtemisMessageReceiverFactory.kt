/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.artemis

import com.typesafe.config.Config

import jakarta.jms.MessageConsumer
import jakarta.jms.TextMessage

import org.apache.qpid.jms.JmsConnectionFactory

import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

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

    override fun <T : Any> createReceiver(endpoint: Endpoint<T>, config: Config, handler: EndpointHandler<T>) {
        val serializer = JsonSerializer.forClass(endpoint.messageClass)
        val artemisConfig = ArtemisConfig.create(endpoint, config)

        logger.info(
            "Starting Artemis message receiver for endpoint '{}' using queue '{}'.",
            endpoint.configPrefix,
            artemisConfig.queueName
        )

        val connectionFactory = JmsConnectionFactory(artemisConfig.serverUri)
        connectionFactory.createConnection().use { connection ->
            val session = connection.createSession()
            val queue = session.createQueue(artemisConfig.queueName)
            val consumer = session.createConsumer(queue)
            connection.start()

            // TODO: To make this a production-ready implementation, error handling needs to be improved.
            var running = true
            do {
                runCatching {
                    val jmsMessage = consumer.receiveSave()
                    if (jmsMessage != null) {
                        val message = ArtemisMessageConverter.toTransportMessage(jmsMessage, serializer)

                        logger.debug(
                            "Received message '{}' with payload of type {}.",
                            message.header.traceId,
                            message.payload.javaClass.name
                        )

                        handler(message)
                    }

                    // jmsMessage is null when there is an unrecoverable error with the consumer; so exit the loop.
                    running = jmsMessage != null
                }.onFailure { exception ->
                    logger.error("Error during message processing.", exception)
                }
            } while (running)
        }
    }
}
