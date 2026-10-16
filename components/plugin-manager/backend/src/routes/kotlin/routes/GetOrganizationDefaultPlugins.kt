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

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.handleDefaultPluginsResult
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.getOrganizationDefaultPlugins(
    defaultPluginsService: DefaultPluginsService
) = get("organizations/{organizationId}/plugins/defaults", {
    operationId = "GetOrganizationDefaultPlugins"
    summary = "Get the default plugins of an organization"
    description = "Get the plugins that are enabled by default for all categories for an organization. If the " +
            "organization does not configure its own defaults for a category, the global defaults are returned, " +
            "or the built-in defaults if no global defaults are configured. The source of the defaults is part of " +
            "the response. The returned plugins are not filtered by their availability."
    tags = listOf("Plugins")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "The default plugins for all categories."

            jsonBody<List<DefaultPlugins>> {
                example("Example") {
                    value = listOf(
                        DefaultPlugins(
                            category = DefaultPluginsCategory.ADVISOR,
                            pluginIds = listOf("OSV"),
                            source = DefaultPluginsSource.ORGANIZATION
                        ),
                        DefaultPlugins(
                            category = DefaultPluginsCategory.SCANNER,
                            pluginIds = listOf("ScanCode"),
                            source = DefaultPluginsSource.GLOBAL
                        )
                    )
                }
            }
        }

        HttpStatusCode.NotFound to {
            description = "The organization does not exist."
        }
    }
}, requirePermission(OrganizationPermission.READ)) {
    val organizationId = OrganizationId(call.requireIdParameter("organizationId"))

    defaultPluginsService.getAllDefaultPlugins(organizationId).handleDefaultPluginsResult {
        call.respond(HttpStatusCode.OK, it)
    }
}
