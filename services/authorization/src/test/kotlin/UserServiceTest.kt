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

package org.eclipse.apoapsis.ortserver.services

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.clients.keycloak.Group
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupId
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.User
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.model.UserGroup

class UserServiceTest : WordSpec({
    val keycloakClient = mockk<KeycloakClient>()
    val service = UserService(keycloakClient)

    fun mockGroupsListForEntity(entityName: String, entityId: Long) {
        coEvery {
            keycloakClient.getGroupMembers(GroupId("gr1-wri"))
        } returns setOf(
            User(UserId("usr-1"), UserName("user1"), "Boo", "Zoo", "boo.zoo@example.com"),
            User(UserId("usr-2"), UserName("user2"), "Woo", "Goo", "woo.goo@example.com"),
            User(UserId("usr-3"), UserName("user3"), "Wee", "Moo", "wee.moo@example.com")
        )

        coEvery {
            keycloakClient.getGroupMembers(GroupId("gr2-adm"))
        } returns setOf(
            User(UserId("usr-1"), UserName("user1"), "Boo", "Zoo", "boo.zoo@example.com")
        )

        coEvery {
            keycloakClient.getGroupMembers(GroupId("gr3-rea"))
        } returns emptySet()

        coEvery {
            keycloakClient.searchGroups(GroupName("${entityName}_${entityId}_"))
        } returns setOf(
            Group(GroupId("gr1-wri"), GroupName("${entityName}_${entityId}_WRITERS")),
            Group(GroupId("gr2-adm"), GroupName("${entityName}_${entityId}_ADMINS")),
            Group(GroupId("gr3-rea"), GroupName("${entityName}_${entityId}_READERS"))
        )
    }

    "getUsersForOrganization" should {
        "return empty list when no users are found for organization" {
            // Given
            val orgId = 7L

            coEvery {
                keycloakClient.searchGroups(GroupName("ORGANIZATION_${orgId}_"))
            } returns emptySet()

            // When
            val result = service.getUsersHavingRightsForOrganization(orgId)

            // Then
            result shouldBe emptyMap()
        }

        "return map of users with corresponding set of roles within organization" {
            // Given
            val orgId = 7L
            mockGroupsListForEntity("ORGANIZATION", orgId)

            // When
            val result = service.getUsersHavingRightsForOrganization(orgId)

            // Then
            result.size shouldBe 3
            result.keys.map { it.username } shouldBe listOf("user1", "user2", "user3")
            result[result.keys.find { it.username == "user1" }] shouldBe setOf(UserGroup.WRITERS, UserGroup.ADMINS)
            result[result.keys.find { it.username == "user2" }] shouldBe setOf(UserGroup.WRITERS)
            result[result.keys.find { it.username == "user3" }] shouldBe setOf(UserGroup.WRITERS)
        }
    }

    "getUsersForProduct" should {
        "return empty list when no users are found for product" {
            // Given
            val prodId = 4L

            coEvery {
                keycloakClient.searchGroups(GroupName("PRODUCT_${prodId}_"))
            } returns emptySet()

            // When
            val result = service.getUsersHavingRightForProduct(prodId)

            // Then
            result shouldBe emptyMap()
        }

        "return map of users with corresponding set of roles within organization" {
            // Given
            val prodId = 4L
            mockGroupsListForEntity("PRODUCT", prodId)

            // When
            val result = service.getUsersHavingRightForProduct(prodId)

            // Then
            result.size shouldBe 3
            result.keys.map { it.username } shouldBe listOf("user1", "user2", "user3")
            result[result.keys.find { it.username == "user1" }] shouldBe setOf(UserGroup.WRITERS, UserGroup.ADMINS)
            result[result.keys.find { it.username == "user2" }] shouldBe setOf(UserGroup.WRITERS)
            result[result.keys.find { it.username == "user3" }] shouldBe setOf(UserGroup.WRITERS)
        }
    }

    "getUsersForRepository" should {
        "return empty list when no users are found for repository" {
            // Given
            val repoId = 2L

            coEvery {
                keycloakClient.searchGroups(GroupName("REPOSITORY_${repoId}_"))
            } returns emptySet()

            // When
            val result = service.getUsersHavingRightsForRepository(repoId)

            // Then
            result shouldBe emptyMap()
        }

        "return map of users with corresponding set of roles within repository" {
            // Given
            val prodId = 4L
            mockGroupsListForEntity("REPOSITORY", prodId)

            // When
            val result = service.getUsersHavingRightsForRepository(prodId)

            // Then
            result.size shouldBe 3
            result.keys.map { it.username } shouldBe listOf("user1", "user2", "user3")
            result[result.keys.find { it.username == "user1" }] shouldBe setOf(UserGroup.ADMINS, UserGroup.WRITERS)
            result[result.keys.find { it.username == "user2" }] shouldBe setOf(UserGroup.WRITERS)
            result[result.keys.find { it.username == "user3" }] shouldBe setOf(UserGroup.WRITERS)
        }
    }
})
