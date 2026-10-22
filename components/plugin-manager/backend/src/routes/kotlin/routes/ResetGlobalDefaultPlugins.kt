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

import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.delete
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.handleDefaultPluginsResult
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireEnumParameter

internal fun Route.resetGlobalDefaultPlugins(
    defaultPluginsService: DefaultPluginsService
) = delete("admin/plugins/defaults/{category}", {
    operationId = "resetGlobalDefaultPlugins"
    summary = "Reset the global default plugins of a category"
    description = "Reset the global default plugins of a category, so that the built-in defaults are used."
    tags = listOf("Plugins")

    request {
        pathParameter<DefaultPluginsCategory>("category") {
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
    }
}, requireSuperuser()) {
    val userId = requirePrincipal().userId
    val category = call.requireEnumParameter<DefaultPluginsCategory>("category")

    defaultPluginsService.resetDefaultPlugins(null, category, userId).handleDefaultPluginsResult {
        call.respond(HttpStatusCode.NoContent)
    }
}
