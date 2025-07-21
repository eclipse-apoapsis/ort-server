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

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory

class GetTemplatesIntegrationTest : PluginManagerIntegrationTest({
    val pluginType = PluginType.ADVISOR
    val pluginId = OssIndexFactory.descriptor.id

    "GetTemplates" should {
        "return the templates for a plugin in alphabetic order" {
            pluginManagerTestApplication { client ->
                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.create("template3", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.create("template2", pluginType, pluginId, "test-user", emptyList())

                val response = client.get("/admin/plugins/$pluginType/$pluginId/templates")
                response shouldHaveStatus HttpStatusCode.OK

                val templates = response.body<List<PluginTemplate>>()

                templates should containExactly(
                    PluginTemplate(
                        name = "template1",
                        pluginType = pluginType,
                        pluginId = pluginId,
                        options = emptyList(),
                        isGlobal = false
                    ),
                    PluginTemplate(
                        name = "template2",
                        pluginType = pluginType,
                        pluginId = pluginId,
                        options = emptyList(),
                        isGlobal = false
                    ),
                    PluginTemplate(
                        name = "template3",
                        pluginType = pluginType,
                        pluginId = pluginId,
                        options = emptyList(),
                        isGlobal = false
                    )
                )
            }
        }

        "return BadRequest if the plugin does not exist" {
            pluginManagerTestApplication { client ->
                client.get(
                    "/admin/plugins/$pluginType/non-existing-plugin/templates"
                ) shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "normalize the plugin ID" {
            pluginManagerTestApplication { client ->
                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.create("template2", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.create("template3", pluginType, pluginId, "test-user", emptyList())

                val response = client.get(
                    "/admin/plugins/$pluginType/${pluginId.uppercase()}/templates"
                )
                response shouldHaveStatus HttpStatusCode.OK

                val templates = response.body<List<PluginTemplate>>()

                templates should containExactlyInAnyOrder(
                    PluginTemplate(
                        name = "template1",
                        pluginType = pluginType,
                        pluginId = pluginId,
                        options = emptyList(),
                        isGlobal = false
                    ),
                    PluginTemplate(
                        name = "template2",
                        pluginType = pluginType,
                        pluginId = pluginId,
                        options = emptyList(),
                        isGlobal = false
                    ),
                    PluginTemplate(
                        name = "template3",
                        pluginType = pluginType,
                        pluginId = pluginId,
                        options = emptyList(),
                        isGlobal = false
                    )
                )
            }
        }
    }
})
