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

package org.eclipse.apoapsis.ortserver.cli.model.printables

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.cli.json

/**
 * A [CliPrintable] that prints a simple [String] message.
 */
@Serializable
class MessagePrintable(private val message: String) : CliPrintable {
    override fun json() = json.encodeToString(StringMessage(message))

    override fun toString() = message
}

fun String.toPrintable(): MessagePrintable = MessagePrintable(this)

/**
 * A wrapper for JSON formatting of a String.
 */
@Serializable
private data class StringMessage(val message: String)
