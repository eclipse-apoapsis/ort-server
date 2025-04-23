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

import com.rabbitmq.client.ConnectionFactory

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory

import org.slf4j.LoggerFactory

/**
 * Implementation of the [MessageSenderFactory] interface for RabbitMQ.
 */
class RabbitMqMessageSenderFactory : MessageSenderFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(RabbitMqMessageSenderFactory::class.java)
    }

    override val name: String = RabbitMqConfig.TRANSPORT_NAME

    override fun <T : Any> createSender(to: Endpoint<T>, configManager: ConfigManager): MessageSender<T> {
        val rabbitMqConfig = RabbitMqConfig.createConfig(configManager)

        logger.info("Creating RabbitMQ sender for endpoint '${to.configPrefix}'.")
        rabbitMqConfig.log(logger)

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
        val connection = connectionFactory.newConnection(
            "ort-server-${to.configPrefix}-${to.messageClass.simpleName}"
        )

        return runCatching {
            val channel = connection.createChannel().also {
                // Check that the queue exists. If it doesn't, this operation fails with a channel level exception.
                it.queueDeclarePassive(queueName)
            }

            RabbitMqMessageSender(channel, queueName, to)
        }.onFailure {
            connection.close()
        }.getOrThrow()
    }
}
