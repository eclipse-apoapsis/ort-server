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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.User
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField

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

    "listUsers" should {
        "return a deterministic default page ordered by username" {
            val keycloakUsers = (25 downTo 1).mapTo(mutableSetOf()) { index ->
                createKeycloakUser(index, username = "user${index.toString().padStart(2, '0')}")
            }
            val service = createService(keycloakUsers)

            val result = service.listUsers()

            result.data.map(User::username) shouldContainExactly (1..ListQueryParameters.DEFAULT_LIMIT).map { index ->
                "user${index.toString().padStart(2, '0')}"
            }
            result.params shouldBe ListQueryParameters(limit = ListQueryParameters.DEFAULT_LIMIT, offset = 0)
            result.totalCount shouldBe 25L
        }

        "apply limit and offset after sorting" {
            val service = createService(
                setOf(
                    createKeycloakUser(1, username = "charlie"),
                    createKeycloakUser(2, username = "alpha"),
                    createKeycloakUser(3, username = "bravo")
                )
            )

            val result = service.listUsers(ListQueryParameters(limit = 1, offset = 1))

            result.data.map(User::username) shouldContainExactly listOf("bravo")
            result.totalCount shouldBe 3L
        }

        "return an empty page for an offset greater than the integer range" {
            val service = createService(setOf(createKeycloakUser(1)))

            val result = service.listUsers(ListQueryParameters(offset = Int.MAX_VALUE.toLong() + 1))

            result.data.shouldBeEmpty()
            result.totalCount shouldBe 1L
        }

        "preserve all users for null and blank searches" {
            val service = createService(setOf(createKeycloakUser(1), createKeycloakUser(2)))

            service.listUsers(search = null).totalCount shouldBe 2L
            service.listUsers(search = "  ").totalCount shouldBe 2L
        }

        "match a partial case-insensitive search against every user field" {
            val keycloakUsers = setOf(
                createKeycloakUser(1, username = "Matching-Username"),
                createKeycloakUser(2, firstName = "Matching-FirstName"),
                createKeycloakUser(3, lastName = "Matching-LastName"),
                createKeycloakUser(4, email = "matching-email@example.org"),
                createKeycloakUser(5)
            )
            val service = createService(keycloakUsers)

            val result = service.listUsers(ListQueryParameters(limit = 2, offset = 1), "MaTcHiNg")

            result.data.map(User::username) shouldContainExactly listOf("user2", "user3")
            result.totalCount shouldBe 4L
        }

        "sort every supported field in ascending and descending order" {
            val keycloakUsers = setOf(
                createKeycloakUser(
                    1,
                    username = "charlie",
                    firstName = "Charlie",
                    lastName = null,
                    email = "charlie@example.org"
                ),
                createKeycloakUser(
                    2,
                    username = "alpha",
                    firstName = null,
                    lastName = "Alpha",
                    email = "alpha@example.org"
                ),
                createKeycloakUser(
                    3,
                    username = "bravo",
                    firstName = "Bravo",
                    lastName = "Bravo",
                    email = null
                )
            )
            val service = createService(keycloakUsers)
            val expectedAscending = mapOf(
                "username" to listOf("alpha", "bravo", "charlie"),
                "firstName" to listOf("alpha", "bravo", "charlie"),
                "lastName" to listOf("charlie", "alpha", "bravo"),
                "email" to listOf("bravo", "alpha", "charlie")
            )

            expectedAscending.forEach { (sortField, expected) ->
                val ascending = service.listUsers(queryParameters(sortField, OrderDirection.ASCENDING))
                val descending = service.listUsers(queryParameters(sortField, OrderDirection.DESCENDING))

                ascending.data.map(User::username) shouldContainExactly expected
                descending.data.map(User::username) shouldContainExactly expected.reversed()
            }
        }

        "apply multiple sort fields in request order" {
            val service = createService(
                setOf(
                    createKeycloakUser(1, username = "charlie", firstName = "Same", lastName = "Zulu"),
                    createKeycloakUser(2, username = "alpha", firstName = "Same", lastName = "Alpha"),
                    createKeycloakUser(3, username = "bravo", firstName = "Different", lastName = "Middle")
                )
            )
            val parameters = ListQueryParameters(
                sortFields = listOf(
                    OrderField("firstName", OrderDirection.ASCENDING),
                    OrderField("lastName", OrderDirection.DESCENDING)
                )
            )

            val result = service.listUsers(parameters)

            result.data.map(User::username) shouldContainExactly listOf("bravo", "charlie", "alpha")
        }

        "use username as a tie-breaker" {
            val service = createService(
                setOf(
                    createKeycloakUser(1, username = "charlie", firstName = "Same"),
                    createKeycloakUser(2, username = "alpha", firstName = "Same"),
                    createKeycloakUser(3, username = "bravo", firstName = "Same")
                )
            )

            val result = service.listUsers(queryParameters("firstName", OrderDirection.DESCENDING))

            result.data.map(User::username) shouldContainExactly listOf("alpha", "bravo", "charlie")
        }

        "reject empty and unsupported sort fields" {
            val service = createService(setOf(createKeycloakUser(1)))

            shouldThrow<QueryParametersException> {
                service.listUsers(queryParameters("", OrderDirection.ASCENDING))
            }
            shouldThrow<QueryParametersException> {
                service.listUsers(queryParameters("unknown", OrderDirection.ASCENDING))
            }
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

        "ignore unknown user IDs" {
            val existingKeycloakUser = createKeycloakUser(1)
            val expectedUser = createUser(1)
            val userIds = setOf(existingKeycloakUser.username.value, "non-existing-user")

            val client = mockk<KeycloakClient> {
                coEvery { getUser(existingKeycloakUser.username) } returns existingKeycloakUser
                coEvery { getUser(UserName("non-existing-user")) } throws KeycloakClientException("User not found")
            }

            val service = KeycloakUserService(client)
            val users = service.getUsersById(userIds)

            users shouldContainExactly setOf(expectedUser)
        }
    }

    "userExists" should {
        "return true if the username can be resolved" {
            val username = "existing-user"
            val client = mockk<KeycloakClient> {
                coEvery { getUser(UserName(username)) } returns createKeycloakUser(1)
            }

            val service = KeycloakUserService(client)
            service.userExists(username) shouldBe true
        }

        "return false if the username does not exist" {
            val username = "non-existing-user"
            val client = mockk<KeycloakClient> {
                coEvery { getUser(UserName(username)) } throws KeycloakClientException("User not found")
            }

            val service = KeycloakUserService(client)
            service.userExists(username) shouldBe false
        }
    }
})

/**
 * Generate a test user in Keycloak with properties derived from the given [index].
 */
private fun createKeycloakUser(
    index: Int,
    username: String = "user$index",
    firstName: String? = "First$index",
    lastName: String? = "Last$index",
    email: String? = "user$index@example.com"
): KeycloakUser =
    KeycloakUser(
        id = UserId("id-$index"),
        username = UserName(username),
        firstName = firstName,
        lastName = lastName,
        email = email
    )

/**
 * Create a user service backed by a client that returns the given [users].
 */
private fun createService(users: Set<KeycloakUser>): KeycloakUserService {
    val client = mockk<KeycloakClient> {
        coEvery { getUsers() } returns users
    }

    return KeycloakUserService(client)
}

/**
 * Create list query parameters for the given sort [field] and [direction].
 */
private fun queryParameters(field: String, direction: OrderDirection) =
    ListQueryParameters(sortFields = listOf(OrderField(field, direction)))

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
