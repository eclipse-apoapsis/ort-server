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

package org.eclipse.apoapsis.ortserver.transport.testing

import com.typesafe.config.Config

import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory

/** The name to identify the test transport implementation. */
const val TEST_TRANSPORT_NAME = "testMessageTransport"

/**
 * A test [MessageSenderFactory] implementation that is referenced by test cases.
 */
class MessageSenderFactoryForTesting : MessageSenderFactory {
    companion object {
        /** A map for storing messages sent by one of the created senders. */
        private val messageQueues = mutableMapOf<Endpoint<*>, BlockingQueue<Message<*>>>()

        /**
         * Return the next message that was sent to the given [endpoint][to] or throw an exception if no such message
         * exists.
         */
        fun <T : Any> expectMessage(to: Endpoint<T>): Message<T> {
            val queue = queueFor(to)

            @Suppress("UNCHECKED_CAST")
            return queue.remove() as Message<T>
        }

        /**
         * Verify that no message has been sent to the given [endpoint][to] within the given [delay]. The [delay] can
         * be needed if messages are sent asynchronously.
         */
        fun <T : Any> expectNoMessage(to: Endpoint<T>, delay: Duration = 1.seconds) {
            val queue = queueFor(to)

            queue.poll(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS) should beNull()
        }

        /**
         * Make sure that a sender for the given [endpoint] has been created and return its queue of sent messages.
         */
        private fun <T : Any> queueFor(endpoint: Endpoint<T>) = messageQueues[endpoint].shouldNotBeNull()
    }

    override val name: String = TEST_TRANSPORT_NAME

    override fun <T : Any> createSender(to: Endpoint<T>, configManager: ConfigManager): MessageSender<T> {
        val queue = LinkedBlockingQueue<Message<T>>()
        val sender = MessageSenderForTesting(to, configManager, queue)

        @Suppress("UNCHECKED_CAST")
        messageQueues[to] = queue as BlockingQueue<Message<*>>
        return sender
    }
}

/**
 * A test [MessageSender] implementation that is used to test whether the correct parameters have been passed to the
 * factory to create an instance.
 */
class MessageSenderForTesting<T : Any>(
    val endpoint: Endpoint<T>,

    val config: Config,

    /** A queue where to store messages sent via this object. */
    private val queue: Queue<Message<T>>
) : MessageSender<T> {
    override fun send(message: Message<T>) {
        queue.offer(message)
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

        /**
         * Invoke the recorded [EndpointHandler] for the given [endpoint] with the given [message]. Fail if no handler
         * or a handler to a different endpoint was registered.
         */
        suspend fun <T : Any> receive(endpoint: Endpoint<T>, message: Message<T>) {
            createdEndpoint shouldBe endpoint

            @Suppress("UNCHECKED_CAST")
            val handler = createdHandler as EndpointHandler<T>
            handler(message)
        }
    }

    override val name: String = TEST_TRANSPORT_NAME

    override suspend fun <T : Any> createReceiver(
        from: Endpoint<T>,
        configManager: ConfigManager,
        handler: EndpointHandler<T>
    ) {
        check(createdEndpoint == null) { "Too many invocations of createReceiver." }

        createdEndpoint = from
        createdConfig = configManager
        createdHandler = handler
    }
}
