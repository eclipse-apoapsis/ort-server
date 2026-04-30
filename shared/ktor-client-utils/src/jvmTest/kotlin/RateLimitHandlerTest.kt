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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.inspectors.forAll
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RateLimitHandlerTest : WordSpec({
    val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    afterTest {
        unmockkAll()
        ConfigFactory.invalidateCaches()
    }

    "installRateLimitHandling()" should {
        "retry on 429 and succeed on the next attempt" {
            server.stubFor(
                get(urlPathEqualTo("/api/resource"))
                    .inScenario("rate-limit")
                    .whenScenarioStateIs("Started")
                    .willSetStateTo("ok")
                    .willReturn(
                        aResponse()
                            .withStatus(429)
                            .withHeader("Retry-After", "1")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/api/resource"))
                    .inScenario("rate-limit")
                    .whenScenarioStateIs("ok")
                    .willReturn(aResponse().withStatus(200))
            )

            val response = createClient(createConfig()).get("http://localhost:${server.port()}/api/resource") {
                expectSuccess = false
            }

            response.status shouldBe HttpStatusCode.OK
            server.verify(2, getRequestedFor(urlPathEqualTo("/api/resource")))
        }

        "give up after maxRetries and return the 429 response" {
            server.stubFor(
                get(urlPathEqualTo("/api/limited"))
                    .willReturn(
                        aResponse()
                            .withStatus(429)
                            .withHeader("Retry-After", "1")
                    )
            )

            val maxRetries = 3
            val response =
                createClient(createConfig(maxRetries = maxRetries, defaultDelay = 5.milliseconds))
                    .get("http://localhost:${server.port()}/api/limited") {
                        expectSuccess = false
                    }

            response.status shouldBe HttpStatusCode.TooManyRequests
            // 1 initial request + maxRetries
            server.verify(maxRetries + 1, getRequestedFor(urlPathEqualTo("/api/limited")))
        }

        "respect the Retry-After header" {
            val retryAfterSeconds = 1L

            server.stubFor(
                get(urlPathEqualTo("/api/slow"))
                    .inScenario("retry-after")
                    .whenScenarioStateIs("Started")
                    .willSetStateTo("ok")
                    .willReturn(
                        aResponse()
                            .withStatus(429)
                            .withHeader("Retry-After", retryAfterSeconds.toString())
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/api/slow"))
                    .inScenario("retry-after")
                    .whenScenarioStateIs("ok")
                    .willReturn(aResponse().withStatus(200))
            )

            val startTime = System.currentTimeMillis()

            createClient(createConfig()).get("http://localhost:${server.port()}/api/slow") {
                expectSuccess = false
            }

            (System.currentTimeMillis() - startTime) shouldBeGreaterThanOrEqual (retryAfterSeconds * 1000L)
        }

        "cap the delay at maxDelayMs even when Retry-After is very large" {
            val maxDelayMs = 500L

            server.stubFor(
                get(urlPathEqualTo("/api/capped"))
                    .inScenario("capped-delay")
                    .whenScenarioStateIs("Started")
                    .willSetStateTo("ok")
                    .willReturn(
                        aResponse()
                            .withStatus(429)
                            .withHeader("Retry-After", "9999")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/api/capped"))
                    .inScenario("capped-delay")
                    .whenScenarioStateIs("ok")
                    .willReturn(aResponse().withStatus(200))
            )

            val startTime = System.currentTimeMillis()

            createClient(createConfig(maxRetries = 1, maxDelay = maxDelayMs.milliseconds))
                .get("http://localhost:${server.port()}/api/capped") {
                    expectSuccess = false
                }

            val elapsed = System.currentTimeMillis() - startTime
            elapsed shouldBeGreaterThanOrEqual maxDelayMs
            (elapsed < 3_000L) shouldBe true
        }

        "not retry 200 responses" {
            server.stubFor(
                get(urlPathEqualTo("/api/ok"))
                    .willReturn(aResponse().withStatus(200))
            )

            createClient(createConfig()).get("http://localhost:${server.port()}/api/ok") {
                expectSuccess = false
            }

            server.verify(1, getRequestedFor(urlPathEqualTo("/api/ok")))
        }
    }

    "StandardRateLimitStrategies" should {
        "provide a CONSTANT strategy" {
            val defaultDelay = 11.seconds
            val config = createConfig(defaultDelay = defaultDelay)

            listOf(1, 2, 3, 17, 42, 1000).forAll { retryCount ->
                StandardRateLimitStrategies.CONSTANT.computeDelay(retryCount, config) shouldBe defaultDelay
            }
        }

        "provide a BACKOFF strategy" {
            val defaultDelay = 2.seconds
            val retryCounts = listOf(1, 2, 5, 10)
            val expectedDelays = listOf(2.seconds, 4.seconds, 32.seconds, 1024.seconds)
            val config = createConfig(defaultDelay = defaultDelay)

            retryCounts.zip(expectedDelays).forEach { (retryCount, expectedDelay) ->
                StandardRateLimitStrategies.BACKOFF.computeDelay(retryCount, config) shouldBe expectedDelay
            }
        }

        "provide a NEXT_MINUTE strategy" {
            mockkObject(Clock.System)

            val config = createConfig()
            val times = listOf(0, 15, 30, 45, 59).map { seconds ->
                Instant.parse("2026-04-27T09:21:${seconds.toString().padStart(2, '0')}Z")
            }
            val expectedDelays = listOf(60.seconds, 45.seconds, 30.seconds, 15.seconds, 1.seconds)
            every { Clock.System.now() } returnsMany times

            expectedDelays.withIndex().forEach { indexedValue ->
                StandardRateLimitStrategies.NEXT_MINUTE.computeDelay(
                    indexedValue.index,
                    config
                ) shouldBe indexedValue.value
            }
        }
    }

    "RateLimitConfig.create()" should {
        "create a config from a Config object" {
            val config = ConfigFactory.parseString(
                """
                rateLimit {
                    maxRetries = 7
                    defaultDelayMs = 2000
                    maxDelayMs = 30000
                    delayStrategy = BACKOFF
                }
                """.trimIndent()
            )

            val rateLimitConfig = RateLimitConfig.create(config)

            rateLimitConfig.maxRetries shouldBe 7
            rateLimitConfig.defaultDelay shouldBe 2000.milliseconds
            rateLimitConfig.maxDelay shouldBe 30000.milliseconds
            rateLimitConfig.delayStrategy shouldBe StandardRateLimitStrategies.BACKOFF
        }

        "support all standard delay strategies" {
            StandardRateLimitStrategies.entries.forEach { strategy ->
                val config = ConfigFactory.parseString(
                    """
                    rateLimit {
                        maxRetries = 3
                        defaultDelayMs = 1000
                        maxDelayMs = 10000
                        delayStrategy = ${strategy.name}
                    }
                    """.trimIndent()
                )

                RateLimitConfig.create(config).delayStrategy shouldBe strategy
            }
        }

        "read default values from application.conf" {
            val config = ConfigFactory.load().getConfig(HttpClientConfig.CONFIG_PATH)

            val rateLimitConfig = RateLimitConfig.create(config)

            rateLimitConfig.maxRetries shouldBe 5
            rateLimitConfig.defaultDelay shouldBe 1000.milliseconds
            rateLimitConfig.maxDelay shouldBe 60000.milliseconds
            rateLimitConfig.delayStrategy shouldBe StandardRateLimitStrategies.BACKOFF
        }

        "allow overriding values via environment variable substitution" {
            val environment = mapOf(
                "HTTP_RATE_LIMIT_MAX_RETRIES" to "10",
                "HTTP_RATE_LIMIT_DEFAULT_DELAY_MS" to "2000",
                "HTTP_RATE_LIMIT_MAX_DELAY_MS" to "120000",
                "HTTP_RATE_LIMIT_DELAY_STRATEGY" to "CONSTANT"
            )

            withEnvironment(environment) {
                ConfigFactory.invalidateCaches()
                val rateLimitConfig = RateLimitConfig.create(
                    ConfigFactory.load().getConfig(HttpClientConfig.CONFIG_PATH)
                )

                rateLimitConfig.maxRetries shouldBe 10
                rateLimitConfig.defaultDelay shouldBe 2000.milliseconds
                rateLimitConfig.maxDelay shouldBe 120000.milliseconds
                rateLimitConfig.delayStrategy shouldBe StandardRateLimitStrategies.CONSTANT
            }
        }
    }

    "resolveDelay()" should {
        "use Retry-After header value in milliseconds" {
            resolveDelay("5", 1, createConfig()) shouldBe 5.seconds
        }

        "cap Retry-After value at maxDelayMs" {
            val maxDelay = 2.seconds

            resolveDelay("9999", 1, createConfig(maxDelay = maxDelay)) shouldBe maxDelay
        }

        "delegate to the strategy if no Retry-After header is present" {
            val config = createConfig(defaultDelay = 1.seconds, delayStrategy = testDelayStrategy())

            resolveDelay(null, 1, config) shouldBe 1.seconds
            resolveDelay(null, 2, config) shouldBe 2.seconds
            resolveDelay(null, 3, config) shouldBe 3.seconds
        }

        "delegate to the strategy if the Retry-After header value cannot be parsed" {
            val config = createConfig(defaultDelay = 1.seconds, delayStrategy = testDelayStrategy())

            resolveDelay("ComeBackLater", 3, config) shouldBe 3.seconds
        }

        "cap the value from the strategy at maxDelayMs" {
            val maxDelay = 60.seconds
            val config = createConfig(
                defaultDelay = 1.seconds,
                maxDelay = maxDelay,
                delayStrategy = testDelayStrategy()
            )

            resolveDelay(null, 100, config) shouldBe maxDelay
        }
    }
})

/**
 * Create an [HttpClient] for testing that uses the given [config].
 */
private fun createClient(config: RateLimitConfig): HttpClient =
    HttpClient(CIO).withRateLimitHandling(config)

/**
 * Create a [RateLimitConfig] with default values for all parameters except the ones explicitly specified.
 */
private fun createConfig(
    maxRetries: Int = 5,
    defaultDelay: Duration = 5.seconds,
    maxDelay: Duration = 60.seconds,
    delayStrategy: RateLimitDelayStrategy = StandardRateLimitStrategies.CONSTANT
): RateLimitConfig =
    RateLimitConfig(maxRetries, defaultDelay, maxDelay, delayStrategy)

/**
 * Return a [RateLimitDelayStrategy] for testing. The strategy computes a delay which is `retryCount` * `defaultDelay`.
 */
private fun testDelayStrategy(): RateLimitDelayStrategy =
    object : RateLimitDelayStrategy {
        override fun computeDelay(retryCount: Int, config: RateLimitConfig): Duration =
            (config.defaultDelay.inWholeSeconds * retryCount).seconds
    }
