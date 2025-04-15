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

package org.eclipse.apoapsis.ortserver.core.apiDocs

import io.github.smiley4.ktoropenapi.config.RouteConfig

import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.model.authorization.OpenIdProviderConfiguration

val getOpenIdProviderConfiguration: RouteConfig.() -> Unit = {
    operationId = "GetOpenIdProviderConfiguration"
    summary = "GetOpenIdProviderSummary"
    description = "Retrieve the OpenID Provider Configuration"
    tags = listOf("Authentication")

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<OpenIdProviderConfiguration> {
                example("Get OpenID Provider Configuration") {
                    value = OpenIdProviderConfiguration(
                        authorizationEndpoint =
                            "https://auth.example.com/auth/realms/master/protocol/openid-connect/certs",
                        idTokenSigningAlgValuesSupported = listOf("RS256"),
                        issuer = "https://auth.example.com/auth/realms/master",
                        jwksUri = "https://auth.example.com/auth/realms/master/protocol/openid-connect/certs",
                        responseTypesSupported = listOf("code", "id_token", "token id_token"),
                        scopesSupported = listOf("ort-server-client"),
                        subjectTypesSupported = listOf("public"),
                        tokenEndpoint = "https://auth.example.com/auth/realms/master/protocol/openid-connect/certs"
                    )
                }
            }
        }
    }
}
