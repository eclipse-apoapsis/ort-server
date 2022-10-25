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
 * Definition of a message handler function of an endpoint. The function is passed the message it should handle.
 */
typealias EndpointHandler<T> = (Message<T>) -> Unit

/**
 * Factory interface for setting up a receiver for an [Endpoint].
 *
 * Via this interface endpoints can be implemented that are independent on a concrete message transport mechanism. The
 * idea is that the endpoint implementation just provides an [EndpointHandler] function. The factory constructs the
 * necessary infrastructure to receive messages (e.g. by registering a listener on a message queue or starting an
 * HTTP endpoint). When a message is received the infrastructure calls the [EndpointHandler], so that it can be
 * processed. Note that no direct interactions are done with this infrastructure; therefore, there is no explicit
 * _MessageReceiver_ type.
 *
 * A ServiceLoader is used to select the concrete factory implementation dynamically based on the configuration
 * settings of the affected [Endpoint].
 */
interface MessageReceiverFactory {
    companion object {
        /**
         * The configuration property that defines the type of the receiver implementation to be used. To create a
         * receiver, a [MessageReceiverFactory] with a name matching the value of this configuration property is
         * looked up.
         */
        const val RECEIVER_TYPE_PROPERTY = "receiver.type"

        /** The service loader to load [MessageReceiverFactory] implementations. */
        private val LOADER = ServiceLoader.load(MessageReceiverFactory::class.java)

        private val log = LoggerFactory.getLogger(MessageReceiverFactory::class.java)

        /**
         * Set up infrastructure to process messages for the given [endpoint] with the given [handler] function based
         * on the provided [config]. Find the [MessageReceiverFactory] configured for this endpoint in the
         * [RECEIVER_TYPE_PROPERTY] property via the Java Service Loader mechanism. Then create an instance using this
         * factory.
         */
        fun <T : Any> createReceiver(endpoint: Endpoint<T>, config: Config, handler: EndpointHandler<T>) {
            val factoryName = config.getString("${endpoint.configPrefix}.$RECEIVER_TYPE_PROPERTY")
            log.info("Setting up a MessageReceiver of type '{}' for endpoint '{}'.", factoryName, endpoint.configPrefix)

            val factory = checkNotNull(LOADER.find { it.name == factoryName }) {
                "No MessageReceiverFactory with name '$factoryName' found on classpath."
            }

            factory.createReceiver(endpoint, config, handler)
        }
    }

    /**
     * A unique name of this factory. Via this name a concrete sender implementation can be selected in the server's
     * configuration.
     */
    val name: String

    /**
     * Set up infrastructure that is able to receive messages to the given [endpoint] and calls the provided [handler]
     * function with the received messages. Use [config] as source of configuration settings for this infrastructure.
     */
    fun <T : Any> createReceiver(endpoint: Endpoint<T>, config: Config, handler: EndpointHandler<T>)
}
