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
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.ADVISOR
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.SCANNER
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.normalizePluginId
import org.eclipse.apoapsis.ortserver.model.OrganizationId

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory
import org.ossreviewtoolkit.plugins.advisors.osv.OsvFactory

class GetOrganizationDefaultPluginsIntegrationTest : PluginManagerIntegrationTest({
    var organizationId = OrganizationId(-1L)

    beforeEach {
        organizationId = OrganizationId(dbExtension.fixtures.organization.id)
    }

    fun builtInDefaults(category: DefaultPluginsCategory) =
        DefaultPlugins(
            category = category,
            pluginIds = (
                DefaultPluginsService.getBuiltInDefaults(category)
                    ?: DefaultPluginsService.getBuiltInDefaults(SCANNER).orEmpty()
            ).map { normalizePluginId(category.pluginType, it) ?: it },
            source = DefaultPluginsSource.BUILT_IN
        )

    "GetOrganizationDefaultPlugins" should {
        "return the built-in defaults if no defaults are configured" {
            pluginManagerTestApplication { client ->
                val response = client.get("/organizations/${organizationId.value}/plugins/defaults")
                response shouldHaveStatus HttpStatusCode.OK

                response.body<List<DefaultPlugins>>() should containExactlyInAnyOrder(
                    DefaultPluginsCategory.entries.map { builtInDefaults(it) }
                )
            }
        }

        "return the organization defaults, falling back to the global defaults" {
            pluginManagerTestApplication { client ->
                val orgPluginIds = listOf(OsvFactory.descriptor.id)
                val globalPluginIds = listOf(OssIndexFactory.descriptor.id)
                val scannerPluginIds = checkNotNull(DefaultPluginsService.getBuiltInDefaults(SCANNER))

                defaultPluginsService.setDefaultPlugins(null, ADVISOR, globalPluginIds, "test-user")
                defaultPluginsService.setDefaultPlugins(null, SCANNER, scannerPluginIds, "test-user")
                defaultPluginsService.setDefaultPlugins(organizationId, ADVISOR, orgPluginIds, "test-user")

                val response = client.get("/organizations/${organizationId.value}/plugins/defaults")
                response shouldHaveStatus HttpStatusCode.OK

                response.body<List<DefaultPlugins>>() should containExactlyInAnyOrder(
                    DefaultPluginsCategory.entries.map {
                        when (it) {
                            ADVISOR ->
                                DefaultPlugins(it, orgPluginIds, DefaultPluginsSource.ORGANIZATION)

                            SCANNER ->
                                DefaultPlugins(it, scannerPluginIds, DefaultPluginsSource.GLOBAL)

                            else -> builtInDefaults(it)
                        }
                    }
                )
            }
        }

        "return NotFound if the organization does not exist" {
            pluginManagerTestApplication { client ->
                client.get("/organizations/999999/plugins/defaults") shouldHaveStatus HttpStatusCode.NotFound
            }
        }
    }
})
