/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.shared.ktorclientutils

import io.github.oshai.kotlinlogging.KotlinLogging

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig as KtorHttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout

private val logger = KotlinLogging.logger {}

/**
 * Create an [HttpClient] configured according to the given [config]. The [additionalConfig] block can be used to
 * install further plugins or override individual settings.
 */
fun createHttpClient(
    config: HttpClientConfig,
    additionalConfig: KtorHttpClientConfig<*>.() -> Unit = {}
): HttpClient {
    logger.debug { "Creating HttpClient with config: $config" }

    return HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeout.inWholeMilliseconds
            socketTimeoutMillis = config.socketTimeout.inWholeMilliseconds
            requestTimeoutMillis = config.requestTimeout.inWholeMilliseconds
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = config.maxRetries)

            if (config.retryExponentialBackoff) {
                exponentialDelay(baseDelayMs = config.retryDelay.inWholeMilliseconds)
            } else {
                constantDelay(millis = config.retryDelay.inWholeMilliseconds)
            }
        }

        additionalConfig()
    }.withRateLimitHandling(config.rateLimitConfig)
}

/**
 * Create an [HttpClient] using a [HttpClientConfig] loaded from the application configuration, optionally applying
 * the given [overridesPath]. Using this function, there is no need to construct an [HttpClientConfig] object
 * manually; all configuration settings are read from the application configuration. The [additionalConfig] block can
 * be used to install further plugins or override individual settings.
 */
fun createHttpClient(overridesPath: String? = null, additionalConfig: KtorHttpClientConfig<*>.() -> Unit = {}) =
    createHttpClient(HttpClientConfig.create(overridesPath), additionalConfig)
