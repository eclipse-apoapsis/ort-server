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

import com.typesafe.config.Config

import org.ossreviewtoolkit.server.transport.Endpoint

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
    val password: String,
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
        private const val USERNAME_PROPERTY = "username"

        /** Name of the configuration property for the password. */
        private const val PASSWORD_PROPERTY = "password"

        /**
         * Create a [RabbitMqConfig] object for a sender to the given [endpoint] from the provided [config].
         */
        fun createSenderConfig(endpoint: Endpoint<*>, config: Config): RabbitMqConfig =
            createConfig(endpoint, "sender", config)

        /**
         * Create a [RabbitMqConfig] object for a receiver of the given [endpoint] from the provided [config].
         */
        fun createReceiverConfig(endpoint: Endpoint<*>, config: Config): RabbitMqConfig =
            createConfig(endpoint, "receiver", config)

        /**
         * Create a [RabbitMqConfig] object for the given [endpoint] and [transportType] (sender or receiver) from the
         * provided [config].
         */
        private fun createConfig(endpoint: Endpoint<*>, transportType: String, config: Config): RabbitMqConfig {
            val prefix = "${endpoint.configPrefix}.$transportType"

            return RabbitMqConfig(
                serverUri = config.getString("$prefix.$SERVER_URI_PROPERTY"),
                queueName = config.getString("$prefix.$QUEUE_NAME_PROPERTY"),
                username = config.getString("$prefix.$USERNAME_PROPERTY"),
                password = config.getString("$prefix.$PASSWORD_PROPERTY"),
            )
        }
    }
}
