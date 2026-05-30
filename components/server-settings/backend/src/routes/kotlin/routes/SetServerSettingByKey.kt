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

package org.eclipse.apoapsis.ortserver.components.serversettings.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.routes.post
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.serversettings.ServerSetting
import org.eclipse.apoapsis.ortserver.components.serversettings.ServerSettingKey
import org.eclipse.apoapsis.ortserver.components.serversettings.ServerSettingsTable
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal fun Route.setServerSettingByKey(db: Database) = post("settings/server/{key}", {
    operationId = "SetServerSettingByKey"
    summary = "Set the server setting for the provided key"
    description = "Set the value and isEnabled properties for a server setting key."
    tags = listOf("Settings")

    request {
        pathParameter<ServerSettingKey>("key") {
            description = "The server setting key."
            required = true
        }

        jsonBody<ServerSetting> {
            description = "The server setting value and isEnabled properties."
            example("Server setting") {
                value = ServerSetting(
                    isEnabled = true,
                    value = "http://example.com/icon.png"
                )
            }
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "The server setting was successfully set."
        }

        HttpStatusCode.BadRequest to {
            description = "The server setting key is invalid."
        }
    }
}, requireSuperuser()) {
    val keyParameter = call.requireParameter("key")

    val key = runCatching {
        enumValueOf<ServerSettingKey>(keyParameter)
    }.getOrElse {
        call.respondError(
            HttpStatusCode.BadRequest,
            message = "Invalid key: $keyParameter",
            cause = "Allowed keys: ${ServerSettingKey.entries.joinToString()}"
        )
        return@post
    }

    val serverSetting = call.receive<ServerSetting>()

    transaction(db) {
        ServerSettingsTable.insertOrUpdate(
            key = key,
            value = serverSetting.value,
            isEnabled = serverSetting.isEnabled
        )
    }

    call.respond(HttpStatusCode.OK)
}
