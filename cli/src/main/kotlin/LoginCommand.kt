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

package org.eclipse.apoapsis.ortserver.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

import org.eclipse.apoapsis.ortserver.cli.model.AuthenticationStorage
import org.eclipse.apoapsis.ortserver.cli.model.HostAuthenticationDetails
import org.eclipse.apoapsis.ortserver.cli.model.Tokens
import org.eclipse.apoapsis.ortserver.client.OrtServerClient.Companion.JSON
import org.eclipse.apoapsis.ortserver.client.auth.AuthService
import org.eclipse.apoapsis.ortserver.client.createDefaultHttpClient

/**
 * A command to log in to an ORT Server instance.
 */
class LoginCommand : SuspendingCliktCommand(name = "login") {
    private val baseUrl by option(
        "--base-url",
        envvar = "OSC_ORT_SERVER_BASE_URL",
        help = "The base URL of the ORT Server instance."
    ).convert { it.ensureSuffix("/") }.required()

    private val tokenUrl by option(
        "--token-url",
        envvar = "OSC_ORT_SERVER_TOKEN_URL",
        help = "The URL to request a token for the ORT Server instance."
    ).required()

    private val clientId by option(
        "--client-id",
        envvar = "OSC_ORT_SERVER_CLIENT_ID",
        help = "The client ID to authenticate with the ORT Server instance."
    ).required()

    private val username by option(
        "--username",
        envvar = "OSC_ORT_SERVER_USERNAME",
        help = "The username to authenticate with the ORT Server instance."
    ).required()

    private val password by option(
        "--password",
        envvar = "OSC_ORT_SERVER_PASSWORD",
        help = "The password to authenticate with the ORT Server instance."
    ).required()

    override fun help(context: Context) = "Login to an ORT Server instance."

    override suspend fun run() {
        val authService = AuthService(
            client = createDefaultHttpClient(JSON),
            tokenUrl = tokenUrl,
            clientId = clientId
        )

        val tokenInfo = authService.generateToken(username, password, setOf("offline_access"))

        AuthenticationStorage.store(
            HostAuthenticationDetails(
                baseUrl,
                tokenUrl,
                clientId,
                username,
                Tokens(
                    tokenInfo.accessToken,
                    tokenInfo.refreshToken
                )
            )
        )

        echo("Successfully logged in to '$baseUrl' as '$username'.")
    }
}
