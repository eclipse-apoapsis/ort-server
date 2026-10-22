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
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.api.v1.model.User as ApiUser
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithOrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.api.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole as ModelProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RoleInfo
import org.eclipse.apoapsis.ortserver.components.authorization.service.UserService
import org.eclipse.apoapsis.ortserver.core.api.UserWithRoleHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithRoleHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.User as ModelUser
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingOptions
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

class UserWithRoleHelperTest : WordSpec({
    "sortAndPage" should {
        "be paged" {
            // Given
            val users = listOf(
                userWithRole("john1hh", OrganizationRole.ADMIN),
                userWithRole("john2hh", OrganizationRole.ADMIN),
                userWithRole("john3hh", OrganizationRole.ADMIN),
                userWithRole("john4hh", OrganizationRole.ADMIN),
                userWithRole("john5hh", OrganizationRole.ADMIN),
                userWithRole("john6hh", OrganizationRole.ADMIN)
            )

            val pagingOptions = PagingOptions(2, 2, listOf(SortProperty("username", SortDirection.ASCENDING)))

            // When
            val pagedUsers = users.sortAndPage(pagingOptions)

            // Then
            pagedUsers shouldHaveSize 2
            pagedUsers[0].user.username shouldBe "john3hh"
            pagedUsers[1].user.username shouldBe "john4hh"
        }

        "sort list by given user field" {
            // Given
            val users = listOf(
                userWithRole("john6hh", OrganizationRole.ADMIN),
                userWithRole("john2hh", OrganizationRole.ADMIN),
                userWithRole("john1hh", OrganizationRole.ADMIN),
                userWithRole("john5hh", OrganizationRole.ADMIN),
                userWithRole("john4hh", OrganizationRole.ADMIN),
                userWithRole("john3hh", OrganizationRole.ADMIN)
            )

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("username", SortDirection.ASCENDING)))

            // When
            val pagedUsers = users.sortAndPage(pagingOptions)

            // Then
            pagedUsers.map { it.user.username } shouldBe
                listOf("john1hh", "john2hh", "john3hh", "john4hh", "john5hh", "john6hh")
        }

        "sort list by role field" {
            // Given
            val users = listOf(
                userWithRole("john6hh", OrganizationRole.ADMIN),
                userWithRole("john2hh", OrganizationRole.WRITER),
                userWithRole("john5hh", OrganizationRole.READER)
            )

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("role", SortDirection.ASCENDING)))

            // When
            val pagedUsers = users.sortAndPage(pagingOptions)

            // Then
            pagedUsers.map { it.role } shouldBe
                listOf(OrganizationRole.READER, OrganizationRole.WRITER, OrganizationRole.ADMIN)
        }

        "sort list by role field in descending order" {
            // Given
            val users = listOf(
                userWithRole("john5hh", OrganizationRole.READER),
                userWithRole("john6hh", OrganizationRole.ADMIN),
                userWithRole("john2hh", OrganizationRole.WRITER)
            )

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("role", SortDirection.DESCENDING)))

            // When
            val pagedUsers = users.sortAndPage(pagingOptions)

            // Then
            pagedUsers.map { it.role } shouldBe
                listOf(OrganizationRole.ADMIN, OrganizationRole.WRITER, OrganizationRole.READER)
        }

        "throw an exception if sort field is empty" {
            // Given
            val users = listOf(userWithRole("john6hh", OrganizationRole.ADMIN))

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("", SortDirection.ASCENDING)))

            // Then
            val exception = shouldThrow<QueryParametersException> { users.sortAndPage(pagingOptions) }
            exception.message shouldBe "Empty sort field."
        }

        "throw an exception if sort field has wrong name" {
            // Given
            val users = listOf(userWithRole("john6hh", OrganizationRole.ADMIN))

            val pagingOptions = PagingOptions(999, 0, listOf(SortProperty("blobby", SortDirection.ASCENDING)))

            // Then
            val exception = shouldThrow<QueryParametersException> { users.sortAndPage(pagingOptions) }
            exception.message shouldBe "Unknown sort field 'blobby'."
        }

        "throw an exception if number of sort fields is higher than 1" {
            // Given
            val users = listOf(userWithRole("john6hh", OrganizationRole.ADMIN))

            val pagingOptions = PagingOptions(
                999,
                0,
                listOf(
                    SortProperty("role", SortDirection.ASCENDING),
                    SortProperty("username", SortDirection.ASCENDING)
                )
            )

            // Then
            val exception = shouldThrow<QueryParametersException> { users.sortAndPage(pagingOptions) }
            exception.message shouldBe "Exactly one sort field must be defined."
        }
    }

    "mapToApi" should {
        "build the items from the users and roles that pass the filter" {
            // Given
            val organizationId = CompoundHierarchyId.forOrganization(OrganizationId(1))
            val productId = CompoundHierarchyId.forProduct(OrganizationId(1), ProductId(2))
            val roles = mapOf(
                "john2hh" to RoleInfo(ModelProductRole.ADMIN, organizationId),
                "ron5ff" to RoleInfo(ModelProductRole.READER, productId),
                "jane3gg" to RoleInfo(ModelProductRole.WRITER, productId)
            )

            val userService = mockk<UserService> {
                coEvery { getUsersById(roles.keys) } returns setOf(
                    ModelUser("john2hh", "John", "Doe", "john.doe@example.com"),
                    ModelUser("ron5ff", "Ron", "Boo", "ron.boo@example.com"),
                    ModelUser("jane3gg", "Jane", "Roe", "jane.roe@example.com")
                )
            }

            // When
            val apiUsers = roles.mapToApi(
                userService,
                roleFilter = { it.assignedAt.level == HierarchyLevel.PRODUCT }
            ) { user, role -> user to role }

            // Then
            apiUsers should containExactlyInAnyOrder(
                ApiUser("ron5ff", "Ron", "Boo", "ron.boo@example.com") to ModelProductRole.READER,
                ApiUser("jane3gg", "Jane", "Roe", "jane.roe@example.com") to ModelProductRole.WRITER
            )
        }
    }
})

private fun userWithRole(username: String, role: OrganizationRole) =
    UserWithOrganizationRole(ApiUser(username, "John", "Doe", "$username@example.com"), role)

private fun List<UserWithOrganizationRole>.sortAndPage(paging: PagingOptions) =
    sortAndPage(paging, { it.user }, { it.role })
