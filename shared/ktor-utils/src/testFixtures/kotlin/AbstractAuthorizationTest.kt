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

package org.eclipse.apoapsis.ortserver.shared.ktorutils

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase

import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.serialization
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.clients.keycloak.DefaultKeycloakClient.Companion.configureAuthentication
import org.eclipse.apoapsis.ortserver.clients.keycloak.User
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.KeycloakTestExtension
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.addUserRole
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createJwtConfigMapForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakClientForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.setUpClientScope
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.setUpUser
import org.eclipse.apoapsis.ortserver.components.authorization.AuthorizationException
import org.eclipse.apoapsis.ortserver.components.authorization.SecurityConfigurations
import org.eclipse.apoapsis.ortserver.components.authorization.configureAuthentication
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.services.AuthorizationService
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.utils.test.Integration

private val TEST_USER = User(
    id = UserId("test-user-id"),
    username = UserName("test-user"),
    firstName = "Test",
    lastName = "User",
    email = "test-user@example.org"
)

private const val TEST_USER_PASSWORD = "password"

/**
 * A base class for authorization tests that provides a database connection and a Keycloak setup, and helper functions
 * to easily test if endpoints require authentication or authorization.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class AbstractAuthorizationTest(body: AbstractAuthorizationTest.() -> Unit) : WordSpec() {
    val dbExtension = DatabaseTestExtension()
    val keycloakExtension = KeycloakTestExtension(createRealmPerTest = true)

    // The "extension()" and "install()" functions cannot be used above because of
    // https://github.com/kotest/kotest/issues/3555.
    // TODO: Check if this is fixed once Kotest 6 is released.
    override fun extensions(): List<Extension> = listOf(dbExtension, keycloakExtension)

    val keycloak = keycloakExtension.mount {
        setUpUser(TEST_USER, TEST_USER_PASSWORD)
        setUpClientScope(TEST_SUBJECT_CLIENT)
    }

    val json = Json { ignoreUnknownKeys = true }

    val keycloakClient = keycloak.createKeycloakClientForTestRealm()
    val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    val jwtConfig = keycloak.createJwtConfigMapForTestRealm()

    init {
        tags(Integration)
        body()
    }

    lateinit var authorizationService: AuthorizationService

    override suspend fun beforeEach(testCase: TestCase) {
        authorizationService = DefaultAuthorizationService(
            keycloakClient,
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            keycloakGroupPrefix = ""
        )

        authorizationService.ensureSuperuserAndSynchronizeRolesAndPermissions()
    }

    private fun authorizationTestApplication(
        routes: Route.() -> Unit,
        block: suspend ApplicationTestBuilder.(unauthenticatedClient: HttpClient, testUserClient: HttpClient) -> Unit
    ) {
        testApplication {
            val config = MapApplicationConfig()
            (keycloakConfig + jwtConfig).forEach { config.put(it.key, it.value) }
            config.put("jwt.audience", TEST_SUBJECT_CLIENT)

            environment {
                this.config = config
            }

            application {
                install(ContentNegotiation) {
                    serialization(ContentType.Application.Json, json)
                }

                install(StatusPages) {
                    exception<AuthorizationException> { call, _ ->
                        call.respond(HttpStatusCode.Forbidden)
                    }
                }

                configureAuthentication(config, keycloakClient)

                routing {
                    authenticate(SecurityConfigurations.TOKEN) {
                        routes()
                    }
                }
            }

            val clientConfig = keycloak.createKeycloakClientConfigurationForTestRealm(
                user = TEST_USER.username.value,
                secret = TEST_USER_PASSWORD
            )
            val testUserClient = createJsonClient().configureAuthentication(clientConfig, json)

            block(createJsonClient(), testUserClient)
        }
    }

    fun requestShouldRequireAuthentication(
        routes: Route.() -> Unit,
        successStatus: HttpStatusCode = HttpStatusCode.OK,
        request: suspend HttpClient.() -> HttpResponse
    ) {
        authorizationTestApplication(routes) { unauthenticatedClient, testUserClient ->
            unauthenticatedClient.request() shouldHaveStatus HttpStatusCode.Unauthorized
            testUserClient.request() shouldHaveStatus successStatus
        }
    }

    fun requestShouldRequireRole(
        routes: Route.() -> Unit,
        role: String,
        successStatus: HttpStatusCode = HttpStatusCode.OK,
        request: suspend HttpClient.() -> HttpResponse
    ) {
        authorizationTestApplication(routes) { _, testUserClient ->
            testUserClient.request() shouldHaveStatus HttpStatusCode.Forbidden
            keycloak.keycloakAdminClient.addUserRole(TEST_USER.username.value, role)
            testUserClient.request() shouldHaveStatus successStatus
        }
    }

    fun requestShouldRequireSuperuser(
        routes: Route.() -> Unit,
        successStatus: HttpStatusCode = HttpStatusCode.OK,
        request: suspend HttpClient.() -> HttpResponse
    ) {
        requestShouldRequireRole(routes, Superuser.ROLE_NAME, successStatus, request)
    }
}
