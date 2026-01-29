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
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.utils.system.getEnv

/**
 * A collection with environment variables that are typically used to configure HTTP proxies. The function to create a
 * default HTTP client checks if any of these variables are set to create a proxy configuration automatically.
 */
private val proxyEnvironmentVariables = sequenceOf("HTTP_PROXY", "http_proxy", "HTTPS_PROXY", "https_proxy")

/**
 * Create a default HTTP client with the given [json] configuration and [engine]. If no engine is provided, it will
 * choose the engine automatically based on the platform. If [maxRetriesOnTimeout] is greater than zero, the client
 * will retry requests that failed due to a timeout or received a 504 Gateway Timeout response up to the given
 * number of times.
 */
fun createDefaultHttpClient(
    json: Json = Json.Default,
    engine: HttpClientEngine? = null,
    maxRetriesOnTimeout: Int = 3
): HttpClient {
    val client = engine?.let { HttpClient(it) } ?: HttpClient {
        // Configure proxy settings from environment variables if any are set.
        proxyEnvironmentVariables.mapNotNull { getEnv(it) }.firstOrNull()?.let { proxyUrl ->
            engine {
                proxy = ProxyBuilder.http(proxyUrl)
            }
        }
    }

    return client.config {
        defaultRequest {
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(json)
        }

        if (maxRetriesOnTimeout > 0) {
            install(HttpRequestRetry) {
                maxRetries = maxRetriesOnTimeout
                retryOnException(retryOnTimeout = true, maxRetries = maxRetriesOnTimeout)
                retryIf { request, response ->
                    response.status == HttpStatusCode.GatewayTimeout && request.method == HttpMethod.Get
                }
            }
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
