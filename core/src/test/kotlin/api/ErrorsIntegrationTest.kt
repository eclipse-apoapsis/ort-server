/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.api

import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension

/**
 * Integration test class testing some error conditions during API requests and how they are handled.
 */
class ErrorsIntegrationTest : StringSpec() {
    private val keycloak = install(KeycloakTestExtension())
    private val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()

    init {
        extension(DatabaseTestExtension())

        "An unauthorized call yields the correct status code" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val response = client.get("/api/v1/organizations")

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "Sorting by an unsupported field should be handled" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val response = client.get("/api/v1/organizations?sort=color") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.BadRequest
                    body<ErrorResponse>().cause shouldContain "color"
                }
            }
        }

        "An invalid limit parameter should be handled" {
            val limitValue = "a-couple-of"

            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val response = client.get("/api/v1/organizations?limit=$limitValue") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.BadRequest
                    val cause = body<ErrorResponse>().cause
                    cause shouldContain "'$limitValue'"
                    cause shouldContain "'limit'"
                }
            }
        }

        "An invalid offset parameter should be handled" {
            val offsetValue = "a-quarter"

            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val response = client.get("/api/v1/organizations?limit=25&offset=$offsetValue") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.BadRequest
                    val cause = body<ErrorResponse>().cause
                    cause shouldContain "'$offsetValue'"
                    cause shouldContain "'offset'"
                }
            }
        }
    }
}
