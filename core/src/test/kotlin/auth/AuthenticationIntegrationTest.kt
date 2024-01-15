/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.auth

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.util.pipeline.PipelineContext

import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.clients.keycloak.DefaultKeycloakClient.Companion.configureAuthentication
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.TEST_USER
import org.ossreviewtoolkit.server.core.TEST_USER_PASSWORD
import org.ossreviewtoolkit.server.core.authorization.OrtPrincipal
import org.ossreviewtoolkit.server.core.createJwtConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.plugins.SecurityConfigurations
import org.ossreviewtoolkit.server.core.setUpClientScope
import org.ossreviewtoolkit.server.core.setUpUser
import org.ossreviewtoolkit.server.core.setUpUserRoles
import org.ossreviewtoolkit.server.core.testutils.authNoDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.utils.test.Integration

class AuthenticationIntegrationTest : StringSpec({
    tags(Integration)

    val keycloak = install(KeycloakTestExtension(createRealmPerTest = true)) {
        setUpUser(TEST_USER, TEST_USER_PASSWORD)
    }

    val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    val keycloakClientConfig = keycloak.createKeycloakClientConfigurationForTestRealm()
    val jwtConfig = keycloak.createJwtConfigMapForTestRealm()

    val testUserClientConfig = keycloakClientConfig.copy(
        apiUser = TEST_USER.username.value,
        apiSecret = TEST_USER_PASSWORD
    )

    val json = Json { ignoreUnknownKeys = true }

    fun authTestApplication(
        onCall: PipelineContext<Unit, ApplicationCall>.() -> Unit = {},
        block: suspend ApplicationTestBuilder.() -> Unit
    ) =
        ortServerTestApplication(config = authNoDbConfig, additionalConfigs = keycloakConfig + jwtConfig) {
            routing {
                route("api/v1") {
                    authenticate(SecurityConfigurations.token) {
                        route("test") {
                            get {
                                onCall()
                                call.respond("OK")
                            }
                        }
                    }
                }
            }

            block()
        }

    "A request with a valid token should be accepted" {
        keycloak.keycloakAdminClient.setUpClientScope(TEST_SUBJECT_CLIENT)

        authTestApplication {
            val authenticatedClient = client.configureAuthentication(testUserClientConfig, json)

            authenticatedClient.get("/api/v1/test") shouldHaveStatus HttpStatusCode.OK
        }
    }

    "A request without a token should be rejected" {
        keycloak.keycloakAdminClient.setUpClientScope(TEST_SUBJECT_CLIENT)

        authTestApplication {
            client.get("/api/v1/test") shouldHaveStatus HttpStatusCode.Unauthorized
        }
    }

    "A token without an audience claim should be rejected" {
        authTestApplication {
            val authenticatedClient = client.configureAuthentication(testUserClientConfig, json)

            authenticatedClient.get("/api/v1/test") shouldHaveStatus HttpStatusCode.Unauthorized
        }
    }

    "A token with a wrong audience claim should be rejected" {
        keycloak.keycloakAdminClient.setUpClientScope(TEST_CLIENT)

        authTestApplication {
            val authenticatedClient = client.configureAuthentication(testUserClientConfig, json)

            authenticatedClient.get("/api/v1/test") shouldHaveStatus HttpStatusCode.Unauthorized
        }
    }

    "A principal with the correct client roles should be created" {
        keycloak.keycloakAdminClient.setUpClientScope(TEST_SUBJECT_CLIENT)
        keycloak.keycloakAdminClient.setUpUserRoles(TEST_USER.username.value, listOf("role-1", "role-2"))

        authTestApplication(onCall = {
            val principal = call.principal<OrtPrincipal>(SecurityConfigurations.token)

            principal.shouldNotBeNull()
            principal.roles should containExactlyInAnyOrder("role-1", "role-2")
        }) {
            val authenticatedClient = client.configureAuthentication(testUserClientConfig, json)

            authenticatedClient.get("/api/v1/test")
        }
    }
})
