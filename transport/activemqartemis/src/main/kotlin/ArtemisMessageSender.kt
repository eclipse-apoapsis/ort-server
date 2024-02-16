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

import jakarta.jms.Connection
import jakarta.jms.MessageProducer
import jakarta.jms.Session

import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer

/**
 * Implementation of the [MessageSender] interface for Apache ActiveMQ Artemis.
 */
internal class ArtemisMessageSender<T : Any>(
    /** The JMS connection. */
    private val connection: Connection,

    /** The JMS session. */
    private val session: Session,

    /** The object to produce JMS messages. */
    private val producer: MessageProducer,

    /** The endpoint to receive messages from this sender. */
    endpoint: Endpoint<T>
) : MessageSender<T>, AutoCloseable {
    /** The object to serializer the payload of messages. */
    private val serializer = JsonSerializer.forClass(endpoint.messageClass)

    override fun send(message: Message<T>) {
        producer.send(ArtemisMessageConverter.toJmsMessage(message, serializer, session))
    }

    override fun close() {
        connection.close()
    }
}
