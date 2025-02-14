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

package org.eclipse.apoapsis.ortserver.cli

import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.command.parse
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption

import kotlin.system.exitProcess

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.cli.utils.createOrtServerClient
import org.eclipse.apoapsis.ortserver.client.OrtServerClient
import org.eclipse.apoapsis.ortserver.client.OrtServerClientException
import org.eclipse.apoapsis.ortserver.client.auth.AuthenticationException
import org.eclipse.apoapsis.ortserver.model.ORT_SERVER_VERSION

const val COMMAND_NAME = "osc"

suspend fun main(args: Array<String>) {
    val cli = OrtServerMain()

    try {
        cli.parse(args)
    } catch (e: OrtServerClientException) {
        when (e.cause) {
            is AuthenticationException -> {
                cli.echo("Authentication failed. Please run '${COMMAND_NAME} auth login' to authenticate.")
            }
        }

        cli.currentContext.exitProcess(1)
    } catch (e: CliktError) {
        cli.echoFormattedHelp(e)
        cli.currentContext.exitProcess(e.statusCode)
    }

    exitProcess(0)
}

class OrtServerMain : SuspendingNoOpCliktCommand(COMMAND_NAME) {
    init {
        completionOption()

        subcommands(AuthCommand(), RunsCommand())

        versionOption(
            version = ORT_SERVER_VERSION,
            names = setOf("--version", "-v"),
            help = "Show the version of the CLI and the ORT Server if authenticated.",
            message = { runBlocking { buildVersionInformation(it) } }
        )
    }

    override fun help(context: Context) = """
        The ORT Server Client (OSC) is a Command Line Interface (CLI) to interact with an ORT Server instance.
    """.trimIndent()

    /**
     * Build the version information for the CLI and (if authenticated) for the ORT Server.
     */
    private suspend fun buildVersionInformation(cliVersion: String): String {
        val serverVersions = runCatching { createOrtServerClient()?.versions?.getVersions() }.getOrNull()

        return buildString {
            appendLine("$commandName version $cliVersion")
            if (serverVersions != null) {
                val serverVersion = serverVersions["ORT Server"]
                val ortVersion = serverVersions["ORT Core"]

                appendLine("Server version ${serverVersion ?: "<unknown>"}")
                appendLine("ORT Core version ${ortVersion ?: "<unknown>"}")
            }
        }
    }
}

internal val json = Json(OrtServerClient.JSON) { prettyPrint = true }
