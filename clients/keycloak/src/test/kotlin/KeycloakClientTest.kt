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
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import io.ktor.http.HttpStatusCode

import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_REALM
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_REALM_ADMIN_PASSWORD
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_REALM_ADMIN_USERNAME
import org.ossreviewtoolkit.server.clients.keycloak.test.testRealmAdmin

class KeycloakClientTest : WordSpec() {
    // For performance reasons the test realm is created only once per spec. Therefore, all tests that modify data must
    // not modify the predefined test data and clean up after themselves to ensure that tests are isolated.
    private val keycloak = install(KeycloakTestExtension(clientTestRealm))

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
                    client.getGroup(GroupId("1"))
                }
            }
        }

        "getGroupByName" should {
            "return the correct realm group" {
                client.getGroup(groupOrgA.name) shouldBe groupOrgA
                client.getGroup(subGroupOrgB1.name) shouldBe subGroupOrgB1
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getGroup(GroupName("1"))
                }
            }
        }

        "createGroup" should {
            "successfully add a new realm group" {
                val response = client.createGroup(GroupName("TEST_GROUP"))
                val group = client.getGroup(GroupName("TEST_GROUP"))

                response.status shouldBe HttpStatusCode.Created
                group.name shouldBe GroupName("TEST_GROUP")

                client.deleteGroup(group.id)
            }

            "throw an exception if a group with the name already exists" {
                shouldThrow<KeycloakClientException> {
                    client.createGroup(groupOrgA.name)
                }
            }
        }

        "updateGroup" should {
            "successfully update the given realm group" {
                client.createGroup(GroupName("TEST_GROUP"))
                val group = client.getGroup(GroupName("TEST_GROUP"))
                val updatedGroup = group.copy(name = GroupName("UPDATED_TEST_GROUP"))

                val response = client.updateGroup(group.id, updatedGroup.name)
                val updatedKeycloakGroup = client.getGroup(GroupName("UPDATED_TEST_GROUP"))

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakGroup shouldBe updatedGroup

                client.deleteGroup(updatedGroup.id)
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.updateGroup(GroupId("1"), GroupName("New-Organization"))
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
                client.createGroup(GroupName("TEST_GROUP"))
                val group = client.getGroup(GroupName("TEST_GROUP"))

                val response = client.deleteGroup(group.id)

                response.status shouldBe HttpStatusCode.NoContent

                shouldThrow<KeycloakClientException> {
                    client.getGroup(GroupName("TEST_GROUP"))
                }
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.deleteGroup(GroupId("1"))
                }
            }
        }

        "getRoles" should {
            "return the correct client roles" {
                val roles = client.getRoles()

                roles shouldContainExactlyInAnyOrder listOf(adminRole, visitorRole, compositeRole)
            }
        }

        "getRole by name" should {
            "return the correct client role" {
                val role = client.getRole(adminRole.name)

                role shouldBe adminRole
            }

            "throw an exception if the role does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getRole(RoleName("UNKNOWN_ROLE"))
                }
            }
        }

        "createRole" should {
            "successfully add a new client role" {
                val response = client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                response.status shouldBe HttpStatusCode.Created
                with(role) {
                    name shouldBe RoleName("TEST_ROLE")
                    description shouldBe "DESCRIPTION"
                }

                client.deleteRole(role.name)
            }

            "throw an exception if a role with the name already exists" {
                shouldThrow<KeycloakClientException> {
                    client.createRole(RoleName("ADMIN"))
                }
            }
        }

        "updateRole" should {
            "update only the name of the given client role" {
                client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                val updatedRole = role.copy(name = RoleName("UPDATED_ROLE"))
                val response = client.updateRole(role.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = client.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakRole shouldBe updatedRole

                client.deleteRole(updatedRole.name)
            }

            "update only the description of the given client role" {
                client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                val updatedRole = role.copy(description = "UPDATED_DESCRIPTION")
                val response = client.updateRole(role.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = client.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakRole shouldBe updatedRole

                client.deleteRole(role.name)
            }

            "successfully update the given client role" {
                client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                val updatedRole = role.copy(name = RoleName("UPDATED_ROLE"), description = "UPDATED_DESCRIPTION")
                val response = client.updateRole(role.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = client.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakRole shouldBe updatedRole

                client.deleteRole(updatedRole.name)
            }

            "throw an exception if a role cannot be updated" {
                shouldThrow<KeycloakClientException> {
                    client.updateRole(RoleName("UNKNOWN_ROLE"), RoleName("UPDATED_UNKNOWN_ROLE"), null)
                }
            }
        }

        "deleteRole" should {
            "successfully delete the given client role" {
                client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                val response = client.deleteRole(role.name)

                response.status shouldBe HttpStatusCode.NoContent

                shouldThrow<KeycloakClientException> {
                    client.getRole(role.name)
                }
            }

            "throw an exception if the role does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.deleteRole(RoleName("UNKNOWN_ROLE"))
                }
            }
        }

        "addCompositeRole" should {
            "successfully add a composite role" {
                client.createRole(RoleName("root"))
                client.createRole(RoleName("composite"))
                val compositeRole = client.getRole(RoleName("composite"))

                client.addCompositeRole(RoleName("root"), compositeRole.id)
                val composites = client.getCompositeRoles(RoleName("root"))

                composites shouldContainExactly listOf(compositeRole)

                client.deleteRole(RoleName("root"))
                client.deleteRole(RoleName("composite"))
            }

            "fail if the composite role does not exist" {
                client.createRole(RoleName("root"))

                shouldThrow<KeycloakClientException> {
                    client.addCompositeRole(RoleName("root"), RoleId("invalid id"))
                }

                client.deleteRole(RoleName("root"))
            }
        }

        "getCompositeRoles" should {
            "return the correct composite roles" {
                client.getCompositeRoles(adminRole.name) should beEmpty()
                client.getCompositeRoles(visitorRole.name) shouldContainExactly listOf(compositeRole)
            }
        }

        "getUsers" should {
            "return the correct realm users" {
                val users = client.getUsers()

                users shouldContainExactlyInAnyOrder setOf(testRealmAdmin, adminUser, visitorUser)
            }
        }

        "getUser" should {
            "return the correct realm user" {
                val user = client.getUser(adminUser.id)

                user shouldBe adminUser
            }

            "throw an exception if the user does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getUser(UserId("1"))
                }
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
            "successfully add a new realm user" {
                val response = client.createUser(UserName("test_user"))
                val user = client.getUser(UserName("test_user"))

                response.status shouldBe HttpStatusCode.Created

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
                val response = client.updateUser(id = user.id, firstName = updatedUser.firstName)
                val updatedKeycloakUser = client.getUser(user.id)

                response.status shouldBe HttpStatusCode.NoContent
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
                val response = client.updateUser(
                    user.id,
                    updatedUser.username,
                    updatedUser.firstName,
                    updatedUser.lastName,
                    updatedUser.email
                )

                val updatedKeycloakUser = client.getUser(user.id)

                response.status shouldBe HttpStatusCode.NoContent
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

                val response = client.deleteUser(user.id)

                response.status shouldBe HttpStatusCode.NoContent

                shouldThrow<KeycloakClientException> {
                    client.getUser(user.id)
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
    KeycloakClient.create(createConfig(), createJson())

/**
 * Generate a configuration with test properties based on this container to be consumed by a test client instance.
 */
private fun KeycloakContainer.createConfig(secret: String = TEST_REALM_ADMIN_PASSWORD) =
    KeycloakClientConfiguration(
        apiUrl = "${authServerUrl}admin/realms/$TEST_REALM",
        clientId = TEST_CLIENT,
        accessTokenUrl = "${authServerUrl}realms/$TEST_REALM/protocol/openid-connect/token",
        apiUser = TEST_REALM_ADMIN_USERNAME,
        apiSecret = secret
    )

/**
 * Create the [Json] instance required by the test client.
 */
private fun createJson(): Json = Json {
    ignoreUnknownKeys = true
}
