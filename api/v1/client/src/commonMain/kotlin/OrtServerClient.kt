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

package org.eclipse.apoapsis.ortserver.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.client.api.RepositoriesApi
import org.eclipse.apoapsis.ortserver.client.api.RunsApi
import org.eclipse.apoapsis.ortserver.client.auth.AuthService

class OrtServerClient(
    /**
     * The configured HTTP client for the interaction with the API.
     */
    client: HttpClient,
) {
    companion object {
        /**
         * The JSON configuration to use for serialization and deserialization.
         */
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Create a new instance of the ORT server client using the given [config] and configure an HTTP client with
         * the necessary authentication.
         */
        fun create(config: OrtServerClientConfig): OrtServerClient {
            val auth = AuthService(
                client = createDefaultHttpClient(JSON),
                tokenUrl = config.tokenUrl,
                clientId = config.clientId
            )

            val client = createOrtHttpClient(JSON) {
                defaultRequest {
                    url(config.baseUrl)
                }

                install(Auth) {
                    bearer {
                        loadTokens {
                            val tokenInfo = auth.generateToken(config.username, config.password)

                            BearerTokens(tokenInfo.accessToken, tokenInfo.refreshToken)
                        }

                        refreshTokens {
                            val tokenInfo = runCatching {
                                auth.refreshToken(oldTokens?.refreshToken.orEmpty())
                            }.getOrElse {
                                auth.generateToken(config.username, config.password)
                            }

                            BearerTokens(tokenInfo.accessToken, tokenInfo.refreshToken)
                        }
                    }
                }
            }
            return OrtServerClient(client)
        }
    }

    /**
     * Provide access to the repositories API, allowing operations on repositories in the ORT server.
     */
    val repositories = RepositoriesApi(client)

    /**
     * Provide access to the runs API, allowing operations on runs in the ORT server.
     */
    val runs = RunsApi(client)
}
