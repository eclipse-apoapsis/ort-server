/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.plugins

import com.github.ricky12awesome.jss.encodeToSchema

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.dsl.AuthType

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

import org.koin.ktor.ext.inject

@OptIn(ExperimentalSerializationApi::class)
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
            version = "0.0.1-SNAPSHOT"
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        server {
            url = "http://localhost:8080"
            description = "Local ORT server"
        }

        encoding {
            schemaEncoder { type ->
                json.encodeToSchema(serializer(type), generateDefinitions = false)
            }

            schemaDefinitionsField = "definitions"

            exampleEncoder { type, example ->
                json.encodeToString(serializer(type!!), example)
            }
        }
    }
}
