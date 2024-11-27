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
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

import kotlin.system.exitProcess

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.client.OrtServerClient
import org.eclipse.apoapsis.ortserver.client.OrtServerClientConfig

const val COMMAND_NAME = "ort-server"

suspend fun main(args: Array<String>) {
    OrtServerMain().main(args)
    exitProcess(0)
}

class OrtServerMain : SuspendingNoOpCliktCommand(COMMAND_NAME) {
    private val ortServerConfig by OrtServerOptions()

    init {
        subcommands(RunsCommand(ortServerConfig))
    }
}

class OrtServerOptions : OptionGroup(
    name = "ORT Server Options",
    help = "Configuration options for the ORT Server instance."
) {
    val baseUrl by option(
        "--base-url",
        envvar = "ORT_SERVER_BASE_URL",
        help = "The base URL of the ORT Server instance."
    ).required()

    val tokenUrl by option(
        "--token-url",
        envvar = "ORT_SERVER_TOKEN_URL",
        help = "The URL to request a token for the ORT Server instance."
    ).required()

    val clientId by option(
        "--client-id",
        envvar = "ORT_SERVER_CLIENT_ID",
        help = "The client ID to authenticate with the ORT Server instance."
    ).required()

    val username by option(
        "--username",
        envvar = "ORT_SERVER_USERNAME",
        help = "The username to authenticate with the ORT Server instance."
    ).required()

    val password by option(
        "--password",
        envvar = "ORT_SERVER_PASSWORD",
        help = "The password to authenticate with the ORT Server instance."
    ).required()

    fun toOrtServerClientConfig(): OrtServerClientConfig {
        return OrtServerClientConfig(
            baseUrl = baseUrl,
            tokenUrl = tokenUrl,
            clientId = clientId,
            username = username,
            password = password
        )
    }
}

internal val json = Json(OrtServerClient.JSON) { prettyPrint = true }
