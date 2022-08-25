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

package org.ossreviewtoolkit.server.core.client

import com.typesafe.config.ConfigFactory

import dasniko.testcontainers.keycloak.KeycloakContainer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.testcontainers.TestContainerExtension
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import io.ktor.http.HttpStatusCode
import io.ktor.server.config.HoconApplicationConfig

import kotlinx.serialization.json.Json

class KeycloakServiceTest : WordSpec() {
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
        "create" should {
            "not throw any instantiation exception" {
                val incorrectConfig = keycloak.createConfig("falseSecret")

                val service = KeycloakService.create(incorrectConfig, createJson())

                val exception = shouldThrow<KeycloakServiceException> {
                    service.getRoles()
                }

                exception.message shouldStartWith "Failed to load roles"
            }
        }

        "getGroups" should {
            "return the correct realm groups" {
                val service = keycloak.createTestService()

                val groups = service.getGroups()

                groups shouldContainAnyOf setOf(groupOrgA, groupOrgB, groupOrgC)
            }
        }

        "getGroup by ID" should {
            "return the correct realm group" {
                val service = keycloak.createTestService()

                val group = service.getGroup(groupOrgA.id)

                group shouldBe groupOrgA
            }

            "throw an exception if the group does not exist" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.getGroup("1")
                }
            }
        }

        "createGroup" should {
            "add successfully a new realm group" {
                val service = keycloak.createTestService()

                val response = service.createGroup("TEST_GROUP")
                val keycloakGroups = service.getGroups()

                response.status shouldBe HttpStatusCode.Created
                keycloakGroups.size shouldBe 4
            }

            "throw an exception if a group with the name already exists" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.createGroup(groupOrgA.name)
                }
            }
        }

        "updateGroup" should {
            "update successfully the given realm group" {
                val service = keycloak.createTestService()

                val updatedGroup = groupOrgA.copy(name = "New-Organization-A")
                val response = service.updateGroup(groupOrgA.id, updatedGroup.name)
                val updatedKeycloakGroup = service.getGroup(groupOrgA.id)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakGroup shouldBe updatedGroup
            }

            "throw an exception if the group does not exist" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.updateGroup("1", "New-Organization")
                }
            }

            "throw an exception if a group with the name already exists" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.updateGroup(groupOrgA.id, groupOrgB.name)
                }
            }
        }

        "deleteGroup" should {
            "delete successfully the given realm group" {
                val service = keycloak.createTestService()

                val response = service.deleteGroup(groupOrgA.id)
                val groups = service.getGroups()

                response.status shouldBe HttpStatusCode.NoContent
                groups.map(Group::name) shouldNotContain groupOrgA.name
            }

            "throw an exception if the group does not exist" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.deleteGroup("1")
                }
            }
        }

        "getRoles" should {
            "return the correct client roles" {
                val service = keycloak.createTestService()

                val roles = service.getRoles()

                roles shouldContainAnyOf listOf(adminRole, visitorRole)
            }
        }

        "getRole by name" should {
            "return the correct client role" {
                val service = keycloak.createTestService()

                val role = service.getRole(adminRole.name)

                role shouldBe adminRole
            }

            "throw an exception if the role does not exist" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.getRole("UNKNOWN_ROLE")
                }
            }
        }

        "createRole" should {
            "add successfully a new client role" {
                val service = keycloak.createTestService()

                val response = service.createRole("TEST_ROLE", "Created for testing purposes.")
                val keycloakRoles = service.getRoles()

                response.status shouldBe HttpStatusCode.Created
                keycloakRoles.size shouldBe 3
            }

            "throw an exception if a role with the name already exists" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.createRole("ADMIN")
                }
            }
        }

        "updateRole" should {
            "update only the name of the given client role" {
                val service = keycloak.createTestService()

                val updatedRole = visitorRole.copy(name = "UPDATED_VISITOR")
                val response = service.updateRole(visitorRole.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = service.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakRole shouldBe updatedRole
            }

            "update only the description of the given client role" {
                val service = keycloak.createTestService()

                val updatedRole = adminRole.copy(description = "This role is for admins.")
                val response = service.updateRole(adminRole.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = service.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakRole shouldBe updatedRole
            }

            "update successfully the given client role" {
                val service = keycloak.createTestService()

                val updatedRole = adminRole.copy(name = "UPDATED_ADMIN", description = "The updated role description.")
                val response = service.updateRole(adminRole.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakService = service.getRole(updatedRole.name)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakService shouldBe updatedRole
            }

            "throw an exception if a role cannot be updated" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.updateRole("UNKOWN_ROLE", "UPDATED_UNKNOWN_ROLE", null)
                }
            }
        }

        "deleteRole" should {
            "delete successfully the given client role" {
                val service = keycloak.createTestService()

                val role = visitorRole.copy(name = "UPDATED_VISITOR")
                val response = service.deleteRole(role.name)
                val keycloakRoles = service.getRoles()

                response.status shouldBe HttpStatusCode.NoContent
                keycloakRoles.map(Role::name) shouldNotContain role.name
            }

            "throw an exception if the role does not exist" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.deleteRole("UNKNOWN_ROLE")
                }
            }
        }

        "getUsers" should {
            "return the correct realm users" {
                val service = keycloak.createTestService()

                val users = service.getUsers()

                users shouldContainAnyOf setOf(adminUser, ortAdminUser, visitorUser)
            }
        }

        "getUser by ID" should {
            "return the correct realm user" {
                val service = keycloak.createTestService()

                val user = service.getUser(adminUser.id)

                user shouldBe adminUser
            }

            "throw an exception if the user does not exist" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.getUser("1")
                }
            }
        }

        "createUser" should {
            "add successfully a new realm user" {
                val service = keycloak.createTestService()

                val response = service.createUser("new-test-user")
                val keycloakUsers = service.getUsers()

                response.status shouldBe HttpStatusCode.Created
                keycloakUsers.size shouldBe 4
            }

            "throw an exception if a user with the username already exists" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.createUser(adminUser.username)
                }
            }
        }

        "updateUser" should {
            "update only the firstname of the user" {
                val service = keycloak.createTestService()

                val updatedUser = visitorUser.copy(firstName = "New First Name")
                val response = service.updateUser(id = visitorUser.id, firstName = updatedUser.firstName)
                val updatedKeycloakUser = service.getUser(visitorUser.id)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakUser shouldBe updatedUser
            }

            "update successfully the given realm user" {
                val service = keycloak.createTestService()

                val updatedUser = visitorUser.copy(email = "updated-visitor-mail@org.com")
                val response = service.updateUser(
                    updatedUser.id,
                    updatedUser.username,
                    updatedUser.firstName,
                    updatedUser.lastName,
                    updatedUser.email
                )

                val updatedKeycloakUser = service.getUser(visitorUser.id)

                response.status shouldBe HttpStatusCode.NoContent
                updatedKeycloakUser shouldBe updatedUser
            }

            "throw an exception if a user cannot be updated" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.updateUser(visitorUser.id, email = adminUser.email)
                }
            }
        }

        "deleteUser" should {
            "delete successfully the given realm user" {
                val service = keycloak.createTestService()

                val response = service.deleteUser(visitorUser.id)
                val keycloakUsers = service.getUsers()

                response.status shouldBe HttpStatusCode.NoContent
                keycloakUsers.map(User::username) shouldNotContain visitorUser.username
            }

            "throw an exception if the user does not exist" {
                val service = keycloak.createTestService()

                shouldThrow<KeycloakServiceException> {
                    service.deleteUser("1")
                }
            }
        }
    }
}

/**
 * Create a test service instance that is configured to access the Keycloak instance managed by this container.
 */
private fun KeycloakContainer.createTestService(): KeycloakService =
    KeycloakService.create(createConfig(), createJson())

/**
 * Generate a configuration with test properties based on this container to be consumed by a test service instance.
 */
private fun KeycloakContainer.createConfig(secret: String = API_SECRET): HoconApplicationConfig {
    val configMap = mapOf(
        "keycloak.accessTokenUrl" to "${authServerUrl}realms/$REALM/protocol/openid-connect/token",
        "keycloak.apiUrl" to "${authServerUrl}admin/realms/$REALM",
        "keycloak.clientId" to CLIENT_ID,
        "keycloak.apiUser" to API_USER,
        "keycloak.apiSecret" to secret
    )

    return HoconApplicationConfig(ConfigFactory.parseMap(configMap))
}

/**
 * Create the [Json] instance required by the test service.
 */
private fun createJson(): Json = Json {
    ignoreUnknownKeys = true
}
