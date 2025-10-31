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

package org.eclipse.apoapsis.ortserver.components.authorization.service

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientException
import org.eclipse.apoapsis.ortserver.clients.keycloak.User as KeycloakUser
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.model.User

class KeycloakUserServiceTest : WordSpec({
    "createUser" should {
        "create a user" {
            val username = "test-user"
            val firstName = "Test"
            val lastName = "User"
            val email = "test-user@example.org"
            val password = "secure-password"
            val temporary = false

            val client = mockk<KeycloakClient> {
                coEvery { createUser(any(), any(), any(), any(), any(), any()) } just runs
            }

            val service = KeycloakUserService(client)
            service.createUser(
                username = username,
                firstName = firstName,
                lastName = lastName,
                email = email,
                password = password,
                temporary = temporary
            )

            coVerify {
                client.createUser(
                    username = UserName(username),
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    password = password,
                    temporary = temporary
                )
            }
        }
    }

    "deleteUser" should {
        "delete a user" {
            val keycloakUser = createKeycloakUser(1)

            val client = mockk<KeycloakClient> {
                coEvery { getUser(keycloakUser.username) } returns keycloakUser
                coEvery { deleteUser(any()) } just runs
            }

            val service = KeycloakUserService(client)
            service.deleteUser(keycloakUser.username.value)

            coVerify {
                client.deleteUser(keycloakUser.id)
            }
        }
    }

    "getUsers" should {
        "return a set of all users" {
            val userCount = 16
            val keycloakUsers = (1..userCount).mapTo(mutableSetOf(), ::createKeycloakUser)
            val expectedUsers = (1..userCount).mapTo(mutableSetOf(), ::createUser)

            val client = mockk<KeycloakClient> {
                coEvery { getUsers() } returns keycloakUsers
            }

            val service = KeycloakUserService(client)
            val users = service.getUsers()

            users shouldContainExactlyInAnyOrder expectedUsers
        }
    }

    "getUserById" should {
        "retrieve a user by its Keycloak username" {
            val keycloakUser = createKeycloakUser(42)
            val expectedUser = createUser(42)

            val client = mockk<KeycloakClient> {
                coEvery { getUser(keycloakUser.username) } returns keycloakUser
            }

            val service = KeycloakUserService(client)
            val user = service.getUserById(keycloakUser.username.value)

            user shouldBe expectedUser
        }
    }

    "getUsersById" should {
        "retrieve multiple users by their Keycloak usernames" {
            val userCount = 8
            val keycloakUsers = (1..userCount).mapTo(mutableSetOf(), ::createKeycloakUser)
            val expectedUsers = (1..userCount).mapTo(mutableSetOf(), ::createUser)
            val userIds = keycloakUsers.mapTo(mutableSetOf()) { it.username.value }

            val client = mockk<KeycloakClient> {
                keycloakUsers.forEach {
                    coEvery { getUser(it.username) } returns it
                }
            }

            val service = KeycloakUserService(client)
            val users = service.getUsersById(userIds)

            users shouldContainExactly expectedUsers
        }
    }

    "existsUsername" should {
        "return true if the username can be resolved" {
            val username = "existing-user"
            val client = mockk<KeycloakClient> {
                coEvery { getUser(UserName(username)) } returns createKeycloakUser(1)
            }

            val service = KeycloakUserService(client)
            service.existsUser(username) shouldBe true
        }

        "return false if the username does not exist" {
            val username = "non-existing-user"
            val client = mockk<KeycloakClient> {
                coEvery { getUser(UserName(username)) } throws KeycloakClientException("User not found")
            }

            val service = KeycloakUserService(client)
            service.existsUser(username) shouldBe false
        }
    }
})

/**
 * Generate a test user in Keycloak with properties derived from the given [index].
 */
private fun createKeycloakUser(index: Int): KeycloakUser =
    KeycloakUser(
        id = UserId("id-$index"),
        username = UserName("user$index"),
        firstName = "First$index",
        lastName = "Last$index",
        email = "user$index@example.com"
    )

/**
 * Generate a test user in the ORT Server model with properties derived from the given [index].
 */
private fun createUser(index: Int): User =
    User(
        username = "user$index",
        firstName = "First$index",
        lastName = "Last$index",
        email = "user$index@example.com"
    )
