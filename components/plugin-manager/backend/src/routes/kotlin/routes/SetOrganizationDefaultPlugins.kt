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
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.put
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.handleDefaultPluginsResult
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireEnumParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.setOrganizationDefaultPlugins(
    defaultPluginsService: DefaultPluginsService
) = put("organizations/{organizationId}/plugins/defaults/{category}", {
    operationId = "SetOrganizationDefaultPlugins"
    summary = "Set the default plugins of a category for an organization"
    description = "Set the plugins of a category that are enabled by default for an organization. This overrides " +
            "the global defaults. Only plugins that are available for the organization can be used. For plugin " +
            "types where the order matters, like package curation providers and package configuration providers, " +
            "the plugins are used in the given order. For reporters, the names of report definitions from the admin " +
            "configuration of the default config context can be used as well."
    tags = listOf("Plugins")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }

        pathParameter<String>("category") {
            description = "The category of the default plugins, for example 'ADVISOR' or 'PROJECT_SCANNER'."
            required = true
        }

        jsonBody<List<String>> {
            description = "The ordered list of the IDs of the plugins to enable by default."
            required = true

            example("Example") {
                value = listOf("OSV", "VulnerableCode")
            }
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "The default plugins were successfully updated."
        }

        HttpStatusCode.BadRequest to {
            description = "The category is invalid, or one of the plugins is not installed, not available for the " +
                    "organization, or configured multiple times."
        }

        HttpStatusCode.NotFound to {
            description = "The organization does not exist."
        }
    }
}, requirePermission(OrganizationPermission.MANAGE_DEFAULT_PLUGINS)) {
    val userId = requirePrincipal().userId
    val organizationId = OrganizationId(call.requireIdParameter("organizationId"))
    val category = call.requireEnumParameter<DefaultPluginsCategory>("category")
    val pluginIds = call.receive<List<String>>()

    defaultPluginsService.setDefaultPlugins(organizationId, category, pluginIds, userId)
        .handleDefaultPluginsResult { call.respond(HttpStatusCode.NoContent) }
}
