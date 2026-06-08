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
import com.github.ajalt.clikt.core.MutuallyExclusiveGroupException
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

import org.eclipse.apoapsis.ortserver.api.v1.model.OidcConfig
import org.eclipse.apoapsis.ortserver.cli.model.AuthenticationStorage
import org.eclipse.apoapsis.ortserver.cli.model.HostAuthenticationDetails
import org.eclipse.apoapsis.ortserver.cli.model.Tokens
import org.eclipse.apoapsis.ortserver.cli.utils.createUnauthenticatedOrtServerClient
import org.eclipse.apoapsis.ortserver.cli.utils.echoMessage
import org.eclipse.apoapsis.ortserver.client.OrtServerClient.Companion.JSON
import org.eclipse.apoapsis.ortserver.client.auth.AuthService
import org.eclipse.apoapsis.ortserver.client.createDefaultHttpClient

private class ServerOptions : OptionGroup(
    name = "Server options",
    help = "Options to configure the ORT Server instance to log in to."
) {
    val baseUrl by option(
        "--url",
        envvar = "OSC_ORT_SERVER_URL",
        help = "The base URL of the ORT Server instance without the '/api/v1' path."
    ).convert { it.ensureSuffix("/") }.required()

    val tokenUrl by option(
        "--token-url",
        envvar = "OSC_ORT_SERVER_TOKEN_URL",
        help = "The URL to request a token for the ORT Server instance."
    )

    val clientId by option(
        "--client-id",
        envvar = "OSC_ORT_SERVER_CLIENT_ID",
        help = "The client ID to authenticate with the ORT Server instance."
    )
}

private class UserOptions : OptionGroup(
    name = "User options",
    help = "Options to authenticate with a username and password. Mutually exclusive with the token options."
) {
    val username by option(
        "--username",
        envvar = "OSC_ORT_SERVER_USERNAME",
        help = "The username to authenticate with the ORT Server instance."
    ).required()

    val password by option(
        "--password",
        envvar = "OSC_ORT_SERVER_PASSWORD",
        help = "The password to authenticate with the ORT Server instance."
    ).required()
}

private class TokenOptions : OptionGroup(
    name = "Token options",
    help = "Options to authenticate with a token. Mutually exclusive with the user options."
) {
    val offlineToken by option(
        "--token",
        envvar = "OSC_ORT_SERVER_TOKEN",
        help = "The token to authenticate with the ORT Server instance. Tokens can be generated in the profile " +
                "section of the ORT Server UI."
    ).required()
}

/**
 * A command to log in to an ORT Server instance.
 */
class LoginCommand : SuspendingCliktCommand(name = "login") {
    private val serverOptions by ServerOptions()
    private val userOptions by UserOptions().cooccurring()
    private val tokenOptions by TokenOptions().cooccurring()

    override fun help(context: Context) = "Login to an ORT Server instance."

    override suspend fun run() {
        if (userOptions != null && tokenOptions != null) {
            throw MutuallyExclusiveGroupException(listOf("--username and --password", "--offline-token"))
        }

        if (userOptions == null && tokenOptions == null) {
            throw UsageError("Either --username and --password or --offline-token must be provided.")
        }

        val oidcConfig = createOidcConfig(serverOptions.baseUrl, serverOptions.tokenUrl, serverOptions.clientId)

        val tokenInfo = AuthService(
            client = createDefaultHttpClient(JSON),
            tokenUrl = oidcConfig.accessTokenUrl,
            clientId = oidcConfig.clientId
        ).createToken(userOptions, tokenOptions)

        AuthenticationStorage.store(
            HostAuthenticationDetails(
                serverOptions.baseUrl,
                oidcConfig.accessTokenUrl,
                oidcConfig.clientId,
                userOptions?.username,
                Tokens(
                    tokenInfo.accessToken,
                    tokenInfo.refreshToken
                )
            )
        )

        if (userOptions != null) {
            echoMessage("Successfully logged in to '${serverOptions.baseUrl}' as '${userOptions?.username}'.")
        } else {
            echoMessage("Successfully logged in to '${serverOptions.baseUrl}' with an offline token.")
        }
    }
}

private suspend fun createOidcConfig(baseUrl: String, tokenUrl: String?, clientId: String?) =
    if (tokenUrl == null || clientId == null) {
        val client = createUnauthenticatedOrtServerClient(baseUrl)
        val serverConfig = client.auth.getCliOidcConfig()

        serverConfig.copy(
            accessTokenUrl = tokenUrl ?: serverConfig.accessTokenUrl,
            clientId = clientId ?: serverConfig.clientId
        )
    } else {
        OidcConfig(tokenUrl, clientId)
    }

private suspend fun AuthService.createToken(userOptions: UserOptions?, tokenOptions: TokenOptions?) =
    if (userOptions != null) {
        generateToken(userOptions.username, userOptions.password, setOf("offline_access"))
    } else {
        refreshToken(checkNotNull(tokenOptions).offlineToken, setOf("offline_access"))
    }
