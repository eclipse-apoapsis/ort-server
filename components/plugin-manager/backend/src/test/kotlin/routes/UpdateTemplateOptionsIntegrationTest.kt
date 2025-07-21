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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class UpdateTemplateOptionsIntegrationTest : PluginManagerIntegrationTest({
    val pluginType = PluginType.ADVISOR
    val pluginId = VulnerableCodeFactory.descriptor.id
    val serverUrlOption = VulnerableCodeFactory.descriptor.options.single { it.name == "serverUrl" }
    val readTimeoutOption = VulnerableCodeFactory.descriptor.options.single { it.name == "readTimeout" }

    "UpdateTemplateOptions" should {
        "fail if the template does not exist" {
            pluginManagerTestApplication { client ->
                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

                val response =
                    client.put("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                        setBody(options)
                    }

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "fail if the template was deleted before" {
            pluginManagerTestApplication { client ->
                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.delete("template1", pluginType, pluginId, "test-user")

                val response =
                    client.put("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                        setBody(options)
                    }

                response shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "update the options of an existing template" {
            pluginManagerTestApplication { client ->
                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())

                val response =
                    client.put("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                        setBody(options)
                    }

                response shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get()
                template shouldNotBeNull {
                    name shouldBe "template1"
                    this.pluginType shouldBe PluginType.ADVISOR
                    this.pluginId shouldBe pluginId
                    this.options shouldBe options
                }
            }
        }

        "normalize the plugin ID" {
            pluginManagerTestApplication { client ->
                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())

                val response =
                    client.put("/admin/plugins/$pluginType/${pluginId.uppercase()}/templates/template1") {
                        setBody(options)
                    }

                response shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get()
                template shouldNotBeNull {
                    name shouldBe "template1"
                    this.pluginType shouldBe PluginType.ADVISOR
                    this.pluginId shouldBe pluginId
                    this.options shouldBe options
                }
            }
        }

        "fail if a plugin option is invalid" {
            pluginManagerTestApplication { client ->
                val nonExistingOption = PluginOptionTemplate(
                    option = "invalidOption",
                    type = PluginOptionType.STRING,
                    value = "https://example.org",
                    isFinal = true
                )

                pluginTemplateService.create("template1", pluginType, pluginId, "test-user", emptyList())

                client.put("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                    setBody(listOf(nonExistingOption))
                } shouldHaveStatus HttpStatusCode.BadRequest

                val wrongOptionType = PluginOptionTemplate(
                    option = serverUrlOption.name,
                    type = PluginOptionType.LONG,
                    value = "https://example.org",
                    isFinal = true
                )

                client.put("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                    setBody(listOf(wrongOptionType))
                } shouldHaveStatus HttpStatusCode.BadRequest

                val invalidOptionValue = PluginOptionTemplate(
                    option = readTimeoutOption.name,
                    type = PluginOptionType.LONG,
                    value = "invalidValue",
                    isFinal = true
                )

                client.put("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                    setBody(listOf(invalidOptionValue))
                } shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
