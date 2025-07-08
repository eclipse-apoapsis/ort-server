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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.pluginManagerRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory

class RemoveTemplateFromOrganizationIntegrationTest : AbstractIntegrationTest({
    lateinit var pluginEventStore: PluginEventStore
    lateinit var pluginService: PluginService
    lateinit var pluginTemplateService: PluginTemplateService
    var organizationId: Long = 0

    val pluginType = PluginType.ADVISOR
    val pluginId = OssIndexFactory.descriptor.id

    beforeEach {
        organizationId = dbExtension.fixtures.createOrganization().id
        pluginEventStore = PluginEventStore(dbExtension.db)
        pluginService = PluginService(dbExtension.db)
        pluginTemplateService = PluginTemplateService(
            dbExtension.db,
            PluginTemplateEventStore(dbExtension.db),
            pluginService,
            dbExtension.fixtures.organizationRepository
        )
    }

    "RemoveTemplateFromOrganization" should {
        "remove a template from an organization if it exists" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
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
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/nonExistentTemplate" +
                            "/removeFromOrganization?organizationId=1"
                ) shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "return BadRequest if the template is not assigned to the organization" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())

                client.post(
                    "/admin/plugins/$pluginType/$pluginId/templates/template1/removeFromOrganization?organizationId=1"
                ) shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "normalize the plugin ID" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
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
