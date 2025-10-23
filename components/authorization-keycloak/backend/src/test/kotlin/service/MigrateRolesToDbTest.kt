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

package org.eclipse.apoapsis.ortserver.components.authorization.keycloak.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import io.mockk.coEvery
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.User
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.components.authorization.db.RoleAssignmentsTable
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.Superuser
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole as DbOrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole as DbProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole as DbRepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService as DbAuthorizationService
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

import org.jetbrains.exposed.sql.insert

class MigrateRolesToDbTest : StringSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    /**
     * Create a test instance of [KeycloakAuthorizationService] with the given dependencies.
     */
    private fun createService(
        keycloakClient: KeycloakClient,
        dbAuthorizationService: DbAuthorizationService
    ): KeycloakAuthorizationService =
        KeycloakAuthorizationService(
            keycloakClient = keycloakClient,
            db = dbExtension.db,
            organizationRepository = mockk(),
            productRepository = mockk(),
            repositoryRepository = mockk(),
            keycloakGroupPrefix = GROUP_PREFIX,
            dbAuthorizationService = dbAuthorizationService
        )

    init {
        "role assignments for all hierarchy elements should be migrated correctly" {
            val orgAdminUser = "OrgAdmin"
            val productAdminUser = "ProductAdmin"
            val user1 = "test-user1"
            val user2 = "test-user2"
            val user3 = "test-user3"
            val repositoryId = CompoundHierarchyId.forRepository(
                OrganizationId(dbExtension.fixtures.organization.id),
                ProductId(dbExtension.fixtures.product.id),
                RepositoryId(dbExtension.fixtures.repository.id)
            )
            val productId = repositoryId.parent!!
            val organizationId = productId.parent!!
            val repository2 = dbExtension.fixtures.createRepository(url = "https://example.com/repo2.git")
            val repository2Id = CompoundHierarchyId.forRepository(
                OrganizationId(dbExtension.fixtures.organization.id),
                ProductId(dbExtension.fixtures.product.id),
                RepositoryId(repository2.id)
            )
            val organization2 = dbExtension.fixtures.createOrganization("Org2")
            val organization2Id = CompoundHierarchyId.forOrganization(OrganizationId(organization2.id))
            val product2 = dbExtension.fixtures.createProduct("Product2", organizationId = organization2.id)
            val product2Id = CompoundHierarchyId.forProduct(
                OrganizationId(organization2.id),
                ProductId(product2.id)
            )

            val groupsWithUsers = mapOf(
                OrganizationRole.ADMIN.groupName(dbExtension.fixtures.organization.id) to listOf(orgAdminUser),
                OrganizationRole.WRITER.groupName(dbExtension.fixtures.organization.id) to listOf(
                    productAdminUser,
                    user1
                ),
                OrganizationRole.READER.groupName(dbExtension.fixtures.organization.id) to listOf(user2),
                ProductRole.ADMIN.groupName(dbExtension.fixtures.product.id) to listOf(productAdminUser),
                ProductRole.WRITER.groupName(dbExtension.fixtures.product.id) to listOf(user1),
                ProductRole.READER.groupName(dbExtension.fixtures.product.id) to listOf(user2),
                RepositoryRole.ADMIN.groupName(dbExtension.fixtures.repository.id) to emptyList(),
                RepositoryRole.WRITER.groupName(dbExtension.fixtures.repository.id) to listOf(
                    user1,
                    user2
                ),
                RepositoryRole.READER.groupName(dbExtension.fixtures.repository.id) to emptyList(),
                RepositoryRole.ADMIN.groupName(repository2.id) to emptyList(),
                RepositoryRole.WRITER.groupName(repository2.id) to emptyList(),
                RepositoryRole.READER.groupName(repository2.id) to listOf(user1, user2),
                OrganizationRole.ADMIN.groupName(organization2.id) to listOf(orgAdminUser),
                OrganizationRole.WRITER.groupName(organization2.id) to emptyList(),
                OrganizationRole.READER.groupName(organization2.id) to listOf(
                    user3,
                    productAdminUser
                ),
                ProductRole.ADMIN.groupName(product2.id) to emptyList(),
                ProductRole.WRITER.groupName(product2.id) to listOf(user3),
                ProductRole.READER.groupName(product2.id) to emptyList(),

                Superuser.GROUP_NAME to emptyList()
            )

            val expectedAssignments = listOf(
                RoleAssignment(
                    userId = orgAdminUser,
                    role = DbOrganizationRole.ADMIN,
                    hierarchyId = organizationId
                ),
                RoleAssignment(
                    userId = productAdminUser,
                    role = DbOrganizationRole.WRITER,
                    hierarchyId = organizationId
                ),
                RoleAssignment(
                    userId = user1,
                    role = DbOrganizationRole.WRITER,
                    hierarchyId = organizationId
                ),
                RoleAssignment(
                    userId = user2,
                    role = DbOrganizationRole.READER,
                    hierarchyId = organizationId
                ),
                RoleAssignment(
                    userId = productAdminUser,
                    role = DbProductRole.ADMIN,
                    hierarchyId = productId
                ),
                RoleAssignment(
                    userId = user1,
                    role = DbProductRole.WRITER,
                    hierarchyId = productId
                ),
                RoleAssignment(
                    userId = user2,
                    role = DbProductRole.READER,
                    hierarchyId = productId
                ),
                RoleAssignment(
                    userId = user1,
                    role = DbRepositoryRole.WRITER,
                    hierarchyId = repositoryId
                ),
                RoleAssignment(
                    userId = user2,
                    role = DbRepositoryRole.WRITER,
                    hierarchyId = repositoryId
                ),
                RoleAssignment(
                    userId = user1,
                    role = DbRepositoryRole.READER,
                    hierarchyId = repository2Id
                ),
                RoleAssignment(
                    userId = user2,
                    role = DbRepositoryRole.READER,
                    hierarchyId = repository2Id
                ),
                RoleAssignment(
                    userId = orgAdminUser,
                    role = DbOrganizationRole.ADMIN,
                    hierarchyId = organization2Id
                ),
                RoleAssignment(
                    userId = productAdminUser,
                    role = DbOrganizationRole.READER,
                    hierarchyId = organization2Id
                ),
                RoleAssignment(
                    userId = user3,
                    role = DbOrganizationRole.READER,
                    hierarchyId = organization2Id
                ),
                RoleAssignment(
                    userId = user3,
                    role = DbProductRole.WRITER,
                    hierarchyId = product2Id
                )
            )

            val keycloakClient = createKeycloakClientForGroups(groupsWithUsers)
            val assignments = mutableListOf<RoleAssignment>()
            val dbAuthorizationService = createDbAuthorizationServiceMock(assignments)

            val authorizationService = createService(
                keycloakClient = keycloakClient,
                dbAuthorizationService = dbAuthorizationService
            )
            authorizationService.migrateRolesToDb() shouldBe true

            assignments shouldContainExactlyInAnyOrder expectedAssignments
        }

        "exceptions when querying Keycloak group members should be ignored" {
            val user = "some-user"
            val groupsWithUsers = mapOf(
                OrganizationRole.WRITER.groupName(dbExtension.fixtures.organization.id) to listOf(user),
            )

            val keycloakClient = createKeycloakClientForGroups(groupsWithUsers)
            val assignments = mutableListOf<RoleAssignment>()
            val dbAuthorizationService = createDbAuthorizationServiceMock(assignments)

            val authorizationService = createService(
                keycloakClient = keycloakClient,
                dbAuthorizationService = dbAuthorizationService
            )
            authorizationService.migrateRolesToDb() shouldBe true

            assignments shouldHaveSize 1
        }

        "superuser role assignments should be migrated correctly" {
            val superuser1 = "SuperMan"
            val superuser2 = "BatMan"

            val groupsWithUsers = mapOf(
                Superuser.GROUP_NAME to listOf(superuser1, superuser2)
            )

            val expectedAssignments = listOf(
                RoleAssignment(
                    userId = superuser1,
                    role = DbOrganizationRole.ADMIN,
                    hierarchyId = CompoundHierarchyId.WILDCARD
                ),
                RoleAssignment(
                    userId = superuser2,
                    role = DbOrganizationRole.ADMIN,
                    hierarchyId = CompoundHierarchyId.WILDCARD
                )
            )

            val keycloakClient = createKeycloakClientForGroups(groupsWithUsers)
            val assignments = mutableListOf<RoleAssignment>()
            val dbAuthorizationService = createDbAuthorizationServiceMock(assignments)

            val authorizationService = createService(
                keycloakClient = keycloakClient,
                dbAuthorizationService = dbAuthorizationService
            )
            authorizationService.migrateRolesToDb() shouldBe true

            assignments shouldContainExactlyInAnyOrder expectedAssignments
        }

        "migration should be skipped if the DB already contains role assignments" {
            dbExtension.fixtures.repository.id // This forces the creation of hierarchy elements.

            dbExtension.db.dbQuery {
                RoleAssignmentsTable.insert {
                    it[userId] = "some-user-id"
                    it[organizationRole] = "READER"
                    it[organizationId] = dbExtension.fixtures.organization.id
                }
            }

            val keycloakClient = createKeycloakClientForGroups(emptyMap())
            val assignments = mutableListOf<RoleAssignment>()
            val dbAuthorizationService = createDbAuthorizationServiceMock(assignments)

            val authorizationService = createService(
                keycloakClient = keycloakClient,
                dbAuthorizationService = dbAuthorizationService
            )
            authorizationService.migrateRolesToDb() shouldBe false

            assignments should beEmpty()
        }
    }
}

/** The prefix for group names in Keycloak. */
private const val GROUP_PREFIX = "namespace_"

/**
 * Create a mock [KeycloakClient] that is prepared to answer requests for the members of groups. The known groups
 * and their users are provided by the given [groupsWithUsers].
 */
private fun createKeycloakClientForGroups(groupsWithUsers: Map<String, List<String>>): KeycloakClient =
    mockk<KeycloakClient> {
    coEvery { getGroupMembers(any<GroupName>()) } answers {
        val groupName = firstArg<String>()
        groupName shouldStartWith GROUP_PREFIX
        groupsWithUsers[groupName.removePrefix(GROUP_PREFIX)]?.mapTo(mutableSetOf()) { userId ->
            User(
                id = UserId("$userId-id"),
                username = UserName(userId)
            )
        } ?: throw IllegalArgumentException("Unknown group: $groupName")
    }
}

/**
 * A data class to store the properties of a role assignment requested on the authorization service.
 */
private data class RoleAssignment(
    /** The subject user ID. */
    val userId: String,

    /** The role that is assigned. */
    val role: Role,

    /** The ID of the hierarchy element. */
    val hierarchyId: CompoundHierarchyId
)

/**
 * Create a mock authorization service that is prepared to store all assignments passed to it in the given
 * [assignments] list.
 */
private fun createDbAuthorizationServiceMock(assignments: MutableList<RoleAssignment>): DbAuthorizationService =
    mockk<DbAuthorizationService> {
        coEvery { assignRole(any(), any(), any()) } answers {
            val assignment = RoleAssignment(
                userId = firstArg(),
                role = secondArg(),
                hierarchyId = thirdArg()
            )
            assignments += assignment
        }
    }
