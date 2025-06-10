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

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.pluginManagerRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class PluginManagerAuthorizationTest : AbstractAuthorizationTest({
    lateinit var pluginEventStore: PluginEventStore
    lateinit var pluginService: PluginService

    beforeEach {
        pluginEventStore = PluginEventStore(dbExtension.db)
        pluginService = PluginService(dbExtension.db)
    }

    "DisablePlugin" should {
        "require the superuser role" {
            val pluginType = PluginType.ADVISOR
            val pluginId = VulnerableCodeFactory.descriptor.id

            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService) },
                successStatus = HttpStatusCode.Accepted
            ) {
                post("/admin/plugins/$pluginType/$pluginId/disable")
            }
        }
    }

    "EnablePlugin" should {
        "require the superuser role" {
            val pluginType = PluginType.ADVISOR
            val pluginId = VulnerableCodeFactory.descriptor.id

            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService) },
                successStatus = HttpStatusCode.NotModified
            ) {
                post("/admin/plugins/$pluginType/$pluginId/enable")
            }
        }
    }

    "GetInstalledPlugins" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService) }
            ) {
                get("/admin/plugins")
            }
        }
    }
})
