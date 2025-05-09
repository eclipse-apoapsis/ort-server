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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory

class UpdateTemplateOptionsIntegrationTest : AbstractIntegrationTest({
    lateinit var pluginTemplateService: PluginTemplateService

    val pluginType = PluginType.ADVISOR
    val pluginId = OssIndexFactory.descriptor.id
    val serverUrlOption = OssIndexFactory.descriptor.options.single { it.name == "serverUrl" }

    beforeEach {
        pluginTemplateService = PluginTemplateService(dbExtension.db, PluginTemplateEventStore(dbExtension.db))
    }

    "UpdateTemplateOptions" should {
        "create a template if it does not exist" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            updateTemplateOptions(pluginTemplateService)
                        }
                    }
                }

                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

                val response =
                    client.post("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                        setBody(options)
                    }

                response shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get()
                template shouldNotBeNull {
                    name shouldBe "template1"
                    pluginType shouldBe PluginType.ADVISOR
                    pluginId shouldBe OssIndexFactory.descriptor.id
                    options shouldBe options
                }
            }
        }

        "create a template if it was deleted before" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            updateTemplateOptions(pluginTemplateService)
                        }
                    }
                }

                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

                pluginTemplateService.updateOptions("template1", pluginType, pluginId, "test-user", emptyList())
                pluginTemplateService.delete("template1", pluginType, pluginId, "test-user")

                val response =
                    client.post("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                        setBody(options)
                    }

                response shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get()
                template shouldNotBeNull {
                    name shouldBe "template1"
                    pluginType shouldBe PluginType.ADVISOR
                    pluginId shouldBe OssIndexFactory.descriptor.id
                    options shouldBe options
                }
            }
        }

        "update the options of an existing template" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            updateTemplateOptions(pluginTemplateService)
                        }
                    }
                }

                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

                pluginTemplateService.updateOptions("template1", pluginType, pluginId, "test-user", emptyList())

                val response =
                    client.post("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                        setBody(options)
                    }

                response shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get()
                template shouldNotBeNull {
                    name shouldBe "template1"
                    pluginType shouldBe PluginType.ADVISOR
                    pluginId shouldBe OssIndexFactory.descriptor.id
                    options shouldBe options
                }
            }
        }

        "normalize the plugin ID" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            updateTemplateOptions(pluginTemplateService)
                        }
                    }
                }

                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

                val response =
                    client.post("/admin/plugins/$pluginType/${pluginId.uppercase()}/templates/template1") {
                        setBody(options)
                    }

                response shouldHaveStatus HttpStatusCode.OK

                val result = pluginTemplateService.getTemplate("template1", pluginType, pluginId)
                result.isOk shouldBe true

                val template = result.get()
                template shouldNotBeNull {
                    name shouldBe "template1"
                    pluginType shouldBe PluginType.ADVISOR
                    pluginId shouldBe OssIndexFactory.descriptor.id
                    options shouldBe options
                }
            }
        }

        "fail if a plugin option is invalid" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            updateTemplateOptions(pluginTemplateService)
                        }
                    }
                }

                val nonExistingOption = PluginOptionTemplate(
                    option = "invalidOption",
                    type = PluginOptionType.STRING,
                    value = "https://example.org",
                    isFinal = true
                )

                client.post("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                    setBody(listOf(nonExistingOption))
                } shouldHaveStatus HttpStatusCode.BadRequest

                val wrongOptionType = PluginOptionTemplate(
                    option = serverUrlOption.name,
                    type = PluginOptionType.LONG,
                    value = "https://example.org",
                    isFinal = true
                )

                client.post("/admin/plugins/$pluginType/$pluginId/templates/template1") {
                    setBody(listOf(wrongOptionType))
                } shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
