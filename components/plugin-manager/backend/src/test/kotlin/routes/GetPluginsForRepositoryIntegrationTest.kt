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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PreconfiguredPluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PreconfiguredPluginOption

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class GetPluginsForRepositoryIntegrationTest : PluginManagerIntegrationTest({
    var organizationId = -1L
    var repositoryId = -1L

    val pluginType = PluginType.ADVISOR
    val pluginDescriptor = VulnerableCodeFactory.descriptor
    val pluginId = pluginDescriptor.id

    beforeEach {
        organizationId = dbExtension.fixtures.organization.id
        repositoryId = dbExtension.fixtures.repository.id
    }

    "GetPluginsForRepository" should {
        "include all plugins" {
            pluginManagerTestApplication { client ->
                val allPlugins = pluginService.getPlugins().map { it.type to it.id }

                val response = client.get("/repositories/$repositoryId/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                val responsePlugins = response.body<List<PreconfiguredPluginDescriptor>>().map { it.type to it.id }

                responsePlugins shouldContainExactlyInAnyOrder allPlugins
            }
        }

        "not include globally disabled plugins" {
            pluginManagerTestApplication { client ->
                client.post("/admin/plugins/$pluginType/$pluginId/disable")

                val response = client.get("/repositories/$repositoryId/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<PreconfiguredPluginDescriptor>>().map { it.id } shouldNotContain pluginId
            }
        }

        "return correct values for plugins without templates" {
            pluginManagerTestApplication { client ->
                val response = client.get("/repositories/$repositoryId/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<PreconfiguredPluginDescriptor>>()
                    .find { it.id == pluginId && it.type == pluginType }
                    .shouldNotBeNull {
                        id shouldBe pluginId
                        type shouldBe pluginType
                        displayName shouldBe pluginDescriptor.displayName
                        description shouldBe pluginDescriptor.description
                        options shouldContainExactlyInAnyOrder pluginDescriptor.options.map {
                            PreconfiguredPluginOption(
                                name = it.name,
                                description = it.description,
                                type = it.type.mapToApi(),
                                defaultValue = it.defaultValue,
                                isFixed = false,
                                isNullable = it.isNullable,
                                isRequired = it.isRequired
                            )
                        }
                    }
            }
        }

        "take global templates into account" {
            pluginManagerTestApplication { client ->
                val templateName = "template"
                val option = "serverUrl"
                val serverUrl = "https://example.org/api/"

                pluginTemplateService.create(
                    templateName = templateName,
                    pluginType = pluginType,
                    pluginId = pluginId,
                    userId = "user",
                    options = listOf(
                        PluginOptionTemplate(
                            option = option,
                            type = PluginOptionType.STRING,
                            value = serverUrl,
                            isFinal = true
                        )
                    )
                ).isOk shouldBe true

                pluginTemplateService.enableGlobal(
                    templateName = templateName,
                    pluginType = pluginType,
                    pluginId = pluginId,
                    userId = "user"
                ).isOk shouldBe true

                val response = client.get("/repositories/$repositoryId/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<PreconfiguredPluginDescriptor>>()
                    .find { it.id == pluginId && it.type == pluginType }
                    .shouldNotBeNull {
                        options.find { it.name == option }.shouldNotBeNull {
                            defaultValue shouldBe serverUrl
                            isFixed shouldBe true
                        }
                    }
            }
        }

        "take organization templates into account" {
            pluginManagerTestApplication { client ->
                val templateName = "template"
                val option = "serverUrl"
                val serverUrl = "https://example.org/api/"

                pluginTemplateService.create(
                    templateName = templateName,
                    pluginType = pluginType,
                    pluginId = pluginId,
                    userId = "user",
                    options = listOf(
                        PluginOptionTemplate(
                            option = option,
                            type = PluginOptionType.STRING,
                            value = serverUrl,
                            isFinal = true
                        )
                    )
                ).isOk shouldBe true

                pluginTemplateService.addOrganization(
                    templateName = templateName,
                    pluginType = pluginType,
                    pluginId = pluginId,
                    organizationId = organizationId,
                    userId = "user"
                ).isOk shouldBe true

                val response = client.get("/repositories/$repositoryId/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<PreconfiguredPluginDescriptor>>()
                    .find { it.id == pluginId && it.type == pluginType }
                    .shouldNotBeNull {
                        options.find { it.name == option }.shouldNotBeNull {
                            defaultValue shouldBe serverUrl
                            isFixed shouldBe true
                        }
                    }
            }
        }

        "prioritize organization templates over global templates" {
            pluginManagerTestApplication { client ->
                val option = "serverUrl"
                val globalTemplateName = "global template"
                val globalServerUrl = "https://example.org/api/"
                val organizationTemplateName = "organization template"
                val organizationServerUrl = "https://organization.example.org/api/"

                pluginTemplateService.create(
                    templateName = globalTemplateName,
                    pluginType = pluginType,
                    pluginId = pluginId,
                    userId = "user",
                    options = listOf(
                        PluginOptionTemplate(
                            option = option,
                            type = PluginOptionType.STRING,
                            value = globalServerUrl,
                            isFinal = false
                        )
                    )
                ).isOk shouldBe true

                pluginTemplateService.enableGlobal(
                    templateName = globalTemplateName,
                    pluginType = pluginType,
                    pluginId = pluginId,
                    userId = "user"
                ).isOk shouldBe true

                pluginTemplateService.create(
                    templateName = organizationTemplateName,
                    pluginType = pluginType,
                    pluginId = pluginId,
                    userId = "user",
                    options = listOf(
                        PluginOptionTemplate(
                            option = option,
                            type = PluginOptionType.STRING,
                            value = organizationServerUrl,
                            isFinal = true
                        )
                    )
                ).isOk shouldBe true

                pluginTemplateService.addOrganization(
                    templateName = organizationTemplateName,
                    pluginType = pluginType,
                    pluginId = pluginId,
                    organizationId = organizationId,
                    userId = "user"
                ).isOk shouldBe true

                val response = client.get("/repositories/$repositoryId/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<PreconfiguredPluginDescriptor>>()
                    .find { it.id == pluginId && it.type == pluginType }
                    .shouldNotBeNull {
                        options.find { it.name == option }.shouldNotBeNull {
                            defaultValue shouldBe organizationServerUrl
                            isFixed shouldBe true
                        }
                    }
            }
        }
    }
})
