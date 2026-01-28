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

@file:Suppress("TooManyFunctions")

package org.eclipse.apoapsis.ortserver.components.authorization.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.components.authorization.db.RoleAssignmentsTable
import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.HierarchyPermissions
import org.eclipse.apoapsis.ortserver.components.authorization.rights.IdsByLevel
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.PermissionChecker
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RoleInfo
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

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
    ): EffectiveRole? =
        checkPermissions(userId, resolveCompoundId(hierarchyId), checker)

    override suspend fun getEffectiveRole(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId
    ): EffectiveRole {
        val roleAssignments = loadAssignments(userId, compoundHierarchyId)

        return findHighestRole(
            roleAssignments,
            compoundHierarchyId
        )?.let { (_, permissionChecker, hierarchyPermissions) ->
            EffectiveRoleImpl(
                elementId = compoundHierarchyId,
                isSuperuser = hierarchyPermissions.isSuperuser(),
                permissions = permissionChecker
            )
        } ?: EffectiveRoleImpl(
            elementId = compoundHierarchyId,
            isSuperuser = false,
            permissions = PermissionChecker()
        )
    }

    override suspend fun getEffectiveRole(
        userId: String,
        hierarchyId: HierarchyId
    ): EffectiveRole =
        getEffectiveRole(userId, resolveCompoundId(hierarchyId))

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

    override suspend fun listUsers(compoundHierarchyId: CompoundHierarchyId): Map<String, RoleInfo> =
        withContext(Dispatchers.Default) {
            db.dbQuery {
                logger.debug("Loading role assignments on element {}...", compoundHierarchyId)

                RoleAssignmentsTable.selectAll()
                    .where {
                        RoleAssignmentsTable.organizationId eq compoundHierarchyId.organizationId?.value
                    }.mapNotNull { row ->
                        row.extractRole()?.let { role ->
                            Triple(row[RoleAssignmentsTable.userId], role, row.extractHierarchyId())
                        }
                    }
            }.groupBy(keySelector = { it.first }, valueTransform = { it.third to it.second })
                .mapValues { (user, assignments) ->
                    async { computeRoleForUser(user, compoundHierarchyId, assignments) }
                }.mapNotNull { (user, deferredRole) ->
                    deferredRole.await()?.let { user to it }
                }.toMap()
        }

    override suspend fun filterHierarchyIds(
        userId: String,
        organizationPermissions: Set<OrganizationPermission>,
        productPermissions: Set<ProductPermission>,
        repositoryPermissions: Set<RepositoryPermission>,
        containedIn: HierarchyId?
    ): HierarchyFilter {
        val assignments = loadAssignments(userId, null)
        val checker = PermissionChecker(
            organizationPermissions,
            productPermissions,
            repositoryPermissions
        )
        val permissions = HierarchyPermissions.create(assignments, checker)

        val containedInId = containedIn?.let { resolveCompoundId(it) }
        val includes = includesDominatedByContainsFilter(permissions, containedInId)
            ?: permissions.includes().filterContainedIn(containedInId)

        return HierarchyFilter(
            transitiveIncludes = includes,
            nonTransitiveIncludes = permissions.implicitIncludes().filterContainedIn(containedInId),
            isWildcard = permissions.isSuperuser() && containedInId == null
        )
    }

    /**
     * Check if the given [containedInId] filter is contained in any of the includes in the given [permissions]. If so,
     * the user can access all elements under the [containedInId] ID, so return a map of includes with just this ID.
     * If this is not the case or [containedInId] is undefined, return *null*.
     */
    private fun includesDominatedByContainsFilter(
        permissions: HierarchyPermissions,
        containedInId: CompoundHierarchyId?
    ): IdsByLevel? =
        containedInId?.let {
            val containedInLevel = it.level
            val isFilterCovered = permissions.includes().entries.any { (level, ids) ->
                level < containedInLevel && ids.any { id -> containedInId in id }
            }

            if (isFilterCovered) {
                mapOf(containedInId.level to listOf(containedInId))
            } else {
                null
            }
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
        }.also {
            if (it.isInvalid()) {
                logger.warn("Failed to resolve hierarchy ID $hierarchyId.")
                throw InvalidHierarchyIdException(hierarchyId)
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
     * the whole hierarchy below the organization referenced by [compoundHierarchyId] or all assignments of the given
     * [userId] if no ID is specified. This makes sure that all relevant assignments are found.
     */
    private suspend fun loadAssignments(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId?
    ): List<Pair<CompoundHierarchyId, Role>> = db.dbQuery {
        logger.debug("Loading role assignments for user '{}' on element {}...", userId, compoundHierarchyId)

        RoleAssignmentsTable.selectAll()
            .where {
                val hierarchyCondition = compoundHierarchyId?.let { id ->
                    (RoleAssignmentsTable.organizationId eq id.organizationId?.value) or
                            (RoleAssignmentsTable.organizationId eq null)
                } ?: Op.TRUE
                (RoleAssignmentsTable.userId eq userId) and hierarchyCondition
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
    override fun getOrganizationPermissions(): Set<OrganizationPermission> = permissions.organizationPermissions

    override fun getProductPermissions(): Set<ProductPermission> = permissions.productPermissions

    override fun getRepositoryPermissions(): Set<RepositoryPermission> = permissions.repositoryPermissions
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
 * Compute the effective [Role] for a user on the element with the given [hierarchyId] based on the given
 * [assignments].
 */
private fun computeRoleForUser(
    user: String,
    hierarchyId: CompoundHierarchyId,
    assignments: List<Pair<CompoundHierarchyId, Role>>
): RoleInfo? {
    logger.debug("Computing effective role for user '{}' on element {}...", user, hierarchyId)

    return findHighestRole(assignments, hierarchyId)?.first
}

/**
 * Generate the SQL condition to match role assignments for the given [role].
 */
private fun roleCondition(role: Role): Op<Boolean> =
    when (role) {
        is OrganizationRole -> RoleAssignmentsTable.organizationRole eq role.name
        is ProductRole -> RoleAssignmentsTable.productRole eq role.name
        is RepositoryRole -> RoleAssignmentsTable.repositoryRole eq role.name
    }

/**
 * Filter the IDs in this [IdsByLevel] to only include those that are contained in the given [containedInId]. If
 * [containedInId] is *null*, return this object unmodified.
 */
private fun IdsByLevel.filterContainedIn(
    containedInId: CompoundHierarchyId?
): IdsByLevel =
    if (containedInId == null) {
        this
    } else {
        this.mapValues { (_, ids) ->
            ids.filter { id -> id in containedInId }
        }.filterValues { it.isNotEmpty() }
    }

/**
 * Find the highest role for the given [hierarchyId] based on the provided [roleAssignments]. Check for all roles on
 * the level of the hierarchy ID, starting with the ADMIN role, whether all its permissions are granted. This is then
 * the effective role. This assumes that constants in the enums for roles are ordered by the number of permissions they
 * grant in ascending order. (If this changes, there should be failing tests.) Return all relevant information about
 * the detected role and the relevant permissions, or *null* if no role was found.
 */
private fun findHighestRole(
    roleAssignments: List<Pair<CompoundHierarchyId, Role>>,
    hierarchyId: CompoundHierarchyId
): Triple<RoleInfo, PermissionChecker, HierarchyPermissions>? {
    val roles = (
            Role.rolesForLevel(hierarchyId.level)
                .takeUnless { it.isEmpty() } ?: OrganizationRole.entries
            ).reversed().asSequence()

    return roles.mapNotNull { role ->
        val permissionChecker = HierarchyPermissions.permissions(role)
        val permissions = HierarchyPermissions.create(roleAssignments, permissionChecker)
        permissions.permissionGrantedOnLevel(hierarchyId)?.let {
            Triple(RoleInfo(role, it), permissionChecker, permissions)
        }
    }.firstOrNull()
}
