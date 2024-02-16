/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.clients.keycloak.DefaultKeycloakClient.Companion.configureAuthentication
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.SUPERUSER
import org.ossreviewtoolkit.server.core.SUPERUSER_PASSWORD
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.createJwtConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.setUpClientScope
import org.ossreviewtoolkit.server.core.setUpUser
import org.ossreviewtoolkit.server.core.setUpUserRoles
import org.ossreviewtoolkit.server.core.testutils.authNoDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.authorization.Superuser
import org.ossreviewtoolkit.server.utils.test.Integration

/**
 * Integration test class testing some error conditions during API requests and how they are handled.
 */
class ErrorsIntegrationTest : StringSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private val keycloak = install(KeycloakTestExtension()) {
        setUpUser(SUPERUSER, SUPERUSER_PASSWORD)
        setUpUserRoles(SUPERUSER.username.value, listOf(Superuser.ROLE_NAME))
        setUpClientScope(TEST_SUBJECT_CLIENT)
    }

    private val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    private val jwtConfig = keycloak.createJwtConfigMapForTestRealm()
    private val additionalConfig = keycloakConfig + jwtConfig

    private val superuserClientConfig = keycloak.createKeycloakClientConfigurationForTestRealm(
        user = SUPERUSER.username.value,
        secret = SUPERUSER_PASSWORD
    )

    private val json = Json { ignoreUnknownKeys = true }

    init {
        tags(Integration)

        "An unauthorized call yields the correct status code" {
            ortServerTestApplication(dbExtension.db, authNoDbConfig, additionalConfig) {
                val client = createJsonClient()

                client.get("/api/v1/organizations") shouldHaveStatus HttpStatusCode.Unauthorized
            }
        }

        "Sorting by an unsupported field should be handled" {
            ortServerTestApplication(dbExtension.db, authNoDbConfig, additionalConfig) {
                val client = createJsonClient().configureAuthentication(superuserClientConfig, json)

                val response = client.get("/api/v1/organizations?sort=color")

                response shouldHaveStatus HttpStatusCode.BadRequest
                response.body<ErrorResponse>().cause shouldContain "color"
            }
        }

        "An invalid limit parameter should be handled" {
            val limitValue = "a-couple-of"

            ortServerTestApplication(dbExtension.db, authNoDbConfig, additionalConfig) {
                val client = createJsonClient().configureAuthentication(superuserClientConfig, json)

                val response = client.get("/api/v1/organizations?limit=$limitValue")

                response shouldHaveStatus HttpStatusCode.BadRequest
                val cause = response.body<ErrorResponse>().cause
                cause shouldContain "'$limitValue'"
                cause shouldContain "'limit'"
            }
        }

        "An invalid offset parameter should be handled" {
            val offsetValue = "a-quarter"

            ortServerTestApplication(dbExtension.db, authNoDbConfig, additionalConfig) {
                val client = createJsonClient().configureAuthentication(superuserClientConfig, json)

                val response = client.get("/api/v1/organizations?limit=25&offset=$offsetValue")

                response shouldHaveStatus HttpStatusCode.BadRequest
                val cause = response.body<ErrorResponse>().cause
                cause shouldContain "'$offsetValue'"
                cause shouldContain "'offset'"
            }
        }
    }
}
