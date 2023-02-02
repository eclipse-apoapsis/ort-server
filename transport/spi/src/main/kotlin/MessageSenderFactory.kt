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

package org.ossreviewtoolkit.server.transport

import com.typesafe.config.Config

import java.util.ServiceLoader

import org.slf4j.LoggerFactory

/**
 * Factory interface for creating [MessageSender] instances.
 *
 * This interface is intended to be used via the Service Loader mechanism. For each existing implementation of the
 * [MessageSender] interface, a factory must be available. Via configuration properties, the desired implementation is
 * selected.
 */
interface MessageSenderFactory {
    companion object {
        /**
         * The configuration property that defines the type of the sender implementation to be used. To create a
         * [MessageSender], a [MessageSenderFactory] with a name matching the value of this configuration property is
         * looked up.
         */
        const val SENDER_TYPE_PROPERTY = "sender.type"

        /**
         * A prefix used for configuration properties of message senders.
         */
        private const val CONFIG_PREFIX = "sender"

        /** The service loader to load [MessageSenderFactory] implementations. */
        private val LOADER = ServiceLoader.load(MessageSenderFactory::class.java)

        private val log = LoggerFactory.getLogger(MessageSenderFactory::class.java)

        /**
         * Create a [MessageSender] for sending messages to the given [endpoint][to] based on the given [config].
         * Find the [MessageSenderFactory] configured for this endpoint in the [SENDER_TYPE_PROPERTY] property via the
         * Java Service Loader mechanism. Then create an instance using this factory.
         */
        fun <T : Any> createSender(to: Endpoint<T>, config: Config): MessageSender<T> {
            val endpointConfig = config.getConfig(to.configPrefix)
            val factoryName = endpointConfig.getString(SENDER_TYPE_PROPERTY)
            log.info("Creating a MessageSender of type '{}' for endpoint '{}'.", factoryName, to.configPrefix)

            val factory = checkNotNull(LOADER.find { it.name == factoryName }) {
                "No MessageSenderFactory with name '$factoryName' found on classpath."
            }

            return factory.createSender(to, endpointConfig.getConfig(CONFIG_PREFIX))
        }
    }

    /**
     * A unique name of this factory. Via this name a concrete sender implementation can be selected in the server's
     * configuration.
     */
    val name: String

    /**
     * Create a [MessageSender] for sending messages to the given [endpoint][to]. Use [config] as a source of
     * configuration settings (specific to the [MessageSender] implementation).
     */
    fun <T : Any> createSender(to: Endpoint<T>, config: Config): MessageSender<T>
}
