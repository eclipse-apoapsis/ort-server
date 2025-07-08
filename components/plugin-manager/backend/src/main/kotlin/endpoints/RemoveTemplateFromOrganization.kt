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

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.TemplateError
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.removeTemplateFromOrganization(
    pluginTemplateService: PluginTemplateService
) = post("/admin/plugins/{pluginType}/{pluginId}/templates/{templateName}/removeFromOrganization", {
    operationId = "RemoveTemplateFromOrganization"
    summary = "Remove a plugin template from an organization"
    description = "Remove a plugin template from an organization. If any global templates are enabled for the " +
            "plugin, they will now apply to the organization."
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
            description = "The name of the template to remove."
            required = true
        }

        queryParameter<String>("organizationId") {
            description = "The ID of the organization from which the template should be removed."
            required = true
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "The template was successfully removed from the organization."
        }

        HttpStatusCode.NotFound to {
            description = "The specified plugin, plugin template, or organization does not exist."
        }

        HttpStatusCode.BadRequest to {
            description = "The template is not assigned to the organization."
        }
    }
}) {
    requireSuperuser()

    val userId = checkNotNull(call.principal<OrtPrincipal>()).getUserId()
    val pluginType = enumValueOf<PluginType>(call.requireParameter("pluginType"))
    val pluginId = call.requireParameter("pluginId")
    val templateName = call.requireParameter("templateName")
    val organizationId = call.requireIdParameter("organizationId")

    pluginTemplateService.removeOrganization(templateName, pluginType, pluginId, organizationId, userId).onSuccess {
        call.respond(HttpStatusCode.OK, "Template removed from organization successfully.")
    }.onFailure {
        when (it) {
            is TemplateError.InvalidPlugin -> call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.InvalidState -> call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.NotFound -> call.respond(HttpStatusCode.NotFound, it.message)
        }
    }
}
