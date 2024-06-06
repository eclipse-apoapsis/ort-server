/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.plugins

import com.github.ricky12awesome.jss.buildJsonSchema

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthType

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

import org.eclipse.apoapsis.ortserver.model.ORT_SERVER_VERSION

import org.koin.ktor.ext.inject

fun Application.configureOpenApi() {
    val config: ApplicationConfig by inject()
    val json: Json by inject()

    install(SwaggerUI) {
        // Don't show the routes providing the custom json-schemas.
        pathFilter = { _, url -> url.firstOrNull() != "schemas" }

        defaultSecuritySchemeName = SecurityConfigurations.token
        defaultUnauthorizedResponse {
            description = "Invalid Token"
        }

        securityScheme(SecurityConfigurations.token) {
            type = AuthType.OAUTH2
            flows {
                authorizationCode {
                    authorizationUrl = "${config.property("jwt.issuer").getString()}/protocol/openid-connect/auth"
                    tokenUrl = "${config.property("jwt.issuer").getString()}/protocol/openid-connect/token"
                }
            }
        }

        swagger {
            swaggerUrl = "swagger-ui"
            forwardRoot = false
        }

        info {
            title = "ORT Server API"
            version = ORT_SERVER_VERSION
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        server {
            val scheme = config.property("ktor.deployment.publicScheme").getString()
            val fqdn = config.property("ktor.deployment.publicFqdn").getString()
            val port = config.property("ktor.deployment.publicPort").getString()
            url = "$scheme://$fqdn:$port"
            description = "Local ORT server"
        }

        // OpenAPI provides tags not only on operation level, but also on root level.
        // This allows to provide additional information to the tags, and actually the order
        // of the tags on root level also defines the order of appearance of the operations
        // (belonging to these tags) in the Swagger UI.
        // See https://swagger.io/docs/specification/grouping-operations-with-tags/ for details.
        tag("Health") {
        }

        tag("Organizations") {
        }

        tag("Products") {
        }

        tag("Repositories") {
        }

        tag("Secrets") {
        }

        tag("Infrastructure services") {
        }

        tag("Reports") {
        }

        tag("Logs") {
        }

        encoding {
            schemaEncoder { type ->
                val schema = buildJsonSchema(serializer(type), generateDefinitions = false).replaceIfWithType()
                json.encodeToString(schema)
            }

            schemaDefinitionsField = "definitions"

            exampleEncoder { type, example ->
                type?.let { json.encodeToString(serializer(it), example) } ?: example.toString()
            }
        }
    }
}

/**
 * The json-schema-serialization library does not properly support nullable types. For example, for a nullable string
 * property, it creates the following schema:
 *
 * ```json
 * "property": {
 *   "if": {
 *     "type": "string"
 *   },
 *   "else": {
 *     "type": "null"
 *   }
 * }
 * ```
 *
 * This function replaces this with the correct schema:
 *
 * ```json
 * "property": {
 *   "type": ["string", "null"]
 * }
 * ```
 */
private fun JsonElement.replaceIfWithType(): JsonElement {
    if (this !is JsonObject) {
        return this
    }

    val ifElseTypes = getIfElseTypes()
    val properties = toMutableMap()

    if (ifElseTypes != null) {
        properties["type"] = JsonArray(ifElseTypes.toList().map { JsonPrimitive(it) })
        properties -= "if"
        properties -= "else"
    }

    val result = properties.mapValues { (_, value) ->
        value.replaceIfWithType()
    }

    return JsonObject(result)
}

private fun JsonObject.getIfElseTypes(): Pair<String, String>? {
    val ifType = (get("if") as? JsonObject)?.get("type")?.jsonPrimitive?.content
    val elseType = (get("else") as? JsonObject)?.get("type")?.jsonPrimitive?.content

    if (ifType == null || elseType == null) {
        return null
    }

    return ifType to elseType
}
