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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory

class GetTemplateIntegrationTest : PluginManagerIntegrationTest({
    val pluginType = PluginType.ADVISOR
    val pluginId = OssIndexFactory.descriptor.id

    "GetTemplate" should {
        "return a template if it exists" {
            pluginManagerTestApplication { client ->
                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())

                val response = client.get("/admin/plugins/$pluginType/$pluginId/templates/template1")
                response shouldHaveStatus HttpStatusCode.OK

                val template = response.body<PluginTemplate>()
                template.shouldNotBeNull {
                    name shouldBe "template1"
                    this.pluginType shouldBe pluginType
                    this.pluginId shouldBe pluginId
                }
            }
        }

        "return NotFound if the template does not exist" {
            pluginManagerTestApplication { client ->
                client.get(
                    "/admin/plugins/$pluginType/$pluginId/templates/template1"
                ) shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "normalize the plugin ID" {
            pluginManagerTestApplication { client ->
                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())

                val response = client.get(
                    "/admin/plugins/$pluginType/${pluginId.uppercase()}/templates/template1"
                )
                response shouldHaveStatus HttpStatusCode.OK

                val template = response.body<PluginTemplate>()
                template.shouldNotBeNull {
                    name shouldBe "template1"
                    this.pluginType shouldBe pluginType
                    this.pluginId shouldBe pluginId
                }
            }
        }
    }
})
