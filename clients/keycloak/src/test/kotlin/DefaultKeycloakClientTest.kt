/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.clients.keycloak

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import dasniko.testcontainers.keycloak.KeycloakContainer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldStartWith

import kotlin.time.Duration.Companion.milliseconds

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.TokenInfo
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.KeycloakTestExtension
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_CLIENT_SECRET
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_CONFIDENTIAL_CLIENT
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_REALM
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.testRealmAdmin

import org.keycloak.admin.client.Keycloak

class DefaultKeycloakClientTest : WordSpec() {
    // For performance reasons, the test realm is created only once per spec. Therefore, all tests that modify data must
    // not modify the predefined test data and clean up after themselves to ensure that tests are isolated.
    private val keycloak = install(KeycloakTestExtension(clientTestRealm)) {
        setUpConfidentialClientRoles()
    }

    val client = keycloak.createTestClient()

    init {
        "create" should {
            "not throw any instantiation exception" {
                val incorrectConfig = keycloak.createKeycloakClientConfigurationForTestRealm("falseSecret")

                val invalidClient = DefaultKeycloakClient.create(incorrectConfig, createJson())

                val exception = shouldThrow<KeycloakClientException> {
                    invalidClient.getUsers()
                }

                exception.message shouldStartWith "Failed to load users"
            }

            "support the client credentials grant type" {
                val config = keycloak.createKeycloakClientConfigurationForTestRealm(
                    secret = TEST_CLIENT_SECRET,
                    user = "",
                    clientId = TEST_CONFIDENTIAL_CLIENT
                )
                val confidentialClient = DefaultKeycloakClient.create(config, createJson())

                // Test an arbitrary API call
                val users = confidentialClient.getUsers()

                users shouldNot beEmpty()
            }

            "correctly configure the timeout" {
                val json = createJson()
                val user = User(UserId("u1"), UserName("user1"))
                val tokenInfo = TokenInfo("some-token")

                val server = WireMockServer(WireMockConfiguration.options().dynamicPort())
                server.start()
                try {
                    server.stubFor(
                        post(anyUrl())
                            .willReturn(
                                aResponse()
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(json.encodeToString(tokenInfo))
                            )
                    )

                    server.stubFor(
                        get(anyUrl())
                            .willReturn(
                                aResponse()
                                    .withBody(json.encodeToString(user))
                                    .withHeader("Content-Type", "application/json")
                                    .withFixedDelay(500)
                            )
                    )

                    val config = KeycloakClientConfiguration(
                        baseUrl = server.baseUrl(),
                        realm = "some-realm",
                        apiUrl = "${server.baseUrl()}/admin/realms/some-realm",
                        clientId = "some-client",
                        accessTokenUrl = "${server.baseUrl()}/realms/some-realm/protocol/openid-connect/token",
                        apiUser = "some-user",
                        apiSecret = "some-secret",
                        subjectClientId = "some-subject-client",
                        timeout = 50.milliseconds
                    )

                    val client = DefaultKeycloakClient.create(config, json)
                    shouldThrow<KeycloakClientException> {
                        client.getUser(UserName("user1"))
                    }
                } finally {
                    server.stop()
                }
            }
        }

        "getUsers" should {
            "return the correct realm users" {
                val users = client.getUsers()

                users shouldContainExactlyInAnyOrder setOf(testRealmAdmin, adminUser, visitorUser)
            }
        }

        "getUserByName" should {
            "return the correct realm user" {
                client.getUser(adminUser.username) shouldBe adminUser
            }

            "throw an exception if the user does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getUser(UserName("1"))
                }
            }
        }

        "createUser" should {
            "successfully add a new realm user without credentials" {
                client.createUser(UserName("test_user"))
                val user = client.getUser(UserName("test_user"))

                user.username shouldBe UserName("test_user")

                client.deleteUser(user.id)
            }

            "successfully add a new realm user with credentials" {
                client.createUser(username = UserName("test_user"), password = "test123", temporary = true)
                val user = client.getUser(UserName("test_user"))

                user.username shouldBe UserName("test_user")

                client.deleteUser(user.id)
            }

            "throw an exception if a user with the username already exists" {
                shouldThrow<KeycloakClientException> {
                    client.createUser(adminUser.username)
                }
            }
        }

        "updateUser" should {
            "update only the firstname of the user" {
                client.createUser(UserName("test_user"), firstName = "firstName")
                val user = client.getUser(UserName("test_user"))

                val updatedUser = user.copy(firstName = "updatedFirstName")
                client.updateUser(id = user.id, firstName = updatedUser.firstName)
                val updatedKeycloakUser = client.getUser(user.username)

                updatedKeycloakUser shouldBe updatedUser

                client.deleteUser(user.id)
            }

            "successfully update the given realm user" {
                client.createUser(
                    UserName("test_user"),
                    firstName = "firstName",
                    lastName = "lastName",
                    email = "email@example.com"
                )
                val user = client.getUser(UserName("test_user"))

                val updatedUser = user.copy(
                    firstName = "updatedFirstName",
                    lastName = "updatedLastName",
                    email = "updated_email@example.com"
                )
                client.updateUser(
                    user.id,
                    updatedUser.username,
                    updatedUser.firstName,
                    updatedUser.lastName,
                    updatedUser.email
                )

                val updatedKeycloakUser = client.getUser(user.username)

                updatedKeycloakUser shouldBe updatedUser

                client.deleteUser(user.id)
            }

            "throw an exception if a user cannot be updated" {
                shouldThrow<KeycloakClientException> {
                    client.updateUser(visitorUser.id, email = adminUser.email)
                }
            }
        }

        "deleteUser" should {
            "successfully delete the given realm user" {
                client.createUser(UserName("test_user"))
                val user = client.getUser(UserName("test_user"))

                client.deleteUser(user.id)

                shouldThrow<KeycloakClientException> {
                    client.getUser(user.username)
                }
            }

            "throw an exception if the user does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.deleteUser(UserId("1"))
                }
            }
        }
    }
}

/**
 * Create a test client instance that is configured to access the Keycloak instance managed by this container.
 */
private fun KeycloakContainer.createTestClient(): KeycloakClient =
    DefaultKeycloakClient.create(createKeycloakClientConfigurationForTestRealm(), createJson())

/**
 * Create the [Json] instance required by the test client.
 */
private fun createJson(): Json = Json {
    ignoreUnknownKeys = true
}

/**
 * Configure the role assignments for the confidential test client. The client is granted all roles supported by the
 * realm-management client; so it can be used in the same way as the client backed by the admin user.
 */
private fun Keycloak.setUpConfidentialClientRoles() {
    val realm = realm(TEST_REALM)
    val realmManagementClient = realm.clients().findByClientId("realm-management").single()

    val serviceAccount = realm.users().search("service-account-$TEST_CONFIDENTIAL_CLIENT", true).single()
    val serviceAccountResource = realm.users().get(serviceAccount.id)

    val clientRoles = serviceAccountResource.roles().clientLevel(realmManagementClient.id)
    val availableRoles = clientRoles.listAvailable()

    clientRoles.add(availableRoles)
}
