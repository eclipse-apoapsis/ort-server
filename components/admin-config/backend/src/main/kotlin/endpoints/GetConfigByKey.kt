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

package org.eclipse.apoapsis.ortserver.components.adminconfig.endpoints

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.adminconfig.Config
import org.eclipse.apoapsis.ortserver.components.adminconfig.ConfigKey
import org.eclipse.apoapsis.ortserver.components.adminconfig.ConfigTable
import org.eclipse.apoapsis.ortserver.components.authorization.requireAuthenticated
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

internal fun Route.getConfigByKey(db: Database) = get("admin/config/{key}", {
    operationId = "GetConfigByKey"
    summary = "Get the value of a config key"
    description = "Get the value and isEnabled properties of a config key. " +
        "If the key does not exist in the database, a default value is returned."
    tags = listOf("Admin")

    request {
        pathParameter<ConfigKey>("key") {
            description = "The config key."
            required = true
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            body<Config> {
                example("Config values") {
                    value = """
                    {
                        "isEnabled": false,
                        "value": "http://example.com/icon.png"
                    }
                """.trimIndent()
                }
            }
        }

        HttpStatusCode.BadRequest to {
            description = "Invalid config key."
        }
    }
}) {
    requireAuthenticated()

    val keyParameter = call.requireParameter("key")
    val key = runCatching {
        enumValueOf<ConfigKey>(keyParameter)
    }.getOrElse {
        call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse(
                message = "Invalid config key: $keyParameter",
                cause = "Allowed keys: ${ConfigKey.entries.joinToString(", ")}"
            )
        )
        return@get
    }
    val configValue = transaction(db) { ConfigTable.get(key) }
    call.respond(HttpStatusCode.OK, configValue)
}
