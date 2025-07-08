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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints

import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.TemplateError
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.getTemplate(
    pluginTemplateService: PluginTemplateService
) = get("admin/plugins/{pluginType}/{pluginId}/templates/{templateName}", {
    operationId = "GetPluginTemplate"
    summary = "Get a templates for a plugin"
    description = "Retrieve the template with the given name for a specific plugin type and ID."
    tags = listOf("Plugins")

    request {
        pathParameter<String>("pluginType") {
            description = "The type of the plugin to retrieve templates for."
            required = true
        }

        pathParameter<String>("pluginId") {
            description = "The ID of the plugin to retrieve templates for."
            required = true
        }

        pathParameter<String>("templateName") {
            description = "The name of the template to retrieve."
            required = true
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "The template for the specified plugin."

            body<PluginTemplate> {
                mediaTypes = setOf(ContentType.Application.Json)

                example("Example") {
                    PluginTemplate(
                        name = "exampleTemplate",
                        pluginType = PluginType.ADVISOR,
                        pluginId = "examplePlugin",
                        options = listOf(
                            PluginOptionTemplate(
                                option = "exampleOption",
                                type = PluginOptionType.STRING,
                                value = "exampleValue",
                                isFinal = true
                            )
                        ),
                        isGlobal = false,
                        organizationIds = listOf(1, 2)
                    )
                }
            }
        }

        HttpStatusCode.NotFound to {
            description = "The specified plugin template does not exist."
        }
    }
}) {
    requireSuperuser()

    val pluginType = enumValueOf<PluginType>(call.requireParameter("pluginType"))
    val pluginId = call.requireParameter("pluginId")
    val templateName = call.requireParameter("templateName")

    pluginTemplateService.getTemplate(templateName, pluginType, pluginId).onSuccess {
        call.respond(HttpStatusCode.OK, it)
    }.onFailure {
        when (it) {
            is TemplateError.InvalidPlugin -> call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.InvalidState -> call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.NotFound -> call.respond(HttpStatusCode.NotFound, it.message)
        }
    }
}
