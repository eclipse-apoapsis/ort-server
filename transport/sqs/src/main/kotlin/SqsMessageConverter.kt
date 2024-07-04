/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.sqs

import aws.sdk.kotlin.services.sqs.model.MessageAttributeValue

import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY

internal const val EMPTY_VALUE = "<empty-value>"

internal fun MessageHeader.toMessageAttributes() =
    mapOf(
        TRACE_PROPERTY to MessageAttributeValue {
            stringValue = traceId.ifEmpty { EMPTY_VALUE }
            dataType = "String"
        },
        RUN_ID_PROPERTY to MessageAttributeValue {
            stringValue = ortRunId.toString()
            dataType = "Number"
        }
    )

internal fun Map<String, MessageAttributeValue>.toMessageHeader(): MessageHeader {
    val traceId = checkNotNull(get(TRACE_PROPERTY)?.stringValue) {
        "The trace ID attribute is not a valid string value."
    }

    val ortRunId = checkNotNull(get(RUN_ID_PROPERTY)?.stringValue?.toLongOrNull()) {
        "The ORT run ID attribute is not a valid long value."
    }

    return MessageHeader(traceId.takeUnless { it == EMPTY_VALUE }.orEmpty(), ortRunId)
}
