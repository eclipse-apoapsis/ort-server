/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.clients.keycloak.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters

internal suspend fun HttpClient.generateAccessToken(
    tokenUrl: String,
    clientId: String,
    username: String,
    password: String
) = submitForm(
    url = tokenUrl,
    formParameters = if (username.isEmpty()) {
        createClientCredentialsParameters(clientId, password)
    } else {
        createPasswordParameters(clientId, username, password)
    }
)

internal suspend fun HttpClient.refreshToken(tokenUrl: String, clientId: String, refreshToken: String) =
    submitForm(
        url = tokenUrl,
        formParameters = Parameters.build {
            append("client_id", clientId)
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
        }
    )

private fun createPasswordParameters(clientId: String, username: String, password: String): Parameters =
    Parameters.build {
        append("grant_type", "password")
        append("client_id", clientId)
        append("username", username)
        append("password", password)
    }

private fun createClientCredentialsParameters(clientId: String, clientSecret: String): Parameters =
    Parameters.build {
        append("grant_type", "client_credentials")
        append("client_id", clientId)
        append("client_secret", clientSecret)
    }
