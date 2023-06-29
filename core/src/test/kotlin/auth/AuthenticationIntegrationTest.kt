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

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder

import kotlinx.serialization.json.Json

import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.keycloak.representations.idm.ProtocolMapperRepresentation

import org.ossreviewtoolkit.server.clients.keycloak.DefaultKeycloakClient.Companion.configureAuthentication
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_REALM
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.plugins.SecurityConfigurations
import org.ossreviewtoolkit.server.core.testutils.authNoDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication

class AuthenticationIntegrationTest : StringSpec({
    val keycloak = install(KeycloakTestExtension(createRealmPerTest = true))
    val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    val keycloakClientConfig = keycloak.createKeycloakClientConfigurationForTestRealm()
    val jwtConfig = mapOf(
        "jwt.jwksUri" to "${keycloak.authServerUrl}realms/$TEST_REALM/protocol/openid-connect/certs",
        "jwt.issuer" to "${keycloak.authServerUrl}realms/$TEST_REALM",
        "jwt.realm" to TEST_REALM,
        "jwt.audience" to keycloakClientConfig.subjectClientId
    )

    val json = Json { ignoreUnknownKeys = true }

    fun authTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) =
        ortServerTestApplication(config = authNoDbConfig, additionalConfigs = keycloakConfig + jwtConfig) {
            routing {
                route("api/v1") {
                    authenticate(SecurityConfigurations.token) {
                        route("test") {
                            get {
                                call.respond("OK")
                            }
                        }
                    }
                }
            }

            block()
        }

    "A request with a valid token should be accepted" {
        keycloak.keycloakAdminClient.setupClientScope(TEST_SUBJECT_CLIENT)

        authTestApplication {
            val authenticatedClient = client.configureAuthentication(keycloakClientConfig, json)

            val response = authenticatedClient.get("/api/v1/test")

            response shouldHaveStatus HttpStatusCode.OK
        }
    }

    "A request without a token should be rejected" {
        keycloak.keycloakAdminClient.setupClientScope(TEST_SUBJECT_CLIENT)

        authTestApplication {
            val response = client.get("/api/v1/test")

            response shouldHaveStatus HttpStatusCode.Unauthorized
        }
    }

    "A token without an audience claim should be rejected" {
        authTestApplication {
            val authenticatedClient = client.configureAuthentication(keycloakClientConfig, json, expectSuccess = false)

            val response = authenticatedClient.get("/api/v1/test")

            response shouldHaveStatus HttpStatusCode.Unauthorized
        }
    }

    "A token with a wrong audience claim should be rejected" {
        keycloak.keycloakAdminClient.setupClientScope(TEST_CLIENT)

        authTestApplication {
            val authenticatedClient = client.configureAuthentication(keycloakClientConfig, json, expectSuccess = false)

            val response = authenticatedClient.get("/api/v1/test")

            response shouldHaveStatus HttpStatusCode.Unauthorized
        }
    }
})

/**
 * Create an audience mapper that adds the provided [audience] to the access token.
 */
private fun audienceMapper(audience: String) = ProtocolMapperRepresentation().apply {
    name = "audience-mapper"
    protocol = "openid-connect"
    protocolMapper = "oidc-audience-mapper"
    config = mapOf(
        "included.client.audience" to audience,
        "id.token.claim" to "false",
        "access.token.claim" to "true",
        "userinfo.token.claim" to "false"
    )
}

/**
 * Create a client scope that maps the provided [audience] to the audience in the JWT token and add the created scope to
 * the default scopes of the [TEST_CLIENT].
 */
private fun Keycloak.setupClientScope(audience: String) {
    val subjectClientScope = "$TEST_SUBJECT_CLIENT-scope"

    realm(TEST_REALM).apply {
        clientScopes().create(
            ClientScopeRepresentation().apply {
                name = subjectClientScope
                protocol = "openid-connect"
                protocolMappers = listOf(audienceMapper(audience))
            }
        )

        val testClient = clients().get(clients().findByClientId(TEST_CLIENT).first().id)
        val clientScope = clientScopes().findAll().single { it.name == subjectClientScope }
        testClient.addDefaultClientScope(clientScope.id)
    }
}
