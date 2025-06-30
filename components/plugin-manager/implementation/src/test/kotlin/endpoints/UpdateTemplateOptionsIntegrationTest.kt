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
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.pluginManagerRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class UpdateTemplateOptionsIntegrationTest : AbstractIntegrationTest({
    lateinit var pluginEventStore: PluginEventStore
    lateinit var pluginService: PluginService
    lateinit var pluginTemplateService: PluginTemplateService

    val pluginType = PluginType.ADVISOR
    val pluginId = VulnerableCodeFactory.descriptor.id
    val serverUrlOption = VulnerableCodeFactory.descriptor.options.single { it.name == "serverUrl" }
    val readTimeoutOption = VulnerableCodeFactory.descriptor.options.single { it.name == "readTimeout" }

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

    "UpdateTemplateOptions" should {
        "create a template if it does not exist" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
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

        "create a template if it was deleted before" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
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

        "update the options of an existing template" {
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
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
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
                val options = listOf(
                    PluginOptionTemplate(
                        option = serverUrlOption.name,
                        type = serverUrlOption.type.mapToApi(),
                        value = "https://example.org",
                        isFinal = true
                    )
                )

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
            integrationTestApplication(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) { client ->
                val nonExistingOption = PluginOptionTemplate(
                    option = "invalidOption",
                    type = PluginOptionType.STRING,
                    value = "https://example.org",
                    isFinal = true
                )

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
