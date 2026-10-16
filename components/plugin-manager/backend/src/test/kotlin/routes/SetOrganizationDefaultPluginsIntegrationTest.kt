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

import com.github.michaelbull.result.get

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.ADVISOR
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEvent
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginRestricted
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.model.OrganizationId

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory
import org.ossreviewtoolkit.plugins.advisors.osv.OsvFactory

class SetOrganizationDefaultPluginsIntegrationTest : PluginManagerIntegrationTest({
    var organizationId = OrganizationId(-1L)

    beforeEach {
        organizationId = OrganizationId(dbExtension.fixtures.organization.id)
    }

    suspend fun getDefaults(organizationId: OrganizationId?, category: DefaultPluginsCategory): DefaultPlugins =
        checkNotNull(defaultPluginsService.getDefaultPlugins(organizationId, category).get())

    "SetOrganizationDefaultPlugins" should {
        "set the default plugins of the organization" {
            pluginManagerTestApplication { client ->
                val pluginIds = listOf(OssIndexFactory.descriptor.id, OsvFactory.descriptor.id)

                client.put("/organizations/${organizationId.value}/plugins/defaults/$ADVISOR") {
                    setBody(pluginIds)
                } shouldHaveStatus HttpStatusCode.NoContent

                getDefaults(organizationId, ADVISOR) shouldBe
                        DefaultPlugins(ADVISOR, pluginIds, DefaultPluginsSource.ORGANIZATION)
                getDefaults(null, ADVISOR).source shouldBe DefaultPluginsSource.BUILT_IN
            }
        }

        "allow restricted plugins that are assigned to the organization by a template" {
            pluginManagerTestApplication { client ->
                val pluginId = OsvFactory.descriptor.id
                eventStore.appendEvent(PluginEvent(PluginType.ADVISOR, pluginId, 1, PluginRestricted, "test-user"))
                pluginTemplateService.create("template", PluginType.ADVISOR, pluginId, "test-user", emptyList())
                pluginTemplateService.addOrganization(
                    "template",
                    PluginType.ADVISOR,
                    pluginId,
                    organizationId,
                    "test-user"
                )

                client.put("/organizations/${organizationId.value}/plugins/defaults/$ADVISOR") {
                    setBody(listOf(pluginId))
                } shouldHaveStatus HttpStatusCode.NoContent

                getDefaults(organizationId, ADVISOR).pluginIds shouldBe listOf(pluginId)
            }
        }

        "return BadRequest for a restricted plugin that is not assigned to the organization" {
            pluginManagerTestApplication { client ->
                val pluginId = OsvFactory.descriptor.id
                eventStore.appendEvent(PluginEvent(PluginType.ADVISOR, pluginId, 1, PluginRestricted, "test-user"))

                client.put("/organizations/${organizationId.value}/plugins/defaults/$ADVISOR") {
                    setBody(listOf(pluginId))
                } shouldHaveStatus HttpStatusCode.BadRequest

                getDefaults(organizationId, ADVISOR).source shouldBe DefaultPluginsSource.BUILT_IN
            }
        }

        "return BadRequest if a plugin is not installed" {
            pluginManagerTestApplication { client ->
                client.put("/organizations/${organizationId.value}/plugins/defaults/$ADVISOR") {
                    setBody(listOf("unknown"))
                } shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "return NotFound if the organization does not exist" {
            pluginManagerTestApplication { client ->
                client.put("/organizations/999999/plugins/defaults/$ADVISOR") {
                    setBody(listOf(OsvFactory.descriptor.id))
                } shouldHaveStatus HttpStatusCode.NotFound
            }
        }
    }
})
