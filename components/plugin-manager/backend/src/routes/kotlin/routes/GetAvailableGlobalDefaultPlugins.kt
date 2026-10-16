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

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginAvailability
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody

internal fun Route.getAvailableGlobalDefaultPlugins(
    defaultPluginsService: DefaultPluginsService
) = get("admin/plugins/defaults/available", {
    operationId = "GetAvailableGlobalDefaultPlugins"
    summary = "Get the plugins available as global default plugins"
    description = "Get the plugins that can be configured as global default plugins. These are all plugins that are " +
            "not disabled, and the report definitions from the admin configuration of the default config context."
    tags = listOf("Plugins")

    response {
        HttpStatusCode.OK to {
            description = "The plugins that can be configured as global default plugins."

            jsonBody<List<PluginDescriptor>> {
                example("Example") {
                    value = listOf(
                        PluginDescriptor(
                            id = "VulnerableCode",
                            type = PluginType.ADVISOR,
                            displayName = "VulnerableCode",
                            summary = "An advisor that uses a VulnerableCode instance to find vulnerabilities.",
                            description = null,
                            availability = PluginAvailability.ENABLED
                        ),
                        PluginDescriptor(
                            id = "customReport",
                            type = PluginType.REPORTER,
                            displayName = "customReport",
                            summary = "A report definition from the admin configuration.",
                            description = null,
                            availability = PluginAvailability.ENABLED
                        )
                    )
                }
            }
        }
    }
}, requireSuperuser()) {
    call.respond(HttpStatusCode.OK, defaultPluginsService.getAvailableGlobalDefaultPlugins())
}
