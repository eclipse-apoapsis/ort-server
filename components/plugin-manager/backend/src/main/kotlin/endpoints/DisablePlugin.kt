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

import io.github.smiley4.ktoropenapi.post

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDisabled
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEvent
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.normalizePluginId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.disablePlugin(eventStore: PluginEventStore) = post("admin/plugins/{pluginType}/{pluginId}/disable", {
    operationId = "DisablePlugin"
    summary = "Disable an ORT plugin globally"
    description = "Disable an ORT plugin globally to make it generally unavailable."
    tags = listOf("Plugins")

    request {
        pathParameter<String>("pluginType") {
            description = "The type of the plugin to disable."
            required = true
        }

        pathParameter<String>("pluginId") {
            description = "The ID of the plugin to disable."
            required = true
        }
    }

    response {
        HttpStatusCode.Accepted to {
            description = "The plugin was disabled successfully."
        }

        HttpStatusCode.NotFound to {
            description = "The plugin was not found."
        }

        HttpStatusCode.NotModified to {
            description = "The plugin is already disabled."
        }
    }
}) {
    requireSuperuser()

    val pluginType = enumValueOf<PluginType>(call.requireParameter("pluginType"))
    val pluginId = normalizePluginId(pluginType, call.requireParameter("pluginId"))

    if (pluginId == null) {
        call.respond(HttpStatusCode.NotFound)
        return@post
    }

    val userId = checkNotNull(call.principal<OrtPrincipal>()).getUserId()

    val plugin = eventStore.getPlugin(pluginType, pluginId)

    if (!plugin.isEnabled()) {
        call.respond(HttpStatusCode.NotModified)
    } else {
        val nextVersion = plugin.version + 1
        val newEvent = PluginEvent(pluginType, pluginId, nextVersion, PluginDisabled, userId)
        eventStore.appendEvent(newEvent)
        call.respond(HttpStatusCode.Accepted)
    }
}
