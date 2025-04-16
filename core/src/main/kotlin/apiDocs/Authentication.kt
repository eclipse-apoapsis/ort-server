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

import org.eclipse.apoapsis.ortserver.model.authentication.OidcConfig

val getCliOidcConfig: RouteConfig.() -> Unit = {
    operationId = "getCliOidcConfig"
    summary = "Get the OpenID configuration for the ORT Server CLI"
    tags = listOf("Authentication")

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<OidcConfig> {
                example("Get the OIDC configuration required for the ORT Server CLI") {
                    value = OidcConfig(
                        accessTokenUrl = "https://auth.example.com/auth/realms/master/protocol/openid-connect/token",
                        clientId = "ort-server-api"
                    )
                }
            }
        }
    }
}
