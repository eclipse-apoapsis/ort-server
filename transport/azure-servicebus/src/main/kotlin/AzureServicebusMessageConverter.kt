/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.azureservicebus

import com.azure.messaging.servicebus.ServiceBusMessage
import com.azure.messaging.servicebus.ServiceBusReceivedMessage

import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer

internal object AzureServicebusMessageConverter {
    fun <T> toServiceBusMessage(message: Message<T>, serializer: JsonSerializer<T>): ServiceBusMessage {
        return ServiceBusMessage(serializer.toJson(message.payload)).apply {
            setContentType("application/json")
            applicationProperties[TRACE_PROPERTY] = message.header.traceId
            applicationProperties[RUN_ID_PROPERTY] = message.header.ortRunId
        }
    }

    fun <T> toTransportMessage(message: ServiceBusReceivedMessage, serializer: JsonSerializer<T>): Message<T> {
        val header = MessageHeader(
            message.applicationProperties.getValue(TRACE_PROPERTY) as String,
            message.applicationProperties.getValue(RUN_ID_PROPERTY) as Long
        )

        val payload = serializer.fromJson(message.getBody().toString())

        return Message(header, payload)
    }
}
