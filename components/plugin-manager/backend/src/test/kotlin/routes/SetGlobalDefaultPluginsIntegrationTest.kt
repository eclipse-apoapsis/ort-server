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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDisabled
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEvent
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.getInstalledPlugins

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory
import org.ossreviewtoolkit.plugins.advisors.osv.OsvFactory

class SetGlobalDefaultPluginsIntegrationTest : PluginManagerIntegrationTest({
    suspend fun getGlobalDefaults(category: DefaultPluginsCategory): DefaultPlugins =
        checkNotNull(defaultPluginsService.getDefaultPlugins(null, category).get())

    "SetGlobalDefaultPlugins" should {
        "set the global default plugins" {
            pluginManagerTestApplication { client ->
                val pluginIds = listOf(OssIndexFactory.descriptor.id, OsvFactory.descriptor.id)

                val response = client.put("/admin/plugins/defaults/${DefaultPluginsCategory.ADVISOR}") {
                    setBody(pluginIds)
                }

                response shouldHaveStatus HttpStatusCode.NoContent
                getGlobalDefaults(DefaultPluginsCategory.ADVISOR) shouldBe
                        DefaultPlugins(DefaultPluginsCategory.ADVISOR, pluginIds, DefaultPluginsSource.GLOBAL)
            }
        }

        "preserve the order of the plugins" {
            pluginManagerTestApplication { client ->
                val category = DefaultPluginsCategory.PACKAGE_CURATION_PROVIDER
                val pluginIds = getInstalledPlugins(category.pluginType).map { it.id }.take(2)
                pluginIds shouldHaveSize 2

                client.put("/admin/plugins/defaults/$category") {
                    setBody(pluginIds)
                } shouldHaveStatus HttpStatusCode.NoContent

                getGlobalDefaults(category).pluginIds shouldBe pluginIds

                client.put("/admin/plugins/defaults/$category") {
                    setBody(pluginIds.reversed())
                } shouldHaveStatus HttpStatusCode.NoContent

                getGlobalDefaults(category).pluginIds shouldBe pluginIds.reversed()
            }
        }

        "allow to set an empty list of plugins" {
            pluginManagerTestApplication { client ->
                client.put("/admin/plugins/defaults/${DefaultPluginsCategory.ADVISOR}") {
                    setBody(emptyList<String>())
                } shouldHaveStatus HttpStatusCode.NoContent

                getGlobalDefaults(DefaultPluginsCategory.ADVISOR) shouldBe
                        DefaultPlugins(DefaultPluginsCategory.ADVISOR, emptyList(), DefaultPluginsSource.GLOBAL)
            }
        }

        "return BadRequest if a plugin is not installed" {
            pluginManagerTestApplication { client ->
                client.put("/admin/plugins/defaults/${DefaultPluginsCategory.ADVISOR}") {
                    setBody(listOf("unknown"))
                } shouldHaveStatus HttpStatusCode.BadRequest

                getGlobalDefaults(DefaultPluginsCategory.ADVISOR).source shouldBe DefaultPluginsSource.BUILT_IN
            }
        }

        "return BadRequest if a plugin is disabled" {
            pluginManagerTestApplication { client ->
                val pluginId = OssIndexFactory.descriptor.id
                eventStore.appendEvent(PluginEvent(PluginType.ADVISOR, pluginId, 1, PluginDisabled, "test-user"))

                client.put("/admin/plugins/defaults/${DefaultPluginsCategory.ADVISOR}") {
                    setBody(listOf(pluginId))
                } shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "return BadRequest if a plugin is configured multiple times" {
            pluginManagerTestApplication { client ->
                val pluginId = OssIndexFactory.descriptor.id

                client.put("/admin/plugins/defaults/${DefaultPluginsCategory.ADVISOR}") {
                    setBody(listOf(pluginId, pluginId))
                } shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
