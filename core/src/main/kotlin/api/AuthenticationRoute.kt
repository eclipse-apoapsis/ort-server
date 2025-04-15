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

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import kotlinx.serialization.json.Json
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi

import org.eclipse.apoapsis.ortserver.clients.keycloak.createDefaultHttpClient
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOpenIdProviderConfiguration
import org.eclipse.apoapsis.ortserver.model.authorization.OpenIdProviderConfiguration

import org.koin.ktor.ext.inject

/**
 * Endpoints related to the authentication of users with the system.
 */
fun Route.authentication() = route("auth") {
    val applicationConfig by inject<ApplicationConfig>()
    val json by inject<Json>()

    val httpClient = createDefaultHttpClient(json)

    get(".well-known/openid-configuration", getOpenIdProviderConfiguration) {
        val issuerUrl = applicationConfig.property("jwt.issuer").getString()
        val openIdConfigUrl = "$issuerUrl/.well-known/openid-configuration"

        val openIdConfig = httpClient.get(openIdConfigUrl).body<OpenIdProviderConfiguration>()

        call.respond(HttpStatusCode.OK, openIdConfig.mapToApi())
    }
}
