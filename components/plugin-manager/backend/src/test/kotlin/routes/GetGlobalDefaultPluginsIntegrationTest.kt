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

class GetGlobalDefaultPluginsIntegrationTest : PluginManagerIntegrationTest({
    fun builtInDefaults(category: DefaultPluginsCategory) =
        DefaultPlugins(
            category = category,
            pluginIds = (
                DefaultPluginsService.getBuiltInDefaults(category)
                    ?: DefaultPluginsService.getBuiltInDefaults(SCANNER).orEmpty()
            ).map { normalizePluginId(category.pluginType, it) ?: it },
            source = DefaultPluginsSource.BUILT_IN
        )

    "GetGlobalDefaultPlugins" should {
        "return the built-in defaults if no defaults are configured" {
            pluginManagerTestApplication { client ->
                val response = client.get("/admin/plugins/defaults")
                response shouldHaveStatus HttpStatusCode.OK

                response.body<List<DefaultPlugins>>() should containExactlyInAnyOrder(
                    DefaultPluginsCategory.entries.map { builtInDefaults(it) }
                )
            }
        }

        "return the configured global defaults" {
            pluginManagerTestApplication { client ->
                val pluginId = OssIndexFactory.descriptor.id
                defaultPluginsService.setDefaultPlugins(null, ADVISOR, listOf(pluginId), "test-user")

                val response = client.get("/admin/plugins/defaults")
                response shouldHaveStatus HttpStatusCode.OK

                response.body<List<DefaultPlugins>>() should containExactlyInAnyOrder(
                    DefaultPluginsCategory.entries.map {
                        if (it == ADVISOR) {
                            DefaultPlugins(ADVISOR, listOf(pluginId), DefaultPluginsSource.GLOBAL)
                        } else {
                            builtInDefaults(it)
                        }
                    }
                )
            }
        }

        "not return the defaults of organizations" {
            pluginManagerTestApplication { client ->
                val organizationId = OrganizationId(dbExtension.fixtures.organization.id)
                defaultPluginsService.setDefaultPlugins(
                    organizationId,
                    ADVISOR,
                    listOf(OssIndexFactory.descriptor.id),
                    "test-user"
                )

                val response = client.get("/admin/plugins/defaults")
                response shouldHaveStatus HttpStatusCode.OK

                response.body<List<DefaultPlugins>>() should containExactlyInAnyOrder(
                    DefaultPluginsCategory.entries.map { builtInDefaults(it) }
                )
            }
        }
    }
})
