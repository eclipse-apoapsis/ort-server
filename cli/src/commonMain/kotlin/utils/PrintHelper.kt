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

package org.eclipse.apoapsis.ortserver.cli.utils

import com.github.ajalt.clikt.command.SuspendingCliktCommand

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.cli.json
import org.eclipse.apoapsis.ortserver.cli.model.printables.CliPrintable
import org.eclipse.apoapsis.ortserver.cli.model.printables.toPrintable

/**
 * Global variable that gets toggled by a command line parameter parsed in the main entry points of the modules.
 */
var useJsonFormat = false

/**
 * Print the [message] of an error to stderr. If required as a [json][CliError] object.
 */
internal fun SuspendingCliktCommand.echoError(message: String?) {
    if (useJsonFormat) {
        echo(json.encodeToString(CliError(message)), err = true)
    } else {
        echo(message, err = true)
    }
}

/**
 * Print the [message] to stdout. If required as a json object.
 */
internal fun SuspendingCliktCommand.echoMessage(message: CliPrintable) {
    if (useJsonFormat) {
        echo(message.json())
    } else {
        echo(message.humanReadable())
    }
}

internal fun SuspendingCliktCommand.echoMessage(message: String) = echoMessage(message.toPrintable())

@Serializable
private data class CliError(val message: String?)
