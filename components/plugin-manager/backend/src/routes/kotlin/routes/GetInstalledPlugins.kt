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

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOption
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody

internal fun Route.getInstalledPlugins(pluginService: PluginService) = get("admin/plugins", {
    operationId = "GetInstalledPlugins"
    summary = "Get installed ORT plugins"
    description = "Get a list with detailed information about all installed ORT plugins."
    tags = listOf("Plugins")

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<List<PluginDescriptor>> {
                example("List of ORT plugins") {
                    value = listOf(
                        PluginDescriptor(
                            id = "VulnerableCode",
                            type = PluginType.ADVISOR,
                            displayName = "VulnerableCode",
                            description = "An advisor that uses a VulnerableCode instance to find vulnerabilities.",
                            options = listOf(
                                PluginOption(
                                    name = "serverUrl",
                                    description = "The base URL of the VulnerableCode REST API.",
                                    type = PluginOptionType.STRING,
                                    defaultValue = "https://public.vulnerablecode.io/api/",
                                    isNullable = false,
                                    isRequired = false
                                )
                            ),
                            enabled = true
                        ),
                        PluginDescriptor(
                            id = "NPM",
                            type = PluginType.PACKAGE_MANAGER,
                            displayName = "NPM",
                            description = "The Node package manager for Node.js.",
                            options = listOf(
                                PluginOption(
                                    name = "legacyPeerDeps",
                                    description = "If true, the '--legacy-peer-deps' flag is passed to NPM.",
                                    type = PluginOptionType.BOOLEAN,
                                    defaultValue = "false",
                                    isNullable = false,
                                    isRequired = false
                                )
                            ),
                            enabled = false
                        )
                    )
                }
            }
        }
    }
}, requireSuperuser()) {
    call.respond(HttpStatusCode.OK, pluginService.getPlugins())
}
