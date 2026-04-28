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
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.stubbing.Scenario

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CreateHttpClientTest : WordSpec({
    val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

    beforeSpec { server.start() }
    afterSpec { server.stop() }
    beforeTest { server.resetAll() }

    fun testConfig(
        maxRetries: Int = 0,
        retryDelay: kotlin.time.Duration = 10.milliseconds,
        retryExponentialBackoff: Boolean = false,
        rateLimitMaxRetries: Int = 0,
        rateLimitDelay: kotlin.time.Duration = 10.milliseconds,
        connectTimeout: kotlin.time.Duration = 5.seconds,
        socketTimeout: kotlin.time.Duration = 5.seconds,
        requestTimeout: kotlin.time.Duration = 5.seconds
    ) = HttpClientConfig(
        connectTimeout = connectTimeout,
        socketTimeout = socketTimeout,
        requestTimeout = requestTimeout,
        maxRetries = maxRetries,
        retryDelay = retryDelay,
        retryExponentialBackoff = retryExponentialBackoff,
        rateLimitConfig = RateLimitConfig(
            maxRetries = rateLimitMaxRetries,
            defaultDelay = rateLimitDelay,
            maxDelay = 1.seconds,
            delayStrategy = StandardRateLimitStrategies.CONSTANT
        )
    )

    "createHttpClient" should {
        "return 200 for a successful request" {
            server.stubFor(get(urlPathEqualTo("/ok")).willReturn(aResponse().withStatus(200)))

            val response = createHttpClient(testConfig()).get("http://localhost:${server.port()}/ok") {
                expectSuccess = false
            }

            response.status shouldBe HttpStatusCode.OK
        }

        "retry on 5xx server errors" {
            server.stubFor(
                get(urlPathEqualTo("/flaky"))
                    .inScenario("flaky")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willSetStateTo("ok")
                    .willReturn(aResponse().withStatus(503))
            )
            server.stubFor(
                get(urlPathEqualTo("/flaky"))
                    .inScenario("flaky")
                    .whenScenarioStateIs("ok")
                    .willReturn(aResponse().withStatus(200))
            )

            val response = createHttpClient(testConfig(maxRetries = 2, retryDelay = 10.milliseconds))
                .get("http://localhost:${server.port()}/flaky") {
                    expectSuccess = false
                }

            response.status shouldBe HttpStatusCode.OK
            server.verify(2, getRequestedFor(urlPathEqualTo("/flaky")))
        }

        "not retry on 4xx client errors" {
            server.stubFor(get(urlPathEqualTo("/bad")).willReturn(aResponse().withStatus(400)))

            createHttpClient(testConfig(maxRetries = 3, retryDelay = 10.milliseconds))
                .get("http://localhost:${server.port()}/bad") {
                    expectSuccess = false
                }

            server.verify(1, getRequestedFor(urlPathEqualTo("/bad")))
        }

        "apply constant delay between retries" {
            val delayMs = 200L
            server.stubFor(
                get(urlPathEqualTo("/slow"))
                    .inScenario("slow")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willSetStateTo("ok")
                    .willReturn(aResponse().withStatus(503))
            )
            server.stubFor(
                get(urlPathEqualTo("/slow"))
                    .inScenario("slow")
                    .whenScenarioStateIs("ok")
                    .willReturn(aResponse().withStatus(200))
            )

            val start = System.currentTimeMillis()
            createHttpClient(testConfig(maxRetries = 1, retryDelay = delayMs.milliseconds))
                .get("http://localhost:${server.port()}/slow") { expectSuccess = false }

            (System.currentTimeMillis() - start) shouldBeGreaterThanOrEqual delayMs
        }

        "handle 429 rate-limit responses via the rate-limit plugin" {
            server.stubFor(
                get(urlPathEqualTo("/limited"))
                    .inScenario("rate-limit")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willSetStateTo("ok")
                    .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1"))
            )
            server.stubFor(
                get(urlPathEqualTo("/limited"))
                    .inScenario("rate-limit")
                    .whenScenarioStateIs("ok")
                    .willReturn(aResponse().withStatus(200))
            )

            val response = createHttpClient(testConfig(rateLimitMaxRetries = 2))
                .get("http://localhost:${server.port()}/limited") { expectSuccess = false }

            response.status shouldBe HttpStatusCode.OK
            server.verify(2, getRequestedFor(urlPathEqualTo("/limited")))
        }

        "apply additional config from the block" {
            server.stubFor(get(urlPathEqualTo("/additional")).willReturn(aResponse().withStatus(200)))

            val response = createHttpClient(testConfig()) {
                defaultRequest {
                    header("X-Custom-Header", "test-value")
                }
            }.get("http://localhost:${server.port()}/additional") {
                expectSuccess = false
            }

            response.status shouldBe HttpStatusCode.OK
            server.verify(
                getRequestedFor(urlPathEqualTo("/additional"))
                    .withHeader("X-Custom-Header", equalTo("test-value"))
            )
        }

        "enforce the request timeout" {
            server.stubFor(
                get(urlPathEqualTo("/timeout"))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(2000))
            )

            shouldThrow<Exception> {
                createHttpClient(testConfig(requestTimeout = 200.milliseconds))
                    .get("http://localhost:${server.port()}/timeout")
            }
        }

        "create a client from the configuration with overrides" {
            server.stubFor(
                get(urlPathEqualTo("/timeout"))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(2000))
            )

            shouldThrow<Exception> {
                createHttpClient("specialHttpClient")
                    .get("http://localhost:${server.port()}/timeout")
            }
        }
    }
})
