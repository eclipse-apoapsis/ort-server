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

import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PreconfiguredPluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PreconfiguredPluginOption
import org.eclipse.apoapsis.ortserver.components.pluginmanager.TemplateError
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.getPluginsForRepository(
    pluginTemplateService: PluginTemplateService
) = get("repositories/{repositoryId}/plugins", {
    operationId = "GetPluginsForRepository"
    summary = "Get available plugins for a repository"
    description = "Get a list of all available plugins for a specific repository."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The ID of the repository to retrieve plugins for."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "A list of plugins available for the repository."

            body<List<PreconfiguredPluginDescriptor>> {
                listOf(
                    PreconfiguredPluginDescriptor(
                        id = "VulnerableCode",
                        type = PluginType.ADVISOR,
                        displayName = "VulnerableCode",
                        description = "An advisor that uses a VulnerableCode instance to find vulnerabilities.",
                        options = listOf(
                            PreconfiguredPluginOption(
                                name = "serverUrl",
                                description = "The base URL of the VulnerableCode REST API.",
                                type = PluginOptionType.STRING,
                                defaultValue = "https://public.vulnerablecode.io/api/",
                                isFixed = true,
                                isNullable = false,
                                isRequired = false
                            )
                        )
                    ),
                    PreconfiguredPluginDescriptor(
                        id = "NPM",
                        type = PluginType.PACKAGE_MANAGER,
                        displayName = "NPM",
                        description = "The Node package manager for Node.js.",
                        options = listOf(
                            PreconfiguredPluginOption(
                                name = "legacyPeerDeps",
                                description = "If true, the '--legacy-peer-deps' flag is passed to NPM.",
                                type = PluginOptionType.BOOLEAN,
                                defaultValue = "false",
                                isFixed = false,
                                isNullable = false,
                                isRequired = false
                            )
                        )
                    )
                )
            }
        }
    }
}, requirePermission(RepositoryPermission.READ)) {
    val repositoryId = call.requireIdParameter("repositoryId")

    pluginTemplateService.getPluginsForRepository(repositoryId).onSuccess {
        call.respond(HttpStatusCode.OK, it)
    }.onFailure {
        when (it) {
            is TemplateError.InvalidPlugin -> call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.InvalidState -> call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.NotFound -> call.respond(HttpStatusCode.NotFound, it.message)
        }
    }
}
