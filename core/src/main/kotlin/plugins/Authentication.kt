/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Payload

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.config.ApplicationConfig

import java.net.URL
import java.util.concurrent.TimeUnit

import org.koin.ktor.ext.inject

/**
 * Configure the authentication for this server application.
 */
fun Application.configureAuthentication() {
    val config: ApplicationConfig by inject()

    val issuer = config.property("jwt.issuer").getString()
    val jwksUri = URL(config.property("jwt.jwksUri").getString())
    val configuredRealm = config.property("jwt.realm").getString()
    val jwkProvider = JwkProviderBuilder(jwksUri)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt(SecurityConfigurations.token) {
            realm = configuredRealm
            verifier(jwkProvider, issuer) {
                acceptLeeway(10)
            }

            validate { credential ->
                credential.payload.takeIf(this@configureAuthentication::validateJwtPayload)?.let { payload ->
                    JWTPrincipal(payload)
                }
            }
        }
    }
}

/**
 * Validate the [payload] of the current JWT. Return *true* if it is valid.
 */
private fun Application.validateJwtPayload(payload: Payload): Boolean =
    payload.audience.contains(environment.config.property("jwt.audience").getString())

/**
 * An object defining the different security configurations supported by this application. These configurations
 * correspond to the different authentication schemes used by endpoints.
 */
object SecurityConfigurations {
    /**
     * Security configuration for the normal API endpoints called by users. Users must provide a valid JWT in order to
     * use an API endpoint.
     */
    const val token = "token"
}
