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
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.should

import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.User
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.KeycloakTestClient
import org.eclipse.apoapsis.ortserver.components.authorization.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.model.User as ModelUser
import org.eclipse.apoapsis.ortserver.model.UserGroup

class UserServiceTest : WordSpec({
    lateinit var keycloakClient: KeycloakClient
    lateinit var service: UserService
    lateinit var user1: User
    lateinit var user2: User
    lateinit var user3: User

    val keycloakGroupPrefix = "PREFIX_"

    beforeEach {
        keycloakClient = KeycloakTestClient()
        service = UserService(keycloakClient, keycloakGroupPrefix)

        keycloakClient.createUser(UserName("user1"))
        keycloakClient.createUser(UserName("user2"))
        keycloakClient.createUser(UserName("user3"))

        user1 = keycloakClient.getUser(UserName("user1"))
        user2 = keycloakClient.getUser(UserName("user2"))
        user3 = keycloakClient.getUser(UserName("user3"))
    }

    "getOrganizationUsers" should {
        val orgId = 1L

        "return empty list when no users are found for organization" {
            service.getUsersHavingRightsForOrganization(orgId) should beEmpty()
        }

        "return map of users with corresponding set of roles within organization" {
            val adminGroupName = GroupName("${keycloakGroupPrefix}${OrganizationRole.ADMIN.groupName(orgId)}")
            val writerGroupName = GroupName("${keycloakGroupPrefix}${OrganizationRole.WRITER.groupName(orgId)}")
            val readerGroupName = GroupName("${keycloakGroupPrefix}${OrganizationRole.READER.groupName(orgId)}")

            keycloakClient.createGroup(adminGroupName)
            keycloakClient.createGroup(writerGroupName)
            keycloakClient.createGroup(readerGroupName)

            keycloakClient.addUserToGroup(user1.username, adminGroupName)
            keycloakClient.addUserToGroup(user1.username, readerGroupName)
            keycloakClient.addUserToGroup(user2.username, writerGroupName)
            keycloakClient.addUserToGroup(user3.username, readerGroupName)

            service.getUsersHavingRightsForOrganization(orgId) should containExactly(
                ModelUser(user1.username.value) to setOf(UserGroup.ADMINS, UserGroup.READERS),
                ModelUser(user2.username.value) to setOf(UserGroup.WRITERS),
                ModelUser(user3.username.value) to setOf(UserGroup.READERS)
            )
        }
    }

    "getProductUsers" should {
        val prodId = 1L

        "return empty list when no users are found for product" {
            service.getUsersHavingRightForProduct(prodId) should beEmpty()
        }

        "return map of users with corresponding set of roles within product" {
            val adminGroupName = GroupName("${keycloakGroupPrefix}${ProductRole.ADMIN.groupName(prodId)}")
            val writerGroupName = GroupName("${keycloakGroupPrefix}${ProductRole.WRITER.groupName(prodId)}")
            val readerGroupName = GroupName("${keycloakGroupPrefix}${ProductRole.READER.groupName(prodId)}")

            keycloakClient.createGroup(adminGroupName)
            keycloakClient.createGroup(writerGroupName)
            keycloakClient.createGroup(readerGroupName)

            keycloakClient.addUserToGroup(user1.username, adminGroupName)
            keycloakClient.addUserToGroup(user1.username, readerGroupName)
            keycloakClient.addUserToGroup(user2.username, writerGroupName)
            keycloakClient.addUserToGroup(user3.username, readerGroupName)

            service.getUsersHavingRightForProduct(prodId) shouldContainExactly mapOf(
                ModelUser(user1.username.value) to setOf(UserGroup.ADMINS, UserGroup.READERS),
                ModelUser(user2.username.value) to setOf(UserGroup.WRITERS),
                ModelUser(user3.username.value) to setOf(UserGroup.READERS)
            )
        }
    }

    "getRepositoryUsers" should {
        val repoId = 1L

        "return empty list when no users are found for repository" {
            service.getUsersHavingRightsForRepository(repoId) should beEmpty()
        }

        "return map of users with corresponding set of roles within repository" {
            val adminGroupName = GroupName("${keycloakGroupPrefix}${RepositoryRole.ADMIN.groupName(repoId)}")
            val writerGroupName = GroupName("${keycloakGroupPrefix}${RepositoryRole.WRITER.groupName(repoId)}")
            val readerGroupName = GroupName("${keycloakGroupPrefix}${RepositoryRole.READER.groupName(repoId)}")

            keycloakClient.createGroup(adminGroupName)
            keycloakClient.createGroup(writerGroupName)
            keycloakClient.createGroup(readerGroupName)

            keycloakClient.addUserToGroup(user1.username, adminGroupName)
            keycloakClient.addUserToGroup(user1.username, readerGroupName)
            keycloakClient.addUserToGroup(user2.username, writerGroupName)
            keycloakClient.addUserToGroup(user3.username, readerGroupName)

            service.getUsersHavingRightsForRepository(repoId) shouldContainExactly mapOf(
                ModelUser(user1.username.value) to setOf(UserGroup.ADMINS, UserGroup.READERS),
                ModelUser(user2.username.value) to setOf(UserGroup.WRITERS),
                ModelUser(user3.username.value) to setOf(UserGroup.READERS)
            )
        }
    }
})
