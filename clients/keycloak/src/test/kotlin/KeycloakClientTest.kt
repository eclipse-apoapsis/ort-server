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

package org.ossreviewtoolkit.server.clients.keycloak

import dasniko.testcontainers.keycloak.KeycloakContainer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.testcontainers.TestContainerExtension
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import io.ktor.http.HttpStatusCode

import kotlinx.serialization.json.Json

class KeycloakClientTest : WordSpec() {
    private val keycloak = install(
        TestContainerExtension(
            KeycloakContainer()
                .withRealmImportFile("test-realm.json")
        )
    )

    override suspend fun beforeSpec(spec: Spec) {
        listeners(keycloak.perSpec())
    }

    init {
        val client = keycloak.createTestClient()

        "create" should {
            "not throw any instantiation exception" {
                val incorrectConfig = keycloak.createConfig("falseSecret")

                val invalidClient = KeycloakClient.create(incorrectConfig, createJson())

                val exception = shouldThrow<KeycloakClientException> {
                    invalidClient.getRoles()
                }

                exception.message shouldStartWith "Failed to load roles"
            }
        }

        "getGroups" should {
            "return the correct realm groups" {
                val groups = client.getGroups()

                groups shouldContainExactlyInAnyOrder setOf(groupOrgA, groupOrgB, groupOrgC)
            }
        }

        "getGroup" should {
            "return the correct realm group" {
                val group = client.getGroup(groupOrgA.id)

                group shouldBe groupOrgA
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getGroup("1")
                }
            }
        }

        "getGroupByName" should {
            "return the correct realm group" {
                client.getGroupByName(groupOrgA.name) shouldBe groupOrgA
                client.getGroupByName(subGroupOrgB1.name) shouldBe subGroupOrgB1
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getGroupByName("1")
                }
            }
        }

        "createGroup" should {
            "successfully add a new realm group" {
                val response = client.createGroup("TEST_GROUP")
                val keycloakGroups = client.getGroups()

                response.status shouldBe HttpStatusCode.Created
                keycloakGroups.size shouldBe 4
            }

            "throw an exception if a group with the name already exists" {
                shouldThrow<KeycloakClientException> {
                    client.createGroup(groupOrgA.name)
                }
            }
        }

        "updateGroup" should {
            "successfully update the given realm group" {
                val updatedGroup = groupOrgA.copy(name = "New-Organization-A")
                val response = client.updateGroup(groupOrgA.id, updatedGroup.name)
                val updatedKeycloakGroup = client.getGroup(groupOrgA.id)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakGroup shouldBe updatedGroup
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.updateGroup("1", "New-Organization")
                }
            }

            "throw an exception if a group with the name already exists" {
                shouldThrow<KeycloakClientException> {
                    client.updateGroup(groupOrgA.id, groupOrgB.name)
                }
            }
        }

        "deleteGroup" should {
            "successfully delete the given realm group" {
                val response = client.deleteGroup(groupOrgA.id)
                val groups = client.getGroups()

                response.status shouldBe HttpStatusCode.NoContent
                groups.map(Group::name) shouldNotContain groupOrgA.name
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.deleteGroup("1")
                }
            }
        }

        "getRoles" should {
            "return the correct client roles" {
                val roles = client.getRoles()

                roles shouldContainExactlyInAnyOrder listOf(adminRole, visitorRole)
            }
        }

        "getRole by name" should {
            "return the correct client role" {
                val role = client.getRole(adminRole.name)

                role shouldBe adminRole
            }

            "throw an exception if the role does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getRole("UNKNOWN_ROLE")
                }
            }
        }

        "createRole" should {
            "successfully add a new client role" {
                val response = client.createRole("TEST_ROLE", "Created for testing purposes.")
                val keycloakRoles = client.getRoles()

                response.status shouldBe HttpStatusCode.Created
                keycloakRoles.size shouldBe 3
            }

            "throw an exception if a role with the name already exists" {
                shouldThrow<KeycloakClientException> {
                    client.createRole("ADMIN")
                }
            }
        }

        "updateRole" should {
            "update only the name of the given client role" {
                val updatedRole = visitorRole.copy(name = "UPDATED_VISITOR")
                val response = client.updateRole(visitorRole.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = client.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakRole shouldBe updatedRole
            }

            "update only the description of the given client role" {
                val updatedRole = adminRole.copy(description = "This role is for admins.")
                val response = client.updateRole(adminRole.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = client.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakRole shouldBe updatedRole
            }

            "successfully update the given client role" {
                val updatedRole = adminRole.copy(name = "UPDATED_ADMIN", description = "The updated role description.")
                val response = client.updateRole(adminRole.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakclient = client.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakclient shouldBe updatedRole
            }

            "throw an exception if a role cannot be updated" {
                shouldThrow<KeycloakClientException> {
                    client.updateRole("UNKOWN_ROLE", "UPDATED_UNKNOWN_ROLE", null)
                }
            }
        }

        "deleteRole" should {
            "successfully delete the given client role" {
                val role = visitorRole.copy(name = "UPDATED_VISITOR")
                val response = client.deleteRole(role.name)
                val keycloakRoles = client.getRoles()

                response.status shouldBe HttpStatusCode.NoContent
                keycloakRoles.map(Role::name) shouldNotContain role.name
            }

            "throw an exception if the role does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.deleteRole("UNKNOWN_ROLE")
                }
            }
        }

        "getUsers" should {
            "return the correct realm users" {
                val users = client.getUsers()

                users shouldContainExactlyInAnyOrder setOf(adminUser, ortAdminUser, visitorUser)
            }
        }

        "getUser by ID" should {
            "return the correct realm user" {
                val user = client.getUser(adminUser.id)

                user shouldBe adminUser
            }

            "throw an exception if the user does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getUser("1")
                }
            }
        }

        "createUser" should {
            "successfully add a new realm user" {
                val response = client.createUser("new-test-user")
                val keycloakUsers = client.getUsers()

                response.status shouldBe HttpStatusCode.Created
                keycloakUsers.size shouldBe 4
            }

            "throw an exception if a user with the username already exists" {
                shouldThrow<KeycloakClientException> {
                    client.createUser(adminUser.username)
                }
            }
        }

        "updateUser" should {
            "update only the firstname of the user" {
                val updatedUser = visitorUser.copy(firstName = "New First Name")
                val response = client.updateUser(id = visitorUser.id, firstName = updatedUser.firstName)
                val updatedKeycloakUser = client.getUser(visitorUser.id)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakUser shouldBe updatedUser
            }

            "successfully update the given realm user" {
                val updatedUser = visitorUser.copy(email = "updated-visitor-mail@org.com")
                val response = client.updateUser(
                    updatedUser.id,
                    updatedUser.username,
                    updatedUser.firstName,
                    updatedUser.lastName,
                    updatedUser.email
                )

                val updatedKeycloakUser = client.getUser(visitorUser.id)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakUser shouldBe updatedUser
            }

            "throw an exception if a user cannot be updated" {
                shouldThrow<KeycloakClientException> {
                    client.updateUser(visitorUser.id, email = adminUser.email)
                }
            }
        }

        "deleteUser" should {
            "successfully delete the given realm user" {
                val response = client.deleteUser(visitorUser.id)
                val keycloakUsers = client.getUsers()

                response.status shouldBe HttpStatusCode.NoContent
                keycloakUsers.map(User::username) shouldNotContain visitorUser.username
            }

            "throw an exception if the user does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.deleteUser("1")
                }
            }
        }
    }
}

/**
 * Create a test client instance that is configured to access the Keycloak instance managed by this container.
 */
private fun KeycloakContainer.createTestClient(): KeycloakClient =
    KeycloakClient.create(createConfig(), createJson())

/**
 * Generate a configuration with test properties based on this container to be consumed by a test client instance.
 */
private fun KeycloakContainer.createConfig(secret: String = API_SECRET) =
    KeycloakClientConfiguration(
        apiUrl = "${authServerUrl}admin/realms/$REALM",
        clientId = CLIENT_ID,
        accessTokenUrl = "${authServerUrl}realms/$REALM/protocol/openid-connect/token",
        apiUser = API_USER,
        apiSecret = secret
    )

/**
 * Create the [Json] instance required by the test client.
 */
private fun createJson(): Json = Json {
    ignoreUnknownKeys = true
}
