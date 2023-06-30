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

import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.keycloak.representations.idm.ProtocolMapperRepresentation
import org.keycloak.representations.idm.RoleRepresentation

import org.ossreviewtoolkit.server.clients.keycloak.DefaultKeycloakClient.Companion.configureAuthentication
import org.ossreviewtoolkit.server.clients.keycloak.User
import org.ossreviewtoolkit.server.clients.keycloak.UserId
import org.ossreviewtoolkit.server.clients.keycloak.UserName
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_REALM
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.toUserRepresentation
import org.ossreviewtoolkit.server.core.authorization.OrtPrincipal
import org.ossreviewtoolkit.server.core.plugins.SecurityConfigurations
import org.ossreviewtoolkit.server.core.testutils.authNoDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication

private val TEST_USER = User(
    id = UserId("test-user-id"),
    username = UserName("test-user"),
    firstName = "Test",
    lastName = "User",
    email = "test-user@example.org"
)

private const val TEST_USER_PASSWORD = "password"

class AuthenticationIntegrationTest : StringSpec({
    val keycloak = install(KeycloakTestExtension(createRealmPerTest = true)) {
        setupUser(TEST_USER, TEST_USER_PASSWORD)
    }

    val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    val keycloakClientConfig = keycloak.createKeycloakClientConfigurationForTestRealm()
    val jwtConfig = mapOf(
        "jwt.jwksUri" to "${keycloak.authServerUrl}realms/$TEST_REALM/protocol/openid-connect/certs",
        "jwt.issuer" to "${keycloak.authServerUrl}realms/$TEST_REALM",
        "jwt.realm" to TEST_REALM,
        "jwt.audience" to keycloakClientConfig.subjectClientId
    )

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
        keycloak.keycloakAdminClient.setupClientScope(TEST_SUBJECT_CLIENT)

        authTestApplication {
            val authenticatedClient = client.configureAuthentication(testUserClientConfig, json)

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
            val authenticatedClient = client.configureAuthentication(testUserClientConfig, json)

            val response = authenticatedClient.get("/api/v1/test")

            response shouldHaveStatus HttpStatusCode.Unauthorized
        }
    }

    "A token with a wrong audience claim should be rejected" {
        keycloak.keycloakAdminClient.setupClientScope(TEST_CLIENT)

        authTestApplication {
            val authenticatedClient = client.configureAuthentication(testUserClientConfig, json)

            val response = authenticatedClient.get("/api/v1/test")

            response shouldHaveStatus HttpStatusCode.Unauthorized
        }
    }

    "A principal with the correct client roles should be created" {
        keycloak.keycloakAdminClient.setupClientScope(TEST_SUBJECT_CLIENT)
        keycloak.keycloakAdminClient.setupUserRoles(TEST_USER.username.value, listOf("role-1", "role-2"))

        authTestApplication(onCall = {
            val principal = call.principal<OrtPrincipal>(SecurityConfigurations.token)

            principal.shouldNotBeNull()
            principal.roles should containExactlyInAnyOrder("role-1", "role-2")
        }) {
            environment {
                // Turn off development mode to fix loading the principal. For some reason, in development mode the
                // OrtPrincipal class is loaded from different class loaders which causes the isInstance check inside of
                // call.principal() to fail.
                developmentMode = false
            }

            val authenticatedClient = client.configureAuthentication(testUserClientConfig, json)

            authenticatedClient.get("/api/v1/test")
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
 * Create a [user] with the provided [password].
 */
private fun Keycloak.setupUser(user: User, password: String) {
    realm(TEST_REALM).apply {
        users().create(user.toUserRepresentation(password = password))
    }
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

/**
 * Create the provided [roles] in the [TEST_SUBJECT_CLIENT] and assign them to the provided [username].
 */
private fun Keycloak.setupUserRoles(username: String, roles: List<String>) {
    realm(TEST_REALM).apply {
        val client = clients().findByClientId(TEST_SUBJECT_CLIENT).single()

        val roleRepresentations = roles.map {
            clients().get(client.id).roles().run {
                create(RoleRepresentation().apply { name = it })
                get(it).toRepresentation()
            }
        }

        val user = users().search(username).single()
        users().get(user.id).roles().clientLevel(client.id).add(roleRepresentations)
    }
}
