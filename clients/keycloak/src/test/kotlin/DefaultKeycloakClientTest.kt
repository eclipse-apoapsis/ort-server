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

import dasniko.testcontainers.keycloak.KeycloakContainer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldStartWith

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.clients.keycloak.test.KeycloakTestExtension
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_CLIENT_SECRET
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_CONFIDENTIAL_CLIENT
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_REALM
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm

import org.keycloak.admin.client.Keycloak

class DefaultKeycloakClientTest : AbstractKeycloakClientTest() {
    // For performance reasons, the test realm is created only once per spec. Therefore, all tests that modify data must
    // not modify the predefined test data and clean up after themselves to ensure that tests are isolated.
    private val keycloak = install(KeycloakTestExtension(clientTestRealm)) {
        setUpConfidentialClientRoles()
    }

    override val client = keycloak.createTestClient()

    init {
        "create" should {
            "not throw any instantiation exception" {
                val incorrectConfig = keycloak.createKeycloakClientConfigurationForTestRealm("falseSecret")

                val invalidClient = DefaultKeycloakClient.create(incorrectConfig, createJson())

                val exception = shouldThrow<KeycloakClientException> {
                    invalidClient.getRoles()
                }

                exception.message shouldStartWith "Failed to load roles"
            }

            "support the client credentials grant type" {
                val config = keycloak.createKeycloakClientConfigurationForTestRealm(
                    secret = TEST_CLIENT_SECRET,
                    user = null,
                    clientId = TEST_CONFIDENTIAL_CLIENT
                )
                val confidentialClient = DefaultKeycloakClient.create(config, createJson())

                // Test an arbitrary API call
                val groups = confidentialClient.getGroups()

                groups shouldNot beEmpty()
            }
        }

        "getGroups" should {
            "return right number of groups with chunk size smaller than total number of groups" {
                val config = keycloak.createKeycloakClientConfigurationForTestRealm(
                    secret = TEST_CLIENT_SECRET,
                    user = null,
                    clientId = TEST_CONFIDENTIAL_CLIENT,
                    dataGetChunkSize = 2
                )
                val confidentialClient = DefaultKeycloakClient.create(config, createJson())

                val groups = confidentialClient.getGroups()

                groups shouldHaveSize 3
                groups.map { it.name.value } shouldContainAll
                    listOf("Organization-A", "Organization-B", "Organization-C")
            }

            "return filtered groups list" {
                val config = keycloak.createKeycloakClientConfigurationForTestRealm(
                    secret = TEST_CLIENT_SECRET,
                    user = null,
                    clientId = TEST_CONFIDENTIAL_CLIENT
                )
                val confidentialClient = DefaultKeycloakClient.create(config, createJson())

                val groups = confidentialClient.getGroups(groupNameFilter = "B")

                groups shouldHaveSize 1
                groups.map { it.name.value } shouldContainAll
                    listOf("Organization-B")
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
