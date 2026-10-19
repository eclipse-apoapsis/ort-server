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

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.ADVISOR
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory

class ResetGlobalDefaultPluginsIntegrationTest : PluginManagerIntegrationTest({
    "ResetGlobalDefaultPlugins" should {
        "reset the global defaults to the built-in defaults" {
            pluginManagerTestApplication { client ->
                defaultPluginsService.setDefaultPlugins(
                    null,
                    ADVISOR,
                    listOf(OssIndexFactory.descriptor.id),
                    "test-user"
                )

                client.delete("/admin/plugins/defaults/$ADVISOR") shouldHaveStatus
                        HttpStatusCode.NoContent

                val defaults = getDefaultPlugins(null, ADVISOR)
                defaults.source shouldBe DefaultPluginsSource.BUILT_IN
                defaults.pluginIds shouldBe DefaultPluginsService.getBuiltInDefaults(ADVISOR)
            }
        }

        "succeed if no global defaults are configured" {
            pluginManagerTestApplication { client ->
                client.delete("/admin/plugins/defaults/$ADVISOR") shouldHaveStatus
                        HttpStatusCode.NoContent

                getDefaultPlugins(null, ADVISOR).source shouldBe
                        DefaultPluginsSource.BUILT_IN
            }
        }

        "return BadRequest for an invalid category" {
            pluginManagerTestApplication { client ->
                client.delete("/admin/plugins/defaults/UNKNOWN") shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
