/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

import kotlinx.serialization.json.Json

/**
 * Create a default HTTP client with the given [json] configuration and [engine]. If no engine is provided, it will
 * choose the engine automatically based on the platform.
 */
fun createDefaultHttpClient(json: Json = Json.Default, engine: HttpClientEngine? = null): HttpClient {
    val client = engine?.let { HttpClient(it) } ?: HttpClient()

    return client.config {
        defaultRequest {
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(json)
        }
    }
}

/**
 * Create a customized HTTP client with a default configuration for the ORT server client, adding response validation
 * and error handling by building upon [createDefaultHttpClient].
 */
fun createOrtHttpClient(
    json: Json = Json.Default,
    engine: HttpClientEngine? = null,
    config: HttpClientConfig<*>.() -> Unit = {}
): HttpClient =
    createDefaultHttpClient(json, engine).config {
        HttpResponseValidator {
            validateResponse { response ->
                if (!response.status.isSuccess()) {
                    when (response.status) {
                        BadRequest -> throw BadRequestException("Request is invalid.")
                        Unauthorized -> throw UnauthorizedException(
                            "Authentication required for ${response.request.url}."
                        )
                        Forbidden -> throw ForbiddenException("Access to '${response.request.url}' is forbidden.")
                        NotFound -> throw NotFoundException("Resource not found.")
                        InternalServerError -> throw InternalServerException("Internal server error.")
                        else -> throw ResponseException(
                            "Request failed with status ${response.status.value}: ${response.bodyAsText()}",
                            response.status
                        )
                    }
                }
            }
        }

        config()
    }

/**
 * An exception thrown by the ORT server client.
 */
class OrtServerClientException(message: String, cause: Throwable? = null) : Exception(message, cause)
