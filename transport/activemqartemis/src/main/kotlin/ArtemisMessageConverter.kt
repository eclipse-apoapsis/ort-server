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

import jakarta.jms.Session
import jakarta.jms.TextMessage

import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer

/**
 * A helper object implementing functionality to convert between JMS messages and messages of the messaging layer.
 * This is used by both the Artemis-based sender and receiver implementations.
 */
internal object ArtemisMessageConverter {
    /**
     * Convert the given [message] to a JMS [TextMessage] using the provided [serializer] and [session].
     */
    fun <T> toJmsMessage(message: Message<T>, serializer: JsonSerializer<T>, session: Session): TextMessage =
        session.createTextMessage(serializer.toJson(message.payload)).apply {
            setStringProperty(TRACE_PROPERTY, message.header.traceId)
            setLongProperty(RUN_ID_PROPERTY, message.header.ortRunId)
        }

    /**
     * Convert the given [jmsMessage] to a [Message] using the given [serializer].
     */
    fun <T> toTransportMessage(jmsMessage: TextMessage, serializer: JsonSerializer<T>): Message<T> {
        val header = MessageHeader(
            traceId = jmsMessage.getStringProperty(TRACE_PROPERTY),
            ortRunId = jmsMessage.getLongProperty(RUN_ID_PROPERTY)
        )
        val payload = serializer.fromJson(jmsMessage.text)

        return Message(header, payload)
    }
}
