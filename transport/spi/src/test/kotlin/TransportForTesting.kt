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

/** The name to identify the test transport implementation. */
internal const val TEST_TRANSPORT_NAME = "testMessageTransport"

/**
 * A test [MessageSenderFactory] implementation that is referenced by test cases.
 */
class MessageSenderFactoryForTesting : MessageSenderFactory {
    override val name: String = TEST_TRANSPORT_NAME

    override fun <T : Any> createSender(to: Endpoint<T>, config: Config): MessageSender<T> =
        MessageSenderForTesting(to, config)
}

/**
 * A test [MessageSender] implementation that is used to test whether the correct parameters have been passed to the
 * factory to create an instance.
 */
class MessageSenderForTesting<T : Any>(
    val endpoint: Endpoint<T>,

    val config: Config
) : MessageSender<T> {
    override fun send(message: Message<T>) {
        println(message)
    }
}

/**
 * A test [MessageReceiverFactory] implementation that is referenced by test cases.
 */
class MessageReceiverFactoryForTesting : MessageReceiverFactory {
    companion object {
        /** Stores the [Endpoint] passed to [createReceiver]. */
        var createdEndpoint: Endpoint<*>? = null

        /** Stores the config passed to [createReceiver]. */
        var createdConfig: Config? = null

        /** Stores the handler function passed to [createReceiver]. */
        var createdHandler: Any? = null

        /**
         * Reset the properties passed from the last [createReceiver] call.
         */
        fun reset() {
            createdEndpoint = null
            createdConfig = null
            createdHandler = null
        }
    }

    override val name: String = TEST_TRANSPORT_NAME

    override fun <T : Any> createReceiver(endpoint: Endpoint<T>, config: Config, handler: EndpointHandler<T>) {
        check(createdEndpoint == null) { "Too many invocations of createReceiver." }

        createdEndpoint = endpoint
        createdConfig = config
        createdHandler = handler
    }
}
