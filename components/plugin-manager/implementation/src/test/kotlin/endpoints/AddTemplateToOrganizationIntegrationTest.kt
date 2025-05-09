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

import com.github.michaelbull.result.get

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory

class AddTemplateToOrganizationIntegrationTest : AbstractIntegrationTest({
    lateinit var pluginTemplateService: PluginTemplateService

    val pluginType = PluginType.ADVISOR
    val pluginId = OssIndexFactory.descriptor.id

    beforeEach {
        pluginTemplateService = PluginTemplateService(dbExtension.db, PluginTemplateEventStore(dbExtension.db))
    }

    "AddTemplateToOrganization" should {
        "add a template to an organization if it exists" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            addTemplateToOrganization(pluginTemplateService)
                        }
                    }
                }

                pluginTemplateService.updateOptions("template1", pluginType, pluginId, "test-user", emptyList())

                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/template1/addToOrganization?organizationId=1"
                ) shouldHaveStatus HttpStatusCode.OK
                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/template1/addToOrganization?organizationId=2"
                ) shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get().shouldNotBeNull()
                template.organizationIds should containExactlyInAnyOrder(1, 2)
            }
        }

        "return NotFound if the template does not exist" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            addTemplateToOrganization(pluginTemplateService)
                        }
                    }
                }

                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/non-existing-template" +
                            "/addToOrganization?organizationId=1"
                ) shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "return NotFound if the organization does not exist" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            addTemplateToOrganization(pluginTemplateService)
                        }
                    }
                }

                pluginTemplateService.updateOptions("template1", pluginType, pluginId, "test-user", emptyList())

                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/template1/addToOrganization?organizationId=999"
                ) shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "return BadRequest if the template is already assigned to the organization" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            addTemplateToOrganization(pluginTemplateService)
                        }
                    }
                }

                pluginTemplateService.updateOptions("template1", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.addOrganization("template1", pluginType, pluginId, 1, "test-user")

                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/template1/addToOrganization?organizationId=1"
                ) shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "normalize the plugin ID" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            addTemplateToOrganization(pluginTemplateService)
                        }
                    }
                }

                pluginTemplateService.updateOptions("template1", pluginType, pluginId, "test-user", emptyList())

                client.post(
                    "/admin/plugins/$pluginType/${pluginId.uppercase()}/templates/template1" +
                            "/addToOrganization?organizationId=1"
                ) shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get().shouldNotBeNull()
                template.organizationIds should containExactlyInAnyOrder(1)
            }
        }
    }
})
