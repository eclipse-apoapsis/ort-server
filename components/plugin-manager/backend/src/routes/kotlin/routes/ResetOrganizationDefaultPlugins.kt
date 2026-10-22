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
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.delete
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.handleDefaultPluginsResult
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireEnumParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.resetOrganizationDefaultPlugins(
    defaultPluginsService: DefaultPluginsService
) = delete("organizations/{organizationId}/plugins/defaults/{category}", {
    operationId = "resetOrganizationDefaultPlugins"
    summary = "Reset the default plugins of a category for an organization"
    description = "Reset the default plugins of a category for an organization, so that the global defaults are " +
            "used."
    tags = listOf("Plugins")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }

        pathParameter<String>("category") {
            description = "The category of the default plugins, for example 'ADVISOR' or 'PROJECT_SCANNER'."
            required = true
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "The default plugins were successfully reset."
        }

        HttpStatusCode.BadRequest to {
            description = "The category is invalid."
        }

        HttpStatusCode.NotFound to {
            description = "The organization does not exist."
        }
    }
}, requirePermission(OrganizationPermission.MANAGE_DEFAULT_PLUGINS)) {
    val userId = requirePrincipal().userId
    val organizationId = OrganizationId(call.requireIdParameter("organizationId"))
    val category = call.requireEnumParameter<DefaultPluginsCategory>("category")

    defaultPluginsService.resetDefaultPlugins(organizationId, category, userId).handleDefaultPluginsResult {
        call.respond(HttpStatusCode.NoContent)
    }
}
