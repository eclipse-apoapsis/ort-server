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

package org.eclipse.apoapsis.ortserver.core.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.eclipse.apoapsis.ortserver.api.v1.model.PagingOptions
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.User as ApiUser
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup as ApiUserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups as ApiUserWithGroups
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.User as ModelUser
import org.eclipse.apoapsis.ortserver.model.UserGroup as ModelUserGroup

class UserWithGroupsHelperTest : WordSpec({
    "sortAndPage" should {
        "be paged" {
            // Given
            val users = listOf(
                ApiUserWithGroups(ApiUser("john1hh", "John", "Doe", "j.d1@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john2hh", "John", "Doe", "j.d2@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john3hh", "John", "Doe", "j.d3@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john4hh", "John", "Doe", "j.d4@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john5hh", "John", "Doe", "j.d5@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john6hh", "John", "Doe", "j.d6@example.com"), listOf(ApiUserGroup.ADMINS))
            )

            val pagingOptions = PagingOptions(2, 2, listOf(SortProperty("username", SortDirection.ASCENDING)))

            // When
            val pagedUsers = users.sortAndPage(pagingOptions)

            // Then
            pagedUsers.size shouldBe 2
            pagedUsers[0].user.username shouldBe "john3hh"
            pagedUsers[1].user.username shouldBe "john4hh"
        }

        "sort list by given user field" {
            // Given
            val users = listOf(
                ApiUserWithGroups(ApiUser("john6hh", "John", "Doe", "j.d6@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john2hh", "John", "Doe", "j.d2@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john1hh", "John", "Doe", "j.d1@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john5hh", "John", "Doe", "j.d5@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john4hh", "John", "Doe", "j.d4@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john3hh", "John", "Doe", "j.d3@example.com"), listOf(ApiUserGroup.ADMINS))
            )

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("username", SortDirection.ASCENDING)))

            // When
            val pagedUsers = users.sortAndPage(pagingOptions)

            // Then
            pagedUsers.size shouldBe 6
            pagedUsers[0].user.username shouldBe "john1hh"
            pagedUsers[1].user.username shouldBe "john2hh"
            pagedUsers[2].user.username shouldBe "john3hh"
            pagedUsers[3].user.username shouldBe "john4hh"
            pagedUsers[4].user.username shouldBe "john5hh"
            pagedUsers[5].user.username shouldBe "john6hh"
        }

        "sort list by group field" {
            // Given
            val users = listOf(
                ApiUserWithGroups(ApiUser("john6hh", "John", "Doe", "j.d6@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john2hh", "John", "Doe", "j.d2@example.com"), listOf(ApiUserGroup.WRITERS)),
                ApiUserWithGroups(ApiUser("john1hh", "John", "Doe", "j.d1@example.com"), listOf(ApiUserGroup.ADMINS)),
                ApiUserWithGroups(ApiUser("john5hh", "John", "Doe", "j.d5@example.com"), listOf(ApiUserGroup.READERS)),
                ApiUserWithGroups(ApiUser("john4hh", "John", "Doe", "j.d4@example.com"), listOf(ApiUserGroup.READERS)),
                ApiUserWithGroups(ApiUser("john3hh", "John", "Doe", "j.d3@example.com"), listOf(ApiUserGroup.WRITERS))
            )

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("group", SortDirection.DESCENDING)))

            // When
            val pagedUsers = users.sortAndPage(pagingOptions)

            // Then
            pagedUsers.size shouldBe 6
            pagedUsers[4].groups[0] shouldBe ApiUserGroup.READERS
            pagedUsers[5].groups[0] shouldBe ApiUserGroup.READERS
            pagedUsers[2].groups[0] shouldBe ApiUserGroup.WRITERS
            pagedUsers[3].groups[0] shouldBe ApiUserGroup.WRITERS
            pagedUsers[1].groups[0] shouldBe ApiUserGroup.ADMINS
            pagedUsers[0].groups[0] shouldBe ApiUserGroup.ADMINS
        }

        "sort list by highest ranked group for user with multiple groups" {
            // Given
            val users = listOf(
                ApiUserWithGroups(
                    ApiUser("john6hh", "John", "Doe", "j.d6@example.com"),
                    listOf(ApiUserGroup.ADMINS, ApiUserGroup.READERS)
                ),
                ApiUserWithGroups(
                    ApiUser("john2hh", "John", "Doe", "j.d2@example.com"),
                    listOf(ApiUserGroup.WRITERS, ApiUserGroup.READERS)
                ),
                ApiUserWithGroups(
                    ApiUser("john1hh", "John", "Doe", "j.d1@example.com"),
                    listOf(ApiUserGroup.READERS)
                )
            )

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("group", SortDirection.ASCENDING)))

            // When
            val pagedUsers = users.sortAndPage(pagingOptions)

            // Then
            pagedUsers.size shouldBe 3
            pagedUsers[0].groups[0] shouldBe ApiUserGroup.ADMINS
            pagedUsers[0].user.username shouldBe "john6hh"
            pagedUsers[1].groups[0] shouldBe ApiUserGroup.WRITERS
            pagedUsers[1].user.username shouldBe "john2hh"
            pagedUsers[2].groups[0] shouldBe ApiUserGroup.READERS
            pagedUsers[2].user.username shouldBe "john1hh"
        }

        "throw an exception if sort field is empty" {
            // Given
            val users = listOf(
                ApiUserWithGroups(
                    ApiUser("john6hh", "John", "Doe", "j.d6@example.com"),
                    listOf(ApiUserGroup.ADMINS, ApiUserGroup.READERS)
                )
            )

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("", SortDirection.ASCENDING)))

            // Then
            val exception = shouldThrow<QueryParametersException> { users.sortAndPage(pagingOptions) }
            exception.message shouldBe "Empty sort field."
        }

        "throw an exception if sort field has wrong name" {
            // Given
            val users = listOf(
                ApiUserWithGroups(
                    ApiUser("john6hh", "John", "Doe", "j.d6@example.com"),
                    listOf(ApiUserGroup.ADMINS, ApiUserGroup.READERS)
                )
            )

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("blobby", SortDirection.ASCENDING)))

            // Then
            val exception = shouldThrow<QueryParametersException> { users.sortAndPage(pagingOptions) }
            exception.message shouldBe "Unknown sort field 'blobby'."
        }

        "throw an exception if number of sort fields is higher than 1" {
            // Given
            val users = listOf(
                ApiUserWithGroups(
                    ApiUser("john6hh", "John", "Doe", "j.d6@example.com"),
                    listOf(ApiUserGroup.ADMINS, ApiUserGroup.READERS)
                )
            )

            val pagingOptions = PagingOptions(
                999,
                0,
                listOf(
                    SortProperty("group", SortDirection.ASCENDING),
                    SortProperty("username", SortDirection.ASCENDING),
                )
            )

            // Then
            val exception = shouldThrow<QueryParametersException> { users.sortAndPage(pagingOptions) }
            exception.message shouldBe "Exactly one sort field must be defined."
        }
    }

    "mapToApi" should {
        "map service output model to API model" {
            // Given
            val modelUsers = mapOf(
                Pair(
                    ModelUser("john2hh", "John", "Doe", "john.doe@example.com"),
                    setOf(ModelUserGroup.ADMINS, ModelUserGroup.WRITERS)
                ),
                Pair(
                    ModelUser("ron5ff", "Ron", "Boo", "ron.boo@example.com"),
                    setOf(ModelUserGroup.WRITERS, ModelUserGroup.ADMINS, ModelUserGroup.READERS)
                )
            )

            // When
            val apiUsers = modelUsers.mapToApi()

            // Then
            apiUsers.size shouldBeExactly 2
            val user1 = apiUsers.find { it.user.username == "john2hh" }
            user1 shouldNotBe null
            user1?.user?.firstName shouldBe "John"
            user1?.user?.lastName shouldBe "Doe"
            user1?.user?.email shouldBe "john.doe@example.com"
            user1?.groups?.size shouldBe 2
            user1?.groups shouldContainExactly listOf(ApiUserGroup.ADMINS, ApiUserGroup.WRITERS)

            val user2 = apiUsers.find { it.user.username == "ron5ff" }
            user2 shouldNotBe null
            user2?.user?.firstName shouldBe "Ron"
            user2?.user?.lastName shouldBe "Boo"
            user2?.user?.email shouldBe "ron.boo@example.com"
            user2?.groups?.size shouldBe 3
            user2?.groups shouldContainExactly listOf(ApiUserGroup.ADMINS, ApiUserGroup.WRITERS, ApiUserGroup.READERS)
        }

        "sort groups by rank" {
            // Given
            val modelUsers = mapOf(
                Pair(
                    ModelUser("john2hh", "John", "Doe", "j.doe@example.com"),
                    setOf(ModelUserGroup.WRITERS, ModelUserGroup.ADMINS, ModelUserGroup.READERS)
                )
            )

            // When
            val apiUsers = modelUsers.mapToApi()

            // Then
            apiUsers.first().groups[0] shouldBe ApiUserGroup.ADMINS
            apiUsers.first().groups[1] shouldBe ApiUserGroup.WRITERS
            apiUsers.first().groups[2] shouldBe ApiUserGroup.READERS
        }
    }
})
