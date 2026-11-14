/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthenticationProviders
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal

import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

class RateLimitTest : WordSpec({
    afterEach {
        stopKoin()
    }

    "The authorized rate limit" should {
        "count the requests of each user separately" {
            testApplication {
                configureRateLimitTestApplication()

                client.get("/authorized") { basicAuth("user1", "") }.status shouldBe HttpStatusCode.OK
                client.get("/authorized") { basicAuth("user1", "") }.status shouldBe HttpStatusCode.TooManyRequests
                client.get("/authorized") { basicAuth("user2", "") }.status shouldBe HttpStatusCode.OK
            }
        }

        "count the requests by IP if the principal has no user ID" {
            testApplication {
                configureRateLimitTestApplication()

                client.get("/authorized") { basicAuth("", "") }.status shouldBe HttpStatusCode.OK
                client.get("/authorized") { basicAuth("", "") }.status shouldBe HttpStatusCode.TooManyRequests
            }
        }
    }
})

/**
 * Configure a test application that allows a single request per rate limit, and authenticates each request with an
 * [OrtServerPrincipal] whose user ID is the username from the basic auth credentials.
 */
private fun ApplicationTestBuilder.configureRateLimitTestApplication() {
    application {
        install(Koin) {
            modules(
                module {
                    single<ApplicationConfig> {
                        MapApplicationConfig(
                            "ktor.rateLimit.public.maxRequests" to "1",
                            "ktor.rateLimit.public.windowMs" to "60000",
                            "ktor.rateLimit.authorized.maxRequests" to "1",
                            "ktor.rateLimit.authorized.windowMs" to "60000"
                        )
                    }
                }
            )
        }

        install(Authentication) {
            basic(AuthenticationProviders.TOKEN_PROVIDER) {
                validate { credentials ->
                    OrtServerPrincipal(
                        userId = credentials.name,
                        username = credentials.name,
                        fullName = null,
                        validationException = null,
                        role = null
                    )
                }
            }
        }

        configureRateLimit()

        routing {
            authenticate(AuthenticationProviders.TOKEN_PROVIDER) {
                rateLimit(RateLimitName("authorized")) {
                    get("/authorized") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
    }
}
