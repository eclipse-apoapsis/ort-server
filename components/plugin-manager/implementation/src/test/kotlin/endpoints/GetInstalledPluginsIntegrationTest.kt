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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.serialization
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.hasRole
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.NpmFactory

class DummyConfig(val principal: OrtPrincipal) : AuthenticationProvider.Config("test")

class FakeAuthenticationProvider(val config: DummyConfig) : AuthenticationProvider(config) {
    override suspend fun onAuthenticate(context: AuthenticationContext) {
        context.principal(config.principal)
    }
}

class GetInstalledPluginsIntegrationTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    beforeSpec {
        mockkStatic(RoutingContext::hasRole)
    }

    afterSpec { unmockkAll() }

    "GetInstalledPlugins" should {
        "return all installed ORT plugins" {
            val principal = mockk<OrtPrincipal> {
                every { hasRole(any()) } returns true
            }

            testApplication {
                application {
                    install(ContentNegotiation) {
                        serialization(ContentType.Application.Json, Json)
                    }

                    install(Authentication) {
                        register(FakeAuthenticationProvider(DummyConfig(principal)))
                    }

                    routing {
                        authenticate("test") {
                            getInstalledPlugins(PluginService(dbExtension.db))
                        }
                    }
                }

                val client = createJsonClient()
                val response = client.get("/admin/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                val pluginDescriptors = response.body<List<PluginDescriptor>>()
                enumValues<PluginType>().forEach { pluginType ->
                    pluginDescriptors.filter { it.type == pluginType } shouldNot beEmpty()
                }
            }
        }

        "return if plugins are enabled or disabled" {
            val principal = mockk<OrtPrincipal> {
                every { getUserId() } returns "userId"
                every { hasRole(any()) } returns true
            }

            val eventStore = PluginEventStore(dbExtension.db)

            testApplication {
                application {
                    install(ContentNegotiation) {
                        serialization(ContentType.Application.Json, Json)
                    }

                    install(Authentication) {
                        register(FakeAuthenticationProvider(DummyConfig(principal)))
                    }

                    routing {
                        authenticate("test") {
                            disablePlugin(eventStore)
                            getInstalledPlugins(PluginService(dbExtension.db))
                        }
                    }
                }

                val npmType = PluginType.PACKAGE_MANAGER
                val npmId = NpmFactory.descriptor.id
                val vulnerableCodeType = PluginType.ADVISOR
                val vulnerableCodeId = VulnerableCodeFactory.descriptor.id

                val client = createJsonClient()
                client.post("/admin/plugins/$npmType/$npmId/disable") shouldHaveStatus HttpStatusCode.Accepted
                val response = client.get("/admin/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                val descriptors = response.body<List<PluginDescriptor>>()

                descriptors.find { it.type == npmType && it.id == npmId }?.enabled shouldBe false
                descriptors.find { it.type == vulnerableCodeType && it.id == vulnerableCodeId }?.enabled shouldBe true
            }
        }
    }
})

fun ApplicationTestBuilder.createJsonClient() = createClient {
    install(ClientContentNegotiation) {
        json(Json)
    }
}
