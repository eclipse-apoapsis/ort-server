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

package org.eclipse.apoapsis.ortserver.shared.ktorutils

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.serialization
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.RoutingContext
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.appendIfNameAbsent

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.hasRole
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.utils.test.Integration

/**
 * A base class for integration tests that provides a database connection and a mock authentication provider.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class AbstractIntegrationTest(body: AbstractIntegrationTest.() -> Unit) : WordSpec() {
    val dbExtension = extension(DatabaseTestExtension())

    val principal = mockk<OrtPrincipal> {
        every { getUserId() } returns "userId"
        every { hasRole(any()) } returns true
    }

    init {
        tags(Integration)
        body()
    }

    override suspend fun beforeSpec(spec: Spec) {
        mockkStatic(RoutingContext::hasRole)
    }

    override fun afterSpec(f: suspend (Spec) -> Unit) {
        unmockkAll()
    }

    fun integrationTestApplication(block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit) {
        testApplication {
            application {
                install(ContentNegotiation) {
                    serialization(ContentType.Application.Json, Json)
                }

                install(Authentication) {
                    register(FakeAuthenticationProvider(DummyConfig(principal)))
                }
            }

            block(createJsonClient())
        }
    }
}

fun ApplicationTestBuilder.createJsonClient() = createClient {
    install(ClientContentNegotiation) {
        json(Json)
    }

    defaultRequest {
        headers.appendIfNameAbsent(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}

class DummyConfig(val principal: OrtPrincipal) : AuthenticationProvider.Config("test")

class FakeAuthenticationProvider(val config: DummyConfig) : AuthenticationProvider(config) {
    override suspend fun onAuthenticate(context: AuthenticationContext) {
        context.principal(config.principal)
    }
}
