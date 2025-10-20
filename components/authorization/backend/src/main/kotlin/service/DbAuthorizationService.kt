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
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
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
    override suspend fun getEffectiveRole(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId
    ): EffectiveRole {
        val roleAssignments = loadAssignments(userId, compoundHierarchyId)
        val permissions = roleAssignments.map { it.toHierarchyPermissions() }
            .takeUnless { it.isEmpty() }?.reduce(::reducePermissions) ?: EMPTY_PERMISSIONS
        val isSuperuser = roleAssignments.any {
            it[RoleAssignmentsTable.organizationId] == null &&
                    it[RoleAssignmentsTable.productId] == null &&
                    it[RoleAssignmentsTable.repositoryId] == null &&
                    it.extractRole() == OrganizationRole.ADMIN
        }

        return EffectiveRoleImpl(
            elementId = compoundHierarchyId,
            isSuperuser = isSuperuser,
            permissions = permissions
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
                permissions = EMPTY_PERMISSIONS
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
     * list of [HierarchyPermissions] instances for the entities that were found.
     */
    private suspend fun loadAssignments(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId
    ): List<ResultRow> = db.dbQuery {
        RoleAssignmentsTable.selectAll()
            .where {
                (RoleAssignmentsTable.userId eq userId) and (
                        repositoryCondition(compoundHierarchyId) or
                                productWildcardCondition(compoundHierarchyId) or
                                organizationWildcardCondition()
                        )
            }.toList()
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
 * An internally used data class to store the available permissions on all levels of the hierarchy for a user.
 */
private data class HierarchyPermissions(
    /** The permissions granted on organization level. */
    val organizationPermissions: Set<OrganizationPermission>,

    /** The permissions granted on product level. */
    val productPermissions: Set<ProductPermission>,

    /** The permissions granted on repository level. */
    val repositoryPermissions: Set<RepositoryPermission>
)

/** An instance of [HierarchyPermissions] with no permissions at all. */
private val EMPTY_PERMISSIONS = HierarchyPermissions(
    organizationPermissions = emptySet(),
    productPermissions = emptySet(),
    repositoryPermissions = emptySet()
)

/**
 * An implementation of the [EffectiveRole] interface used by [DbAuthorizationService].
 */
private class EffectiveRoleImpl(
    override val elementId: CompoundHierarchyId,

    override val isSuperuser: Boolean,

    /** The permissions granted on the different levels of the hierarchy. */
    private val permissions: HierarchyPermissions
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
 * Obtain the information about roles from this [ResultRow] and construct a [HierarchyPermissions] object from it.
 */
private fun ResultRow.toHierarchyPermissions(): HierarchyPermissions =
    extractRole()?.let { role ->
        HierarchyPermissions(
            organizationPermissions = role.organizationPermissions,
            productPermissions = role.productPermissions,
            repositoryPermissions = role.repositoryPermissions
        )
    } ?: EMPTY_PERMISSIONS

/**
 * Combine two [HierarchyPermissions] instances [p1] and [p2] by constructing the union of their permissions on all
 * levels.
 */
private fun reducePermissions(
    p1: HierarchyPermissions,
    p2: HierarchyPermissions
): HierarchyPermissions =
    HierarchyPermissions(
        organizationPermissions = p1.organizationPermissions + p2.organizationPermissions,
        productPermissions = p1.productPermissions + p2.productPermissions,
        repositoryPermissions = p1.repositoryPermissions + p2.repositoryPermissions
    )

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
 * Generate the SQL condition to match role assignments for which no organization ID is defined.
 */
private fun SqlExpressionBuilder.organizationWildcardCondition(): Op<Boolean> =
    RoleAssignmentsTable.organizationId eq null

/**
 * Generate the SQL condition to match role assignments for the given [role].
 */
private fun SqlExpressionBuilder.roleCondition(role: Role): Op<Boolean> =
    when (role) {
        is OrganizationRole -> RoleAssignmentsTable.organizationRole eq role.name
        is ProductRole -> RoleAssignmentsTable.productRole eq role.name
        is RepositoryRole -> RoleAssignmentsTable.repositoryRole eq role.name
    }
