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

package org.eclipse.apoapsis.ortserver.components.authorization.keycloak.migration

import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.components.authorization.db.RoleAssignmentsTable
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.Role
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.Superuser
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole as DbOrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole as DbProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole as DbRepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role as DbRole
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(RolesToDbMigration::class.java)

/**
 * A class implementing a migration that moves roles management to the database.
 *
 * The migration iterates over the existing organizations, products, and repositories and the associating groups in
 * Keycloak that represent the user roles on these entities. It then creates corresponding role assignment entries in
 * the database. The migration needs to be executed once to set up the data structures for the new authorization
 * component.
 */
class RolesToDbMigration(
    private val keycloakClient: KeycloakClient,
    private val db: Database,

    /**
     * A prefix for Keycloak group names, to be used when multiple instances of ORT Server share the same Keycloak
     * realm.
     */
    private val keycloakGroupPrefix: String,

    /**
     * The reworked authorization service that stores authorization data in the database. This is used for the
     * migration functionality.
     */
    private val authorizationService: AuthorizationService

) {
    /**
     * Perform a one-time migration of roles stored in Keycloak to the database. The migration happens if and only if
     * the table with role assignments is empty. It is then populated with data corresponding to the current set of
     * groups existing in Keycloak. The return value indicates whether a migration was performed.
     */
    suspend fun migrateRolesToDb(): Boolean {
        if (!canMigrate()) return false

        logger.warn(
            "Starting migration of Keycloak roles to database-based roles using group prefix '{}'.",
            keycloakGroupPrefix
        )

        val organizationIds = db.dbQuery {
            OrganizationsTable.select(OrganizationsTable.id)
                .map { it[OrganizationsTable.id].value }
        }

        logger.info("Migrating {} organizations.", organizationIds.size)
        organizationIds.forEach { organizationId ->
            migrateOrganizationRolesToDb(organizationId)
        }

        logger.info("Migrating superusers")
        migrateUsersInGroupToDb(
            GroupName(keycloakGroupPrefix + Superuser.GROUP_NAME),
            DbOrganizationRole.ADMIN,
            CompoundHierarchyId.WILDCARD
        )

        return true
    }

    /**
     * Migrate the access rights for the organization with the given [organizationId] to the new database-based roles.
     * This includes the migration of all products and repositories belonging to the organization.
     */
    private suspend fun migrateOrganizationRolesToDb(organizationId: Long) {
        logger.info("Migrating roles for organization '{}'.", organizationId)
        val organizationHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(organizationId))

        migrateElementRolesToDb(
            oldRoles = OrganizationRole.entries,
            newRoles = DbOrganizationRole.entries,
            id = OrganizationId(organizationId),
            newHierarchyID = organizationHierarchyId
        )

        val productIds = db.dbQuery {
            ProductsTable.select(ProductsTable.id)
                .where { ProductsTable.organizationId eq organizationId }
                .map { it[ProductsTable.id].value }
        }

        logger.info("Migrating {} products for organization '{}'.", productIds.size, organizationId)
        productIds.forEach { productId ->
            val productHierarchyId = CompoundHierarchyId.forProduct(
                OrganizationId(organizationId),
                ProductId(productId)
            )
            migrateProductRolesToDb(productHierarchyId)
        }
    }

    /**
     * Migrate the access rights for the product with the given [productHierarchyId] to the new database-based roles.
     * This includes the migration of all repositories belonging to the product.
     */
    private suspend fun migrateProductRolesToDb(productHierarchyId: CompoundHierarchyId) {
        val productId = requireNotNull(productHierarchyId.productId)
        logger.info("Migrating roles for product '{}'.", productId)

        migrateElementRolesToDb(
            oldRoles = ProductRole.entries,
            newRoles = DbProductRole.entries,
            id = productId,
            productHierarchyId
        )

        val repositoryIds = db.dbQuery {
            RepositoriesTable.select(RepositoriesTable.id)
                .where { RepositoriesTable.productId eq productId.value }
                .map { it[RepositoriesTable.id].value }
        }

        logger.info("Migrating {} repositories for product '{}'.", repositoryIds.size, productId)
        repositoryIds.forEach { repositoryId ->
            val repositoryHierarchyId = CompoundHierarchyId.forRepository(
                requireNotNull(productHierarchyId.organizationId),
                productId,
                RepositoryId(repositoryId)
            )
            migrateRepositoryRolesToDb(repositoryHierarchyId)
        }
    }

    /**
     * Migrate the access rights for the repository with the given [repositoryId] to the new database-based roles.
     */
    private suspend fun migrateRepositoryRolesToDb(repositoryId: CompoundHierarchyId) {
        logger.info("Migrating roles for repository '{}'.", repositoryId.repositoryId)

        migrateElementRolesToDb(
            oldRoles = RepositoryRole.entries,
            newRoles = DbRepositoryRole.entries,
            id = requireNotNull(repositoryId.repositoryId),
            repositoryId
        )
    }

    /**
     * Migrate all users in the given [oldRoles] to the corresponding [newRoles] for the hierarchy element with the
     * given [id] and [newHierarchyID].
     */
    private suspend fun <ID : HierarchyId> migrateElementRolesToDb(
        oldRoles: Collection<Role<*, ID>>,
        newRoles: Collection<DbRole>,
        id: ID,
        newHierarchyID: CompoundHierarchyId
    ) {
        oldRoles.zip(newRoles).forEach { (oldRole, newRole) ->
            migrateUsersForRoleToDb(oldRole, newRole, id, newHierarchyID)
        }
    }

    /**
     * Migrate all users assigned to the given [oldRole] for the hierarchy element with the given [id] to the new
     * [newRole] in the database, using the provided [newHierarchyID].
     */
    private suspend fun <ID : HierarchyId> migrateUsersForRoleToDb(
        oldRole: Role<*, ID>,
        newRole: DbRole,
        id: ID,
        newHierarchyID: CompoundHierarchyId
    ) {
        val groupName = GroupName(keycloakGroupPrefix + oldRole.groupName(id))
        migrateUsersInGroupToDb(groupName, newRole, newHierarchyID)
    }

    /**
     * Migrate all users in the Keycloak group with the given [groupName] (which represents a role) to the given
     * [newRole] for the hierarchy element with the given [newHierarchyID].
     */
    private suspend fun migrateUsersInGroupToDb(
        groupName: GroupName,
        newRole: DbRole,
        newHierarchyID: CompoundHierarchyId
    ) {
        runCatching {
            keycloakClient.getGroupMembers(groupName).forEach { user ->
                authorizationService.assignRole(
                    userId = user.username.value,
                    role = newRole,
                    compoundHierarchyId = newHierarchyID
                )
            }
        }.onFailure { exception ->
            logger.error("Failed to load users in group '${groupName.value}' during migration.", exception)
        }
    }

    /**
     * Return a flag whether the migration of access rights to the new database structures is possible. This is the
     * case if the table for role assignments is empty. This should ensure that the migration is only performed once.
     */
    private suspend fun canMigrate(): Boolean = db.dbQuery {
        RoleAssignmentsTable.select(RoleAssignmentsTable.id).empty()
    }
}
