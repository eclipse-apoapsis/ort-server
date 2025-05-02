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

package org.eclipse.apoapsis.ortserver.core.api

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.core.apiDocs.getCliOidcConfig
import org.eclipse.apoapsis.ortserver.model.authentication.OidcConfig

/**
 * Endpoints related to the authentication of users with the system.
 */
fun Route.authentication(applicationConfig: ApplicationConfig) = route("auth") {
    route("oidc-config/cli") {
        get(getCliOidcConfig) {
            val oidcConfig = OidcConfig(
                accessTokenUrl = applicationConfig.property("keycloak.accessTokenUrl").getString(),
                clientId = applicationConfig.property("jwt.audience").getString(),
            )

            call.respond(HttpStatusCode.OK, oidcConfig.mapToApi())
        }
    }
}
