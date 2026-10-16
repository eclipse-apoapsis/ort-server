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
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.ADVISOR
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.REPORTER
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEvent
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginRestricted
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

import org.ossreviewtoolkit.plugins.advisors.osv.OsvFactory
import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

/** A config context that contains report definitions, see `src/test/resources/config/ort-server.conf`. */
private const val REPORT_DEFINITIONS_CONTEXT = "src/test/resources/config"

class GetDefaultPluginsForRepositoryIntegrationTest : PluginManagerIntegrationTest({
    var organizationId = OrganizationId(-1L)
    var repositoryId = RepositoryId(-1L)

    beforeEach {
        organizationId = OrganizationId(dbExtension.fixtures.organization.id)
        repositoryId = RepositoryId(dbExtension.fixtures.repository.id)
    }

    fun List<DefaultPlugins>.forType(category: DefaultPluginsCategory) = single { it.category == category }

    "GetDefaultPluginsForRepository" should {
        "return the defaults of all plugin types" {
            pluginManagerTestApplication { client ->
                val response = client.get("/repositories/${repositoryId.value}/plugins/defaults")
                response shouldHaveStatus HttpStatusCode.OK

                val defaults = response.body<List<DefaultPlugins>>()
                defaults.map { it.category }.toSet() shouldBe DefaultPluginsCategory.entries.toSet()
                defaults.forType(ADVISOR) shouldBe DefaultPlugins(
                    ADVISOR,
                    listOf(OsvFactory.descriptor.id, VulnerableCodeFactory.descriptor.id),
                    DefaultPluginsSource.BUILT_IN
                )
            }
        }

        "return the defaults of the organization of the repository" {
            pluginManagerTestApplication { client ->
                val pluginIds = listOf(VulnerableCodeFactory.descriptor.id)
                defaultPluginsService.setDefaultPlugins(organizationId, ADVISOR, pluginIds, "test-user")

                val response = client.get("/repositories/${repositoryId.value}/plugins/defaults")
                response shouldHaveStatus HttpStatusCode.OK

                response.body<List<DefaultPlugins>>().forType(ADVISOR) shouldBe
                        DefaultPlugins(ADVISOR, pluginIds, DefaultPluginsSource.ORGANIZATION)
            }
        }

        "not return plugins that are not available for the repository" {
            pluginManagerTestApplication { client ->
                eventStore.appendEvent(
                    PluginEvent(PluginType.ADVISOR, OsvFactory.descriptor.id, 1, PluginRestricted, "test-user")
                )

                val response = client.get("/repositories/${repositoryId.value}/plugins/defaults")
                response shouldHaveStatus HttpStatusCode.OK

                response.body<List<DefaultPlugins>>().forType(ADVISOR).pluginIds shouldBe
                        listOf(VulnerableCodeFactory.descriptor.id)
            }
        }

        "use the report definitions from the requested config context" {
            pluginManagerTestApplication { client ->
                defaultPluginsService.setDefaultPlugins(
                    null,
                    REPORTER,
                    listOf("PdfTemplate", "CycloneDX"),
                    "test-user"
                )

                val defaultResponse = client.get("/repositories/${repositoryId.value}/plugins/defaults")
                defaultResponse shouldHaveStatus HttpStatusCode.OK
                defaultResponse.body<List<DefaultPlugins>>().forType(REPORTER).pluginIds shouldBe
                        listOf("PdfTemplate", "CycloneDX")

                val contextResponse = client.get("/repositories/${repositoryId.value}/plugins/defaults") {
                    parameter("configContext", REPORT_DEFINITIONS_CONTEXT)
                }
                contextResponse shouldHaveStatus HttpStatusCode.OK
                contextResponse.body<List<DefaultPlugins>>().forType(REPORTER).pluginIds shouldBe
                        listOf("CycloneDX")
            }
        }

        "return NotFound if the repository does not exist" {
            pluginManagerTestApplication { client ->
                client.get("/repositories/999999/plugins/defaults") shouldHaveStatus HttpStatusCode.NotFound
            }
        }
    }
})
