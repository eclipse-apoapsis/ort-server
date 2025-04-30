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

import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.serialization
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.hasRole
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class DisablePluginIntegrationTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    beforeSpec {
        mockkStatic(RoutingContext::hasRole)
    }

    afterSpec { unmockkAll() }

    "DisablePlugin" should {
        "return Accepted if the plugin was disabled" {
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
                            enablePlugin(eventStore)
                        }
                    }
                }

                val pluginType = PluginType.ADVISOR
                val pluginId = VulnerableCodeFactory.descriptor.id

                val client = createJsonClient()

                // Disable the plugin first because it is enabled by default.
                client.post("/admin/plugins/$pluginType/$pluginId/disable") shouldHaveStatus HttpStatusCode.Accepted

                // Verify again that the plugin can be disabled after it was enabled.
                client.post("/admin/plugins/$pluginType/$pluginId/enable")
                client.post("/admin/plugins/$pluginType/$pluginId/disable") shouldHaveStatus HttpStatusCode.Accepted
            }
        }

        "return NotFound if the plugin is not installed" {
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
                        }
                    }
                }

                val pluginType = PluginType.ADVISOR

                val client = createJsonClient()

                client.post("/admin/plugins/$pluginType/unknown/disable") shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "return NotModified if the plugin was already disabled" {
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
                            enablePlugin(eventStore)
                        }
                    }
                }

                val pluginType = PluginType.ADVISOR
                val pluginId = VulnerableCodeFactory.descriptor.id

                val client = createJsonClient()

                // Disable the plugin first because it is enabled by default.
                client.post("/admin/plugins/$pluginType/$pluginId/disable")
                client.post("/admin/plugins/$pluginType/$pluginId/disable") shouldHaveStatus HttpStatusCode.NotModified
            }
        }
    }
})
