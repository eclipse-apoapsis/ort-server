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
import kotlinx.datetime.Clock
import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId

import org.eclipse.apoapsis.ortserver.components.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.Plugin
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEnabled
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEvent
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

fun Route.enablePlugin(eventStore: PluginEventStore) = post("admin/plugins/{pluginType}/{pluginId}/enable", {
    operationId = "EnablePlugin"
    summary = "Enable an ORT plugin globally"
    description = "Enable an ORT plugin globally to make it generally available to all organizations."
    tags = listOf("Plugins")

    request {
        pathParameter<String>("pluginType") {
            description = "The type of the plugin to enable."
            required = true
        }

        pathParameter<String>("pluginId") {
            description = "The ID of the plugin to enable."
            required = true
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "The plugin was enabled successfully."
        }

        HttpStatusCode.NotModified to {
            description = "The plugin is already enabled."
        }
    }
}) {
    requireSuperuser()

    val pluginType = enumValueOf<PluginType>(call.requireParameter("pluginType"))
    val pluginId = call.requireParameter("pluginId")

    val userId = checkNotNull(call.principal<OrtPrincipal>()).getUserId()

    val events = eventStore.loadEvents(pluginType, pluginId)
    val plugin = Plugin().applyAll(events)

    if (plugin.isEnabled()) {
        call.respond(HttpStatusCode.NotModified)
    } else {
        val nextVersion = events.maxOfOrNull { it.version }?.plus(1) ?: 1L
        val newEvent = PluginEvent(pluginType, pluginId, nextVersion, PluginEnabled, Clock.System.now(), userId)
        eventStore.appendEvent(newEvent)
        call.respond(HttpStatusCode.Accepted)
    }
}
