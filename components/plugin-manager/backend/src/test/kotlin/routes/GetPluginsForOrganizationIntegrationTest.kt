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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.routes

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PreconfiguredPluginDescriptor
import org.eclipse.apoapsis.ortserver.model.OrganizationId

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class GetPluginsForOrganizationIntegrationTest : PluginManagerIntegrationTest({
    var organizationId = OrganizationId(-1L)

    val pluginType = PluginType.ADVISOR
    val pluginId = VulnerableCodeFactory.descriptor.id

    beforeEach {
        organizationId = OrganizationId(dbExtension.fixtures.organization.id)
    }

    "GetPluginsForOrganization" should {
        "include all plugins" {
            pluginManagerTestApplication { client ->
                val allPlugins = pluginService.getPlugins().map { it.type to it.id }

                val response = client.get("/organizations/${organizationId.value}/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                val responsePlugins = response.body<List<PreconfiguredPluginDescriptor>>().map { it.type to it.id }

                responsePlugins shouldContainExactlyInAnyOrder allPlugins
            }
        }

        "not include restricted plugins without a template" {
            pluginManagerTestApplication { client ->
                client.post("/admin/plugins/$pluginType/$pluginId/restrict")

                val response = client.get("/organizations/${organizationId.value}/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<PreconfiguredPluginDescriptor>>().map { it.id } shouldNotContain pluginId
            }
        }

        "include restricted plugins with an organization template" {
            pluginManagerTestApplication { client ->
                client.post("/admin/plugins/$pluginType/$pluginId/restrict")

                pluginTemplateService.create(
                    templateName = "template",
                    pluginType = pluginType,
                    pluginId = pluginId,
                    userId = "user",
                    options = emptyList()
                ).isOk shouldBe true

                pluginTemplateService.addOrganization(
                    templateName = "template",
                    pluginType = pluginType,
                    pluginId = pluginId,
                    organizationId = organizationId,
                    userId = "user"
                ).isOk shouldBe true

                val response = client.get("/organizations/${organizationId.value}/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<PreconfiguredPluginDescriptor>>().map { it.id } shouldContain pluginId
            }
        }

        "replace reporters with the report definitions from the config context" {
            pluginManagerTestApplication { client ->
                val response = client.get("/organizations/${organizationId.value}/plugins") {
                    parameter("configContext", "src/test/resources/config")
                }

                response shouldHaveStatus HttpStatusCode.OK
                val reporterIds = response.body<List<PreconfiguredPluginDescriptor>>()
                    .filter { it.type == PluginType.REPORTER }
                    .map { it.id }

                reporterIds shouldContain "customTemplate"
                reporterIds shouldContain "webApp"
                reporterIds shouldNotContain "PdfTemplate"
                reporterIds shouldNotContain "WebApp"
            }
        }

        "not include report definitions using a disabled reporter" {
            pluginManagerTestApplication { client ->
                client.post("/admin/plugins/${PluginType.REPORTER}/PdfTemplate/disable")

                val response = client.get("/organizations/${organizationId.value}/plugins") {
                    parameter("configContext", "src/test/resources/config")
                }

                response shouldHaveStatus HttpStatusCode.OK
                val reporterIds = response.body<List<PreconfiguredPluginDescriptor>>()
                    .filter { it.type == PluginType.REPORTER }
                    .map { it.id }

                reporterIds shouldNotContain "customTemplate"
                reporterIds shouldNotContain "PdfTemplate"
            }
        }

        "not include report definitions using a restricted reporter without a template" {
            pluginManagerTestApplication { client ->
                client.post("/admin/plugins/${PluginType.REPORTER}/PdfTemplate/restrict")

                val response = client.get("/organizations/${organizationId.value}/plugins") {
                    parameter("configContext", "src/test/resources/config")
                }

                response shouldHaveStatus HttpStatusCode.OK
                val reporterIds = response.body<List<PreconfiguredPluginDescriptor>>()
                    .filter { it.type == PluginType.REPORTER }
                    .map { it.id }

                reporterIds shouldNotContain "customTemplate"
                reporterIds shouldNotContain "PdfTemplate"
            }
        }

        "respond with NotFound if the organization does not exist" {
            pluginManagerTestApplication { client ->
                val response = client.get("/organizations/999999/plugins")

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }
    }
})
