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

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.handleDefaultPluginsResult
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.RequestedConfigContext
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.getDefaultPluginsForRepository(
    configManager: ConfigManager,
    defaultPluginsService: DefaultPluginsService
) = get("repositories/{repositoryId}/plugins/defaults", {
    operationId = "GetDefaultPluginsForRepository"
    summary = "Get the default plugins for a repository"
    description = "Get the plugins that are enabled by default for runs of a specific repository for all " +
            "categories. The defaults of the repository's organization are used, falling back to the global " +
            "defaults and the built-in defaults. Plugins that are not available for the repository and report " +
            "definitions that do not exist in the config context are removed."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The ID of the repository to retrieve the default plugins for."
        }

        queryParameter<String>("configContext") {
            description = "The config context to use."
            required = false
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
                            pluginIds = listOf("OSV", "VulnerableCode"),
                            source = DefaultPluginsSource.ORGANIZATION
                        ),
                        DefaultPlugins(
                            category = DefaultPluginsCategory.REPORTER,
                            pluginIds = listOf("CycloneDX", "SpdxDocument", "WebApp"),
                            source = DefaultPluginsSource.BUILT_IN
                        )
                    )
                }
            }
        }

        HttpStatusCode.NotFound to {
            description = "The repository does not exist."
        }
    }
}, requirePermission(RepositoryPermission.READ)) {
    val repositoryId = RepositoryId(call.requireIdParameter("repositoryId"))
    val requestedContext = call.parameters["configContext"]?.let(::RequestedConfigContext)
        ?: RequestedConfigContext.EMPTY

    val resolvedContext = configManager.resolveContext(requestedContext)

    defaultPluginsService.resolveDefaultPluginsForRepository(repositoryId, resolvedContext)
        .handleDefaultPluginsResult { call.respond(HttpStatusCode.OK, it) }
}
