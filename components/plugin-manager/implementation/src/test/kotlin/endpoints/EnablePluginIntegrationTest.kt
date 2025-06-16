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

import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.pluginManagerRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class EnablePluginIntegrationTest : AbstractIntegrationTest({
    lateinit var eventStore: PluginEventStore
    lateinit var pluginService: PluginService

    beforeEach {
        eventStore = PluginEventStore(dbExtension.db)
        pluginService = PluginService(dbExtension.db)
    }

    "EnablePlugin" should {
        "return Accepted if the plugin was enabled" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            pluginManagerRoutes(eventStore, pluginService)
                        }
                    }
                }

                val pluginType = PluginType.ADVISOR
                val pluginId = VulnerableCodeFactory.descriptor.id

                // Disable the plugin first because it is enabled by default.
                client.post("/admin/plugins/$pluginType/$pluginId/disable")
                client.post("/admin/plugins/$pluginType/$pluginId/enable") shouldHaveStatus HttpStatusCode.Accepted

                // Verify again that the plugin can be enabled after it was disabled.
                client.post("/admin/plugins/$pluginType/$pluginId/disable")
                client.post("/admin/plugins/$pluginType/$pluginId/enable") shouldHaveStatus HttpStatusCode.Accepted
            }
        }

        "return NotFound if the plugin is not installed" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            pluginManagerRoutes(eventStore, pluginService)
                        }
                    }
                }

                val pluginType = PluginType.ADVISOR

                client.post("/admin/plugins/$pluginType/unknown/enable") shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "return NotModified if the plugin was already enabled" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            pluginManagerRoutes(eventStore, pluginService)
                        }
                    }
                }

                val pluginType = PluginType.ADVISOR
                val pluginId = VulnerableCodeFactory.descriptor.id

                client.post("/admin/plugins/$pluginType/$pluginId/enable") shouldHaveStatus HttpStatusCode.NotModified

                // Verify again after disabling and re-enabling the plugin.
                client.post("/admin/plugins/$pluginType/$pluginId/disable")
                client.post("/admin/plugins/$pluginType/$pluginId/enable")
                client.post("/admin/plugins/$pluginType/$pluginId/enable") shouldHaveStatus HttpStatusCode.NotModified
            }
        }
    }
})
