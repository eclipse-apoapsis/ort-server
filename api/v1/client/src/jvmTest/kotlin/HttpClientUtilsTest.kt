/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.client.createDefaultHttpClient

class HttpClientUtilsTest : WordSpec({
    "A default HTTP client" should {
        "retry a GET request with a 504 status code" {
            val maxRetries = 2
            var attempt = 0
            val mockEngine = MockEngine {
                attempt += 1
                if (attempt <= maxRetries) {
                    respond(content = "Gateway Timeout", status = HttpStatusCode.GatewayTimeout)
                } else {
                    respond(content = "OK")
                }
            }

            val client = createDefaultHttpClient(engine = mockEngine, maxRetriesOnTimeout = maxRetries)
            val response = client.get("http://test.example.org")

            response.status shouldBe HttpStatusCode.OK
        }

        "retry only the configured number of times" {
            val maxRetries = 1
            var attempt = 0
            val mockEngine = MockEngine {
                attempt += 1
                respond(content = "Gateway Timeout", status = HttpStatusCode.GatewayTimeout)
            }

            val client = createDefaultHttpClient(engine = mockEngine, maxRetriesOnTimeout = maxRetries)
            val response = client.get("http://test.example.org")

            response.status shouldBe HttpStatusCode.GatewayTimeout
            attempt shouldBe maxRetries + 1
        }

        "not retry a GET request with a different status code" {
            var attempt = 0
            val mockEngine = MockEngine {
                attempt += 1
                respond(content = "Bad Request", status = HttpStatusCode.BadRequest)
            }

            val client = createDefaultHttpClient(engine = mockEngine)
            val response = client.get("http://test.example.org")

            response.status shouldBe HttpStatusCode.BadRequest
            attempt shouldBe 1
        }

        "not retry a request with a different method" {
            var attempt = 0
            val mockEngine = MockEngine {
                attempt += 1
                respond(content = "Gateway Timeout", status = HttpStatusCode.GatewayTimeout)
            }

            val client = createDefaultHttpClient(engine = mockEngine)
            val response = client.post("http://test.example.org")

            response.status shouldBe HttpStatusCode.GatewayTimeout
            attempt shouldBe 1
        }

        "allow disabling the retry mechanism" {
            var attempt = 0
            val mockEngine = MockEngine {
                attempt += 1
                respond(content = "Gateway Timeout", status = HttpStatusCode.GatewayTimeout)
            }

            val client = createDefaultHttpClient(engine = mockEngine, maxRetriesOnTimeout = 0)
            val response = client.get("http://test.example.org")

            response.status shouldBe HttpStatusCode.GatewayTimeout
            attempt shouldBe 1
        }
    }
})
