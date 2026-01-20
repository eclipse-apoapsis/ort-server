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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path

import org.slf4j.Logger

/**
 * A class defining the configuration settings used by the RabbitMQ Transport implementation.
 */
class RabbitMqConfig(
    /** The URI where the server broker is running. */
    val serverUri: String,

    /** The name of the queue that is used for sending and receiving messages. */
    val queueName: String,

    /** The username that is used to connect to RabbitMQ. */
    val username: String,

    /** The password that is used to connect to RabbitMQ. */
    val password: String
) {
    companion object {
        /**
         * Constant for the name of this transport implementation. This name is used for both the message sender and
         * receiver factories.
         */
        const val TRANSPORT_NAME = "rabbitMQ"

        /** Name of the configuration property for the server URI. */
        private const val SERVER_URI_PROPERTY = "serverUri"

        /** Name of the configuration property for the queue name. */
        private const val QUEUE_NAME_PROPERTY = "queueName"

        /** Name of the configuration property for the username. */
        private val USERNAME_PROPERTY = Path("rabbitMqUser")

        /** Name of the configuration property for the password. */
        private val PASSWORD_PROPERTY = Path("rabbitMqPassword")

        /**
         * Create a [RabbitMqConfig] from the provided [configManager].
         */
        fun createConfig(configManager: ConfigManager) =
            RabbitMqConfig(
                serverUri = configManager.getString(SERVER_URI_PROPERTY),
                queueName = configManager.getString(QUEUE_NAME_PROPERTY),
                username = configManager.getSecret(USERNAME_PROPERTY),
                password = configManager.getSecret(PASSWORD_PROPERTY)
            )
    }

    /**
     * Log this configuration using the provided [logger].
     */
    fun log(logger: Logger) {
        if (logger.isInfoEnabled) {
            logger.info("RabbitMQ server URI: '$serverUri'")
            logger.info("RabbitMQ user: '$username'")
            logger.info("RabbitMQ queue: '$queueName'")
        }
    }
}
