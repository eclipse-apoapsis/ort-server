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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.clients.keycloak.test.testRealmAdmin

abstract class AbstractKeycloakClientTest : WordSpec() {
    abstract val client: KeycloakClient

    init {
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
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getGroup(GroupName("1"))
                }
            }
        }

        "searchGroups" should {
            // The Keycloak docs do not describe how exactly the non-exact search works, so test observed scenarios.
            // See: https://www.keycloak.org/docs-api/latest/rest-api/index.html#_get_adminrealmsrealmgroups
            "ignore a trailing underscore" {
                client.createGroup(GroupName("ORGANIZATION_1_READERS"))
                client.createGroup(GroupName("ORGANIZATION_2_READERS"))
                client.createGroup(GroupName("ORGANIZATION_11_READERS"))
                client.createGroup(GroupName("ORGANIZATION_111_READERS"))

                client.searchGroups(GroupName("ORGANIZATION_1_")).map { it.name.value } shouldContainExactlyInAnyOrder
                        setOf(
                            "ORGANIZATION_1_READERS",
                            "ORGANIZATION_11_READERS",
                            "ORGANIZATION_111_READERS"
                        )

                client.deleteGroup(client.getGroup(GroupName("ORGANIZATION_1_READERS")).id)
                client.deleteGroup(client.getGroup(GroupName("ORGANIZATION_2_READERS")).id)
                client.deleteGroup(client.getGroup(GroupName("ORGANIZATION_11_READERS")).id)
                client.deleteGroup(client.getGroup(GroupName("ORGANIZATION_111_READERS")).id)
            }
        }

        "createGroup" should {
            "successfully add a new realm group" {
                client.createGroup(GroupName("TEST_GROUP"))
                val group = client.getGroup(GroupName("TEST_GROUP"))

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

                client.updateGroup(group.id, updatedGroup.name)
                val updatedKeycloakGroup = client.getGroup(GroupName("UPDATED_TEST_GROUP"))

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

                client.deleteGroup(group.id)

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

        "getGroupClientRoles" should {
            "return the correct client roles" {
                client.getGroupClientRoles(groupOrgA.id) should beEmpty()
                client.getGroupClientRoles(groupOrgB.id) shouldContainExactlyInAnyOrder
                        listOf(visitorRole, compositeRole)
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getGroupClientRoles(GroupId("1"))
                }
            }
        }

        "addGroupClientRole" should {
            "successfully add a client role to a group" {
                val groupName = GroupName("group")
                client.createGroup(groupName)
                val group = client.getGroup(groupName)

                client.addGroupClientRole(group.id, visitorRole)

                client.getGroupClientRoles(group.id) shouldContainExactlyInAnyOrder listOf(visitorRole, compositeRole)

                client.deleteGroup(group.id)
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.addGroupClientRole(GroupId("1"), adminRole)
                }
            }

            "throw an exception if the role does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.addGroupClientRole(groupOrgA.id, Role(id = RoleId("1"), name = RoleName("1")))
                }
            }
        }

        "removeGroupClientRole" should {
            "successfully remove a client role from a group" {
                val groupName = GroupName("group")
                client.createGroup(groupName)
                val group = client.getGroup(groupName)

                client.addGroupClientRole(group.id, visitorRole)
                client.removeGroupClientRole(group.id, visitorRole)

                client.getGroupClientRoles(group.id) should beEmpty()

                client.deleteGroup(group.id)
            }

            "throw an exception if the group does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.removeGroupClientRole(GroupId("1"), adminRole)
                }
            }

            "throw an exception if the role does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.removeGroupClientRole(groupOrgA.id, Role(id = RoleId("1"), name = RoleName("1")))
                }
            }
        }

        "getRoles" should {
            "return the correct client roles" {
                val roles = client.getRoles()

                roles shouldContainExactlyInAnyOrder listOf(visitorRole, compositeRole)
            }
        }

        "getRole by name" should {
            "return the correct client role" {
                val role = client.getRole(visitorRole.name)

                role shouldBe visitorRole
            }

            "throw an exception if the role does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getRole(RoleName("UNKNOWN_ROLE"))
                }
            }
        }

        "createRole" should {
            "successfully add a new client role" {
                client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                with(role) {
                    name shouldBe RoleName("TEST_ROLE")
                    description shouldBe "DESCRIPTION"
                }

                client.deleteRole(role.name)
            }

            "throw an exception if a role with the name already exists" {
                shouldThrow<KeycloakClientException> {
                    client.createRole(RoleName("VISITOR"))
                }
            }
        }

        "updateRole" should {
            "update only the name of the given client role" {
                client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                val updatedRole = role.copy(name = RoleName("UPDATED_ROLE"))
                client.updateRole(role.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = client.getRole(updatedRole.name)

                updatedKeycloakRole shouldBe updatedRole

                client.deleteRole(updatedRole.name)
            }

            "update only the description of the given client role" {
                client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                val updatedRole = role.copy(description = "UPDATED_DESCRIPTION")
                client.updateRole(role.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = client.getRole(updatedRole.name)

                updatedKeycloakRole shouldBe updatedRole

                client.deleteRole(role.name)
            }

            "successfully update the given client role" {
                client.createRole(RoleName("TEST_ROLE"), "DESCRIPTION")
                val role = client.getRole(RoleName("TEST_ROLE"))

                val updatedRole = role.copy(name = RoleName("UPDATED_ROLE"), description = "UPDATED_DESCRIPTION")
                client.updateRole(role.name, updatedRole.name, updatedRole.description)
                val updatedKeycloakRole = client.getRole(updatedRole.name)

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

                client.deleteRole(role.name)

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
                client.getCompositeRoles(compositeRole.name) should beEmpty()
                client.getCompositeRoles(visitorRole.name) shouldContainExactly listOf(compositeRole)
            }
        }

        "removeCompositeRole" should {
            "successfully remove a composite role" {
                client.createRole(RoleName("root"))
                client.createRole(RoleName("composite"))
                val compositeRole = client.getRole(RoleName("composite"))
                client.addCompositeRole(RoleName("root"), compositeRole.id)

                client.removeCompositeRole(RoleName("root"), compositeRole.id)
                val composites = client.getCompositeRoles(RoleName("root"))

                composites shouldNotContain listOf(compositeRole)

                client.deleteRole(RoleName("root"))
                client.deleteRole(RoleName("composite"))
            }

            "fail if the composite role does not exist" {
                client.createRole(RoleName("root"))

                shouldThrow<KeycloakClientException> {
                    client.removeCompositeRole(RoleName("root"), RoleId("invalid id"))
                }

                client.deleteRole(RoleName("root"))
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
            "successfully add a new realm user without credentials" {
                client.createUser(UserName("test_user"))
                val user = client.getUser(UserName("test_user"))

                user.username shouldBe UserName("test_user")
                client.getUserHasCredentials(UserName("test_user")) shouldBe false

                client.deleteUser(user.id)
            }

            "successfully add a new realm user with credentials" {
                client.createUser(username = UserName("test_user"), password = "test123", temporary = true)
                val user = client.getUser(UserName("test_user"))

                user.username shouldBe UserName("test_user")
                client.getUserHasCredentials(UserName("test_user")) shouldBe true

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
                val updatedKeycloakUser = client.getUser(user.id)

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

                val updatedKeycloakUser = client.getUser(user.id)

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
                    client.getUser(user.id)
                }
            }

            "throw an exception if the user does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.deleteUser(UserId("1"))
                }
            }
        }

        "getUserClientRoles" should {
            "return the correct client roles" {
                client.getUserClientRoles(adminUser.id) should beEmpty()
                client.getUserClientRoles(visitorUser.id) shouldContainExactlyInAnyOrder
                        listOf(visitorRole, compositeRole)
            }

            "throw an exception if the user does not exist" {
                shouldThrow<KeycloakClientException> {
                    client.getUserClientRoles(UserId("1"))
                }
            }
        }
    }
}
