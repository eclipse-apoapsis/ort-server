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

import io.github.smiley4.ktoropenapi.post

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.TemplateError
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.createTemplate(
    pluginTemplateService: PluginTemplateService
) = post("admin/plugins/{pluginType}/{pluginId}/templates/{templateName}", {
    operationId = "CreatePluginTemplate"
    summary = "Create a new plugin template"
    description = "Create a new plugin template."
    tags = listOf("Plugins")

    request {
        pathParameter<String>("pluginType") {
            description = "The type of the plugin this template is for."
            required = true
        }

        pathParameter<String>("pluginId") {
            description = "The ID of the plugin this template is for."
            required = true
        }

        pathParameter<String>("templateName") {
            description = "The name of the template to create."
            required = true
        }

        body<List<PluginOptionTemplate>> {
            description = "The list of plugin option templates."
            mediaTypes = setOf(ContentType.Application.Json)
            required = true

            example("Example") {
                listOf(
                    PluginOptionTemplate("option1", PluginOptionType.STRING, "defaultValue", false),
                    PluginOptionTemplate("option2", PluginOptionType.BOOLEAN, "true", true)
                )
            }
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "The template was successfully created."
        }

        HttpStatusCode.BadRequest to {
            description = "The specified plugin is not installed or the template could not be created."
        }
    }
}) {
    requireSuperuser()

    val userId = checkNotNull(call.principal<OrtPrincipal>()).getUserId()
    val pluginType = enumValueOf<PluginType>(call.requireParameter("pluginType"))
    val pluginId = call.requireParameter("pluginId")
    val templateName = call.requireParameter("templateName")

    val options = call.receive<List<PluginOptionTemplate>>()

    pluginTemplateService.create(templateName, pluginType, pluginId, userId, options).onSuccess {
        call.respond(HttpStatusCode.OK, "Template created successfully.")
    }.onFailure {
        when (it) {
            is TemplateError.InvalidPlugin -> call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.InvalidState -> call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.NotFound -> call.respond(HttpStatusCode.NotFound, it.message)
        }
    }
}
