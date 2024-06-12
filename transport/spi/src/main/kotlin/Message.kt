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

package org.eclipse.apoapsis.ortserver.transport

/** Name of the message property that stores the trace ID. */
const val TRACE_PROPERTY = "traceId"

/** Name of the message property that stores the ORT run ID. */
const val RUN_ID_PROPERTY = "runId"

/**
 * A data class representing the header of messages passed between internal ORT server endpoints. Via the properties
 * defined here, additional metadata about messages is provided.
 */
data class MessageHeader(
    /**
     * An identifier for the current request. This purpose of this string is to allow a correlation of multiple
     * messages that are exchanged to handle a single request.
     */
    val traceId: String,

    /**
     * The ID of the ORT run this message relates to. Via this property, a direct association to an ORT run can be
     * established.
     */
    val ortRunId: Long,

    /**
     * A map with arbitrary key-value pairs that can be evaluated by a concrete transport implementation. The intended
     * use case is to customize the behavior of the underlying transport implementation for a specific message.
     * Concrete transport implementations can have specific properties they support. The property keys need to start
     * with a prefix that corresponds to the name of the transport; that way they are matched by the transport
     * implementation.
     */
    val transportProperties: Map<String, String> = emptyMap()
)

/**
 * A data class to describe the messages exchanged between the internal ORT server endpoints. Each endpoint can
 * handle messages of a specific type.
 */
data class Message<out T>(
    /** The header of this message defining additional metadata. */
    val header: MessageHeader,

    /** The actual payload of this message. */
    val payload: T
)
