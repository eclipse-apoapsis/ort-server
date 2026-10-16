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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType

class GetAvailableGlobalDefaultPluginsIntegrationTest : PluginManagerIntegrationTest({
    "GetAvailableGlobalDefaultPlugins" should {
        "return all plugins that are not disabled" {
            pluginManagerTestApplication { client ->
                client.post("/admin/plugins/${PluginType.ADVISOR}/OSV/disable") shouldHaveStatus HttpStatusCode.Accepted
                client.post("/admin/plugins/${PluginType.ADVISOR}/VulnerableCode/restrict") shouldHaveStatus
                        HttpStatusCode.Accepted

                val expectedPlugins = pluginService.getPlugins()
                    .filterNot { it.type == PluginType.ADVISOR && it.id == "OSV" }
                    .map { it.type to it.id }

                val response = client.get("/admin/plugins/defaults/available")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<PluginDescriptor>>().map { it.type to it.id } shouldContainExactlyInAnyOrder
                        expectedPlugins
            }
        }
    }
})
