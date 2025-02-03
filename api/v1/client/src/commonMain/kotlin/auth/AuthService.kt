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

package org.eclipse.apoapsis.ortserver.client.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess

/**
 * A service to generate/refresh an OAuth token to authenticate with the ORT server.
 */
class AuthService(
    private val client: HttpClient,
    private val tokenUrl: String,
    private val clientId: String
) {
    /**
     * Generate a token for the given [username] and [password] using password grant type with optional [scopes].
     */
    suspend fun generateToken(username: String, password: String, scopes: Set<String> = emptySet()): TokenInfo =
        client.submitForm(
            url = tokenUrl,
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("username", username)
                append("password", password)
                append("grant_type", "password")
                if (scopes.isNotEmpty()) {
                    append("scope", scopes.joinToString(" "))
                }
            }
        ).let { response ->
            if (!response.status.isSuccess()) {
                throw AuthenticationException(
                    "Failed to generate token: ${response.status.value}: ${response.bodyAsText()}."
                )
            }

            response.body()
        }

    /**
     * Refresh the tokens for the given [refreshToken] with optional [scopes].
     */
    suspend fun refreshToken(refreshToken: String, scopes: Set<String> = emptySet()): TokenInfo =
        client.submitForm(
            url = tokenUrl,
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("refresh_token", refreshToken)
                append("grant_type", "refresh_token")
                if (scopes.isNotEmpty()) {
                    append("scope", scopes.joinToString(" "))
                }
            }
        ).let { response ->
            if (!response.status.isSuccess()) {
                throw AuthenticationException(
                    "Failed to refresh token: ${response.status.value}: ${response.bodyAsText()}."
                )
            }

            response.body()
        }
}

/**
 * An exception to indicate an authentication error.
 */
class AuthenticationException(message: String, cause: Throwable? = null) : Exception(message, cause)
