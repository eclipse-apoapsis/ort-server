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
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.handleDefaultPluginsResult
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody

internal fun Route.getGlobalDefaultPlugins(
    defaultPluginsService: DefaultPluginsService
) = get("admin/plugins/defaults", {
    operationId = "GetGlobalDefaultPlugins"
    summary = "Get the global default plugins"
    description = "Get the plugins that are enabled by default for all categories, unless an organization " +
            "configures its own defaults. If no global defaults are configured for a category, the built-in " +
            "defaults are returned. The built-in defaults for project scanners are the default package scanners."
    tags = listOf("Plugins")

    response {
        HttpStatusCode.OK to {
            description = "The default plugins for all categories."

            jsonBody<List<DefaultPlugins>> {
                example("Example") {
                    value = listOf(
                        DefaultPlugins(
                            category = DefaultPluginsCategory.ADVISOR,
                            pluginIds = listOf("OSV", "VulnerableCode"),
                            source = DefaultPluginsSource.GLOBAL
                        ),
                        DefaultPlugins(
                            category = DefaultPluginsCategory.SCANNER,
                            pluginIds = listOf("ScanCode"),
                            source = DefaultPluginsSource.BUILT_IN
                        )
                    )
                }
            }
        }
    }
}, requireSuperuser()) {
    defaultPluginsService.getAllDefaultPlugins(null).handleDefaultPluginsResult {
        call.respond(HttpStatusCode.OK, it)
    }
}
