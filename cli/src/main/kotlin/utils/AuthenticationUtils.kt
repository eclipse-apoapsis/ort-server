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

import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest

import org.eclipse.apoapsis.ortserver.cli.COMMAND_NAME
import org.eclipse.apoapsis.ortserver.cli.model.AuthenticationStorage
import org.eclipse.apoapsis.ortserver.cli.model.HostAuthenticationDetails
import org.eclipse.apoapsis.ortserver.cli.model.Tokens
import org.eclipse.apoapsis.ortserver.client.OrtServerClient
import org.eclipse.apoapsis.ortserver.client.OrtServerClient.Companion.JSON
import org.eclipse.apoapsis.ortserver.client.auth.AuthService
import org.eclipse.apoapsis.ortserver.client.auth.AuthenticationException
import org.eclipse.apoapsis.ortserver.client.createDefaultHttpClient
import org.eclipse.apoapsis.ortserver.client.createOrtHttpClient

/**
 * Create an authenticated ORT Server client using the stored authentication. Returns null if the user is not
 * authenticated.
 */
fun createOrtServerClient() = AuthenticationStorage.get()?.let { createOrtServerClient(it) }

/**
 * Create a new instance of the ORT server client using the given [authDetails] and configure an HTTP client
 * with the necessary authentication.
 */
private fun createOrtServerClient(authDetails: HostAuthenticationDetails): OrtServerClient {
    val client = createOrtHttpClient(JSON) {
        defaultRequest {
            url(authDetails.baseUrl)
        }

        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(
                        authDetails.tokens.access,
                        authDetails.tokens.refresh
                    )
                }

                refreshTokens {
                    val updatedTokenInfo = runCatching {
                        val auth = AuthService(
                            client = createDefaultHttpClient(JSON),
                            tokenUrl = authDetails.tokenUrl,
                            clientId = authDetails.clientId
                        )

                        auth.refreshToken(authDetails.tokens.refresh).also {
                            val updatedAuthDetails = authDetails.copy(
                                tokens = Tokens(it.accessToken, it.refreshToken)
                            )

                            AuthenticationStorage.store(updatedAuthDetails)
                        }
                    }.getOrElse {
                        throw AuthenticationException(
                            "Authentication refresh failed.$it Please run '$COMMAND_NAME auth login'."
                        )
                    }

                    BearerTokens(updatedTokenInfo.accessToken, updatedTokenInfo.refreshToken)
                }
            }
        }
    }

    return OrtServerClient(client)
}
