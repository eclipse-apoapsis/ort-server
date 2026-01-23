/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.utils.logging

/**
 * An interface representing a key in the Mapped Diagnostic Context (MDC). Using a more advanced representation instead
 * of plain strings reduces the risk of typos in key names, especially for standard keys.
 */
sealed interface MdcKey {
    /** The string representation of the MDC key. */
    val key: String
}

/**
 * An enumeration of standard MDC keys used throughout the application.
 */
enum class StandardMdcKeys(override val key: String) : MdcKey {
    /**
     * The key describing the component that produces the log message. This is typically a separate application or a
     * module within ORT Server.
     */
    COMPONENT("component"),

    /** The ID of the ORT run associated with the log message. */
    ORT_RUN_ID("ortRunId"),

    /** The trace ID of the request associated with the log message. */
    TRACE_ID("traceId")
}

/**
 * A data class allowing the creation of arbitrary MDC keys not covered by [StandardMdcKeys].
 */
data class CustomMdcKey(override val key: String) : MdcKey
