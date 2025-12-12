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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.clients.keycloak.test.testRealmAdmin

abstract class AbstractKeycloakClientTest : WordSpec() {
    abstract val client: KeycloakClient

    init {
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
