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

import kotlin.time.Duration

/**
 * A data class representing standard configuration options for an HTTP client. These properties should be set for
 * each HTTP client instance to make sure that it behaves correctly in real world scenarios.
 */
data class HttpClientConfig(
    /**
     * Timeout for establishing a TCP connection to the server. A value of [Duration.INFINITE] disables the timeout.
     */
    val connectTimeout: Duration,

    /**
     * Timeout for reading data from an established socket connection (i.e. time between two consecutive TCP packets).
     * A value of [Duration.INFINITE] disables the timeout.
     */
    val socketTimeout: Duration,

    /**
     * Timeout for a complete HTTP request/response cycle, from sending the first byte to receiving the last byte.
     * A value of [Duration.INFINITE] disables the timeout.
     */
    val requestTimeout: Duration,

    /**
     * Maximum number of retry attempts when the server responds with a 5xx error. A value of `0` disables retries.
     */
    val maxRetries: Int,

    /**
     * Base delay for the exponential backoff between retries. The effective delay for retry `n` is
     * `retryDelay * 2^(n-1)`. If [retryExponentialBackoff] is `false`, this value is used as a constant delay
     * between all retries.
     */
    val retryDelay: Duration,

    /**
     * Whether to use exponential backoff between retries. If `true`, the delay between retries grows exponentially
     * based on [retryDelay]. If `false`, [retryDelay] is applied as a constant delay between all retries.
     */
    val retryExponentialBackoff: Boolean,

    /**
     * Configuration for handling HTTP 429 (Too Many Requests) responses with automatic retries.
     */
    val rateLimitConfig: RateLimitConfig
) {
    companion object {
        /** The name of the path under which HTTP client configuration options are expected. */
        const val CONFIG_PATH = "httpClient"

        /** The configuration property for the [HttpClientConfig.connectTimeout] option (value in milliseconds). */
        const val CONNECT_TIMEOUT_PROPERTY = "connectTimeoutMs"

        /** The configuration property for the [HttpClientConfig.socketTimeout] option (value in milliseconds). */
        const val SOCKET_TIMEOUT_PROPERTY = "socketTimeoutMs"

        /** The configuration property for the [HttpClientConfig.requestTimeout] option (value in milliseconds). */
        const val REQUEST_TIMEOUT_PROPERTY = "requestTimeoutMs"

        /** The configuration property for the [HttpClientConfig.maxRetries] option. */
        const val MAX_RETRIES_PROPERTY = "maxRetries"

        /**
         * The configuration property for the [HttpClientConfig.retryDelay] option (value in milliseconds).
         */
        const val RETRY_DELAY_PROPERTY = "retryDelayMs"

        /**
         * The configuration property for the [HttpClientConfig.retryExponentialBackoff] option.
         */
        const val RETRY_EXPONENTIAL_BACKOFF_PROPERTY = "retryExponentialBackoff"
    }
}
