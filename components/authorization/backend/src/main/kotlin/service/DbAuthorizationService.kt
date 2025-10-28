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

import org.eclipse.apoapsis.ortserver.components.authorization.db.RoleAssignmentsTable
import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.HierarchyPermissions
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.PermissionChecker
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DbAuthorizationService::class.java)

/**
 * An implementation of [AuthorizationService] storing information about role assignments in the database.
 *
 * Note that this implementation assumes that user IDs are managed in an external system. It does not interpret these
 * values, but just stores and retrieves them as provided.
 */
class DbAuthorizationService(
    /** The database to use. */
    private val db: Database
) : AuthorizationService {
    override suspend fun checkPermissions(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId,
        checker: PermissionChecker
    ): EffectiveRole? {
        val roleAssignments = loadAssignments(userId, compoundHierarchyId)

        val permissions = HierarchyPermissions.create(roleAssignments, checker)
        return if (permissions.hasPermission(compoundHierarchyId)) {
            EffectiveRoleImpl(
                elementId = compoundHierarchyId,
                isSuperuser = permissions.isSuperuser(),
                permissions = checker
            )
        } else {
            null
        }
    }

    override suspend fun checkPermissions(
        userId: String,
        hierarchyId: HierarchyId,
        checker: PermissionChecker
    ): EffectiveRole? {
        val compoundHierarchyId = resolveCompoundId(hierarchyId)

        return compoundHierarchyId.takeUnless { it.isInvalid() }?.let {
            checkPermissions(userId, it, checker)
        }
    }

    override suspend fun getEffectiveRole(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId
    ): EffectiveRole {
        val roleAssignments = loadAssignments(userId, compoundHierarchyId)
        val roles = rolesForLevel(compoundHierarchyId).reversed().asSequence()

        // Check for all roles on the current level, starting with the ADMIN role, whether all its permissions are
        // granted. This is then the effective role. This assumes that constants in the enums for roles are ordered
        // by the number of permissions they grant in ascending order. If this changes, there should be failing tests.
        return roles.mapNotNull { role ->
            val permissionChecker = HierarchyPermissions.permissions(role)
            HierarchyPermissions.create(roleAssignments, permissionChecker)
                .takeIf { it.hasPermission(compoundHierarchyId) }?.let {
                    EffectiveRoleImpl(
                        elementId = compoundHierarchyId,
                        isSuperuser = it.isSuperuser(),
                        permissions = permissionChecker
                    )
                }
        }.firstOrNull() ?: EffectiveRoleImpl(
            elementId = compoundHierarchyId,
            isSuperuser = false,
            permissions = PermissionChecker()
        )
    }

    override suspend fun getEffectiveRole(
        userId: String,
        hierarchyId: HierarchyId
    ): EffectiveRole {
        val compoundHierarchyId = resolveCompoundId(hierarchyId)

        return if (compoundHierarchyId.isInvalid()) {
            logger.warn("Failed to resolve hierarchy ID $hierarchyId.")

            EffectiveRoleImpl(
                elementId = compoundHierarchyId,
                isSuperuser = false,
                permissions = PermissionChecker()
            )
        } else {
            getEffectiveRole(userId, compoundHierarchyId)
        }
    }

    override suspend fun assignRole(
        userId: String,
        role: Role,
        compoundHierarchyId: CompoundHierarchyId
    ) {
        db.dbQuery {
            doRemoveAssignment(userId, compoundHierarchyId)

            logger.info(
                "Assigning role '{}' to user '{}' on hierarchy element {}.",
                role,
                userId,
                compoundHierarchyId
            )

            RoleAssignmentsTable.insert {
                it[RoleAssignmentsTable.userId] = userId
                it[RoleAssignmentsTable.organizationId] = compoundHierarchyId.organizationId?.value
                it[RoleAssignmentsTable.productId] = compoundHierarchyId.productId?.value
                it[RoleAssignmentsTable.repositoryId] = compoundHierarchyId.repositoryId?.value
                it[RoleAssignmentsTable.organizationRole] = (role as? OrganizationRole)?.name
                it[RoleAssignmentsTable.productRole] = (role as? ProductRole)?.name
                it[RoleAssignmentsTable.repositoryRole] = (role as? RepositoryRole)?.name
            }
        }
    }

    override suspend fun removeAssignment(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId
    ): Boolean = db.dbQuery {
        doRemoveAssignment(userId, compoundHierarchyId)
    }

    override suspend fun listUsersWithRole(
        role: Role,
        compoundHierarchyId: CompoundHierarchyId
    ): Set<String> = db.dbQuery {
        RoleAssignmentsTable.select(RoleAssignmentsTable.userId)
            .where {
                (RoleAssignmentsTable.organizationId eq compoundHierarchyId.organizationId?.value) and
                        (RoleAssignmentsTable.productId eq compoundHierarchyId.productId?.value) and
                        (RoleAssignmentsTable.repositoryId eq compoundHierarchyId.repositoryId?.value) and
                        roleCondition(role)
            }.mapTo(mutableSetOf()) { it[RoleAssignmentsTable.userId] }
    }

    override suspend fun listUsers(compoundHierarchyId: CompoundHierarchyId): Map<String, Set<Role>> = db.dbQuery {
        RoleAssignmentsTable.selectAll()
            .where {
                repositoryCondition(compoundHierarchyId) or productWildcardCondition(compoundHierarchyId)
            }.map { row -> row[RoleAssignmentsTable.userId] to row.extractRole() }
            .filterNot { it.second == null }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { it.value.filterNotNullTo(mutableSetOf()) }
    }

    /**
     * Retrieve the missing components to construct a [CompoundHierarchyId] from the given [hierarchyId]. Throw a
     * meaningful exception if this fails.
     */
    private suspend fun resolveCompoundId(hierarchyId: HierarchyId): CompoundHierarchyId =
        when (hierarchyId) {
            is OrganizationId -> CompoundHierarchyId.forOrganization(hierarchyId)
            is ProductId -> CompoundHierarchyId.forProduct(resolveOrganization(hierarchyId), hierarchyId)
            is RepositoryId -> {
                val (orgId, prodId) = resolveOrganizationAndProduct(hierarchyId)
                CompoundHierarchyId.forRepository(orgId, prodId, hierarchyId)
            }
        }

    /**
     * Retrieve the ID of the organization the product with the given [productId] belongs to. Return *null* if the
     * product does not exist.
     */
    private suspend fun resolveOrganization(productId: ProductId): OrganizationId =
        db.dbQuery {
            ProductsTable.select(ProductsTable.organizationId)
                .where { ProductsTable.id eq productId.value }
                .singleOrNull()?.let {
                    OrganizationId(it[ProductsTable.organizationId].value)
                }
        } ?: OrganizationId(INVALID_ID)

    /**
     * Retrieve the IDs of the organization and product the repository with the given [repositoryId] belongs to.
     * Return *null* if IDs cannot be resolved.
     */
    private suspend fun resolveOrganizationAndProduct(repositoryId: RepositoryId): Pair<OrganizationId, ProductId> =
        db.dbQuery {
            RepositoriesTable.join(ProductsTable, JoinType.INNER)
                .select(ProductsTable.organizationId, RepositoriesTable.productId)
                .where { RepositoriesTable.id eq repositoryId.value }
                .map { row ->
                    Pair(
                        OrganizationId(row[ProductsTable.organizationId].value),
                        ProductId(row[RepositoriesTable.productId].value)
                    )
                }.singleOrNull()
        } ?: Pair(OrganizationId(INVALID_ID), ProductId(INVALID_ID))

    /**
     * Load all role assignments for the given [userId] in the hierarchy defined by [compoundHierarchyId]. Return a
     * list with pairs of IDs and assigned roles for the entities that were found. The function selects assignments in
     * the whole hierarchy below the organization referenced by [compoundHierarchyId]. This makes sure that all
     * relevant assignments are found.
     */
    private suspend fun loadAssignments(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId
    ): List<Pair<CompoundHierarchyId, Role>> = db.dbQuery {
        RoleAssignmentsTable.selectAll()
            .where {
                (RoleAssignmentsTable.userId eq userId) and (
                        (RoleAssignmentsTable.organizationId eq compoundHierarchyId.organizationId?.value) or
                                (RoleAssignmentsTable.organizationId eq null)
                        )
            }.mapNotNull { row ->
                row.extractRole()?.let { role ->
                    row.extractHierarchyId() to role
                }
            }
    }

    /**
     * Remove the current role assignment for the user with the given [userId] on the hierarchy element defined by
     * [compoundHierarchyId] if there is one. The return value indicates whether an assignment was removed.
     */
    private fun doRemoveAssignment(userId: String, compoundHierarchyId: CompoundHierarchyId): Boolean = (
            RoleAssignmentsTable.deleteWhere {
                (RoleAssignmentsTable.userId eq userId) and
                        (RoleAssignmentsTable.organizationId eq compoundHierarchyId.organizationId?.value) and
                        (RoleAssignmentsTable.productId eq compoundHierarchyId.productId?.value) and
                        (RoleAssignmentsTable.repositoryId eq compoundHierarchyId.repositoryId?.value)
            } == 1
    ).also {
        if (it) {
            logger.info(
                "Removed role assignment for user '{}' on hierarchy element {}.",
                userId,
                compoundHierarchyId
            )
        }
    }
}

/**
 * Constant to represent an invalid ID in the database. This is used to determine whether resolving an ID failed.
 */
private const val INVALID_ID = -1L

/**
 * An implementation of the [EffectiveRole] interface used by [DbAuthorizationService].
 */
private class EffectiveRoleImpl(
    override val elementId: CompoundHierarchyId,

    override val isSuperuser: Boolean,

    /** The permissions granted on the different levels of the hierarchy. */
    private val permissions: PermissionChecker
) : EffectiveRole {
    override fun hasOrganizationPermission(permission: OrganizationPermission): Boolean =
        permission in permissions.organizationPermissions

    override fun hasProductPermission(permission: ProductPermission): Boolean =
        permission in permissions.productPermissions

    override fun hasRepositoryPermission(permission: RepositoryPermission): Boolean =
        permission in permissions.repositoryPermissions
}

/**
 * Check whether this [CompoundHierarchyId] is invalid. This means that its components could not be resolved.
 */
private fun CompoundHierarchyId.isInvalid() =
    organizationId?.value == INVALID_ID ||
            productId?.value == INVALID_ID ||
            repositoryId?.value == INVALID_ID

/**
 * Fetch the concrete [Role] that is referenced by this [ResultRow].
 */
private fun ResultRow.extractRole(): Role? = runCatching {
    listOfNotNull(
        this[RoleAssignmentsTable.organizationRole]?.let(OrganizationRole::valueOf),
        this[RoleAssignmentsTable.productRole]?.let(ProductRole::valueOf),
        this[RoleAssignmentsTable.repositoryRole]?.let(RepositoryRole::valueOf)
    ).first()
}.onFailure {
    logger.error("Failed to extract role from database row: ${this[RoleAssignmentsTable.id]}", it)
}.getOrNull()

/**
 * Extract the [CompoundHierarchyId] on the correct level from this [ResultRow].
 */
private fun ResultRow.extractHierarchyId(): CompoundHierarchyId {
    val orgId = this[RoleAssignmentsTable.organizationId]?.let { OrganizationId(it.value) }
    if (orgId == null) {
        return CompoundHierarchyId.WILDCARD
    } else {
        val productId = this[RoleAssignmentsTable.productId]?.let { ProductId(it.value) }
        if (productId == null) {
            return CompoundHierarchyId.forOrganization(orgId)
        } else {
            val repositoryId = this[RoleAssignmentsTable.repositoryId]?.let { RepositoryId(it.value) }
            return if (repositoryId == null) {
                CompoundHierarchyId.forProduct(orgId, productId)
            } else {
                CompoundHierarchyId.forRepository(orgId, productId, repositoryId)
            }
        }
    }
}

/**
 * Generate the SQL condition to match the repository part of this [hierarchyId]. The condition also has to select
 * assignments on higher levels in the same hierarchy.
 */
private fun SqlExpressionBuilder.repositoryCondition(hierarchyId: CompoundHierarchyId): Op<Boolean> =
    (
            (RoleAssignmentsTable.repositoryId eq hierarchyId.repositoryId?.value) or
                    (RoleAssignmentsTable.repositoryId eq null)
            ) and
            (RoleAssignmentsTable.productId eq hierarchyId.productId?.value) and
            (RoleAssignmentsTable.organizationId eq hierarchyId.organizationId?.value)

/**
 * Generate the SQL condition to match role assignments for the given [hierarchyId] for which no product ID is
 * defined.
 */
private fun SqlExpressionBuilder.productWildcardCondition(hierarchyId: CompoundHierarchyId): Op<Boolean> =
    (RoleAssignmentsTable.productId eq null) and
            (RoleAssignmentsTable.organizationId eq hierarchyId.organizationId?.value)

/**
 * Generate the SQL condition to match role assignments for the given [role].
 */
private fun SqlExpressionBuilder.roleCondition(role: Role): Op<Boolean> =
    when (role) {
        is OrganizationRole -> RoleAssignmentsTable.organizationRole eq role.name
        is ProductRole -> RoleAssignmentsTable.productRole eq role.name
        is RepositoryRole -> RoleAssignmentsTable.repositoryRole eq role.name
    }

/**
 * Return a collection with the roles that are relevant on the hierarchy level defined by [hierarchyId].
 */
private fun rolesForLevel(hierarchyId: CompoundHierarchyId): Collection<Role> =
    when (hierarchyId.level) {
        CompoundHierarchyId.REPOSITORY_LEVEL -> RepositoryRole.entries
        CompoundHierarchyId.PRODUCT_LEVEL -> ProductRole.entries
        else -> OrganizationRole.entries
    }
