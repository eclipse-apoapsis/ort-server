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
import io.kotest.matchers.shouldBe

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.pluginManagerRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory

class DeleteTemplateIntegrationTest : AbstractIntegrationTest({
    lateinit var pluginEventStore: PluginEventStore
    lateinit var pluginService: PluginService
    lateinit var pluginTemplateService: PluginTemplateService

    val pluginType = PluginType.ADVISOR
    val pluginId = OssIndexFactory.descriptor.id

    beforeEach {
        pluginEventStore = PluginEventStore(dbExtension.db)
        pluginService = PluginService(dbExtension.db)
        pluginTemplateService = PluginTemplateService(
            dbExtension.db,
            PluginTemplateEventStore(dbExtension.db),
            pluginService,
            dbExtension.fixtures.organizationRepository
        )
    }

    "DeleteTemplate" should {
        "delete a template if it exists" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
                pluginTemplateService.updateOptions("template1", pluginType, pluginId, "test-user", emptyList())

                client.delete("/admin/plugins/$pluginType/$pluginId/templates/template1") shouldHaveStatus
                        HttpStatusCode.OK

                pluginTemplateService.getTemplate("template1", pluginType, pluginId).isErr shouldBe true
            }
        }

        "return NotFound if the template does not exist" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
                client.delete("/admin/plugins/$pluginType/$pluginId/templates/non-existing") shouldHaveStatus
                        HttpStatusCode.NotFound
            }
        }

        "normalize the plugin ID" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
                pluginTemplateService.updateOptions("template1", pluginType, pluginId, "test-user", emptyList())

                client.delete("/admin/plugins/$pluginType/${pluginId.uppercase()}/templates/template1") shouldHaveStatus
                        HttpStatusCode.OK

                pluginTemplateService.getTemplate("template1", pluginType, pluginId).isErr shouldBe true
            }
        }
    }
})
