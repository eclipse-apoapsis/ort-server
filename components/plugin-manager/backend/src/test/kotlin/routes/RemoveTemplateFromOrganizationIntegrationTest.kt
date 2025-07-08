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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.routes

import com.github.michaelbull.result.get

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory

class RemoveTemplateFromOrganizationIntegrationTest : PluginManagerIntegrationTest({
    var organizationId: Long = 0

    val pluginType = PluginType.ADVISOR
    val pluginId = OssIndexFactory.descriptor.id

    beforeEach {
        organizationId = dbExtension.fixtures.createOrganization().id
    }

    "RemoveTemplateFromOrganization" should {
        "remove a template from an organization if it exists" {
            pluginManagerTestApplication { client ->
                val organizationId2 = dbExtension.fixtures.createOrganization(name = "org2").id

                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.addOrganization("template1", pluginType, pluginId, organizationId, "test-user")
                pluginTemplateService.addOrganization("template1", pluginType, pluginId, organizationId2, "test-user")

                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/template1" +
                            "/removeFromOrganization?organizationId=$organizationId"
                ) shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get().shouldNotBeNull()
                template.organizationIds should containExactly(organizationId2)
            }
        }

        "return NotFound if the template does not exist" {
            pluginManagerTestApplication { client ->
                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/nonExistentTemplate" +
                            "/removeFromOrganization?organizationId=1"
                ) shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "return BadRequest if the template is not assigned to the organization" {
            pluginManagerTestApplication { client ->
                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())

                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/template1/removeFromOrganization?organizationId=1"
                ) shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "normalize the plugin ID" {
            pluginManagerTestApplication { client ->
                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.addOrganization("template1", pluginType, pluginId, organizationId, "test-user")

                client.post(
                    "/admin/plugins/$pluginType/${pluginId.uppercase()}/templates/template1" +
                            "/removeFromOrganization?organizationId=$organizationId"
                ) shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get().shouldNotBeNull()
                template.organizationIds should beEmpty()
            }
        }
    }
})
