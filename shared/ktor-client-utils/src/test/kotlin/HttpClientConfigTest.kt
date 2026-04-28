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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HttpClientConfigTest : WordSpec({
    "HttpClientConfig.create()" should {
        "create a config from a Config object" {
            val config = ConfigFactory.parseString(
                """
                httpClient {
                    connectTimeoutMs = 5000
                    socketTimeoutMs = 15000
                    requestTimeoutMs = 45000
                    maxRetries = 5
                    retryDelayMs = 2000
                    retryExponentialBackoff = false

                    rateLimit {
                        maxRetries = 2
                        defaultDelayMs = 500
                        maxDelayMs = 10000
                        delayStrategy = BACKOFF
                    }
                }
                """.trimIndent()
            )

            val httpClientConfig = HttpClientConfig.create(config)

            httpClientConfig.connectTimeout shouldBe 5000.milliseconds
            httpClientConfig.socketTimeout shouldBe 15000.milliseconds
            httpClientConfig.requestTimeout shouldBe 45000.milliseconds
            httpClientConfig.maxRetries shouldBe 5
            httpClientConfig.retryDelay shouldBe 2000.milliseconds
            httpClientConfig.retryExponentialBackoff shouldBe false
            with(httpClientConfig.rateLimitConfig) {
                maxRetries shouldBe 2
                defaultDelay shouldBe 500.milliseconds
                maxDelay shouldBe 10000.milliseconds
                delayStrategy shouldBe StandardRateLimitStrategies.BACKOFF
            }
        }

        "create a config from a Config object if settings are defined on top-level" {
            val config = ConfigFactory.parseString(
                """
                    connectTimeoutMs = 5000
                    socketTimeoutMs = 15000
                    requestTimeoutMs = 45000
                    maxRetries = 5
                    retryDelayMs = 2000
                    retryExponentialBackoff = false

                    rateLimit {
                        maxRetries = 2
                        defaultDelayMs = 500
                        maxDelayMs = 10000
                        delayStrategy = BACKOFF
                    }
                """.trimIndent()
            )

            val httpClientConfig = HttpClientConfig.create(config)

            httpClientConfig.connectTimeout shouldBe 5000.milliseconds
            httpClientConfig.socketTimeout shouldBe 15000.milliseconds
            httpClientConfig.requestTimeout shouldBe 45000.milliseconds
            httpClientConfig.maxRetries shouldBe 5
            httpClientConfig.retryDelay shouldBe 2000.milliseconds
            httpClientConfig.retryExponentialBackoff shouldBe false
            with(httpClientConfig.rateLimitConfig) {
                maxRetries shouldBe 2
                defaultDelay shouldBe 500.milliseconds
                maxDelay shouldBe 10000.milliseconds
                delayStrategy shouldBe StandardRateLimitStrategies.BACKOFF
            }
        }

        "read default values from application.conf" {
            val httpClientConfig = HttpClientConfig.create()

            httpClientConfig.connectTimeout shouldBe 10.seconds
            httpClientConfig.socketTimeout shouldBe 30.seconds
            httpClientConfig.requestTimeout shouldBe 60.seconds
            httpClientConfig.maxRetries shouldBe 3
            httpClientConfig.retryDelay shouldBe 1.seconds
            httpClientConfig.retryExponentialBackoff shouldBe true
            with(httpClientConfig.rateLimitConfig) {
                maxRetries shouldBe 5
                defaultDelay shouldBe 1000.milliseconds
                maxDelay shouldBe 60000.milliseconds
                delayStrategy shouldBe StandardRateLimitStrategies.BACKOFF
            }
        }

        "allow overriding values from another configuration path" {
            val httpClientConfig = HttpClientConfig.create("specialHttpClient")

            httpClientConfig.connectTimeout shouldBe 10.seconds
            httpClientConfig.maxRetries shouldBe 7
            with(httpClientConfig.rateLimitConfig) {
                maxRetries shouldBe 5
                defaultDelay shouldBe 2.seconds
                maxDelay shouldBe 60000.milliseconds
                delayStrategy shouldBe StandardRateLimitStrategies.NEXT_MINUTE
            }
        }

        "allow overriding values via environment variable substitution" {
            val environment = mapOf(
                "HTTP_CLIENT_CONNECT_TIMEOUT_MS" to "2000",
                "HTTP_CLIENT_SOCKET_TIMEOUT_MS" to "5000",
                "HTTP_CLIENT_REQUEST_TIMEOUT_MS" to "10000",
                "HTTP_CLIENT_MAX_RETRIES" to "7",
                "HTTP_CLIENT_RETRY_DELAY_MS" to "500",
                "HTTP_CLIENT_RETRY_EXPONENTIAL_BACKOFF" to "false",
                "HTTP_RATE_LIMIT_MAX_RETRIES" to "10",
                "HTTP_RATE_LIMIT_DEFAULT_DELAY_MS" to "2000",
                "HTTP_RATE_LIMIT_MAX_DELAY_MS" to "120000",
                "HTTP_RATE_LIMIT_DELAY_STRATEGY" to "CONSTANT"
            )

            withEnvironment(environment) {
                ConfigFactory.invalidateCaches()
                val httpClientConfig = HttpClientConfig.create(ConfigFactory.load())

                httpClientConfig.connectTimeout shouldBe 2.seconds
                httpClientConfig.socketTimeout shouldBe 5.seconds
                httpClientConfig.requestTimeout shouldBe 10.seconds
                httpClientConfig.maxRetries shouldBe 7
                httpClientConfig.retryDelay shouldBe 500.milliseconds
                httpClientConfig.retryExponentialBackoff shouldBe false
                with(httpClientConfig.rateLimitConfig) {
                    maxRetries shouldBe 10
                    defaultDelay shouldBe 2.seconds
                    maxDelay shouldBe 120.seconds
                    delayStrategy shouldBe StandardRateLimitStrategies.CONSTANT
                }
            }
        }
    }
})
