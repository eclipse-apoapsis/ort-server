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

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import kotlin.time.Duration.Companion.milliseconds

/**
 * Return an [HttpClientConfig] instance that has been initialized from the values of the given [config].
 * The settings can either be defined in the [HttpClientConfig.CONFIG_PATH] subpath or on top-level.
 */
fun HttpClientConfig.Companion.create(config: Config): HttpClientConfig {
    val httpClientConfig = if (config.hasPath(CONFIG_PATH)) {
        config.getConfig(CONFIG_PATH)
    } else {
        config
    }

    return HttpClientConfig(
        connectTimeout = httpClientConfig.getLong(CONNECT_TIMEOUT_PROPERTY).milliseconds,
        socketTimeout = httpClientConfig.getLong(SOCKET_TIMEOUT_PROPERTY).milliseconds,
        requestTimeout = httpClientConfig.getLong(REQUEST_TIMEOUT_PROPERTY).milliseconds,
        maxRetries = httpClientConfig.getInt(MAX_RETRIES_PROPERTY),
        retryDelay = httpClientConfig.getLong(RETRY_DELAY_PROPERTY).milliseconds,
        retryExponentialBackoff = httpClientConfig.getBoolean(RETRY_EXPONENTIAL_BACKOFF_PROPERTY),
        rateLimitConfig = RateLimitConfig.create(httpClientConfig)
    )
}

/**
 * Return an [HttpClientConfig] instance that has been initialized from the values defined in the
 * _application.conf_ file with optional overrides from the given [overridesPath]. Using this function,
 * application modules can partly override the settings of HTTP clients in their own configuration and rely on
 * default values for the remaining settings.
 */
fun HttpClientConfig.Companion.create(overridesPath: String? = null): HttpClientConfig {
    val defaultConfig = ConfigFactory.load()
    val clientConfig = overridesPath?.let { path ->
        defaultConfig.getConfig(path).withFallback(defaultConfig.getConfig(HttpClientConfig.CONFIG_PATH))
    } ?: defaultConfig

    return create(clientConfig)
}
