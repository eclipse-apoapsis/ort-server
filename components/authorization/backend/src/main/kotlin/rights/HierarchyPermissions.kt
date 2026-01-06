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

package org.eclipse.apoapsis.ortserver.components.authorization.rights

import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel

/**
 * A class that encapsulates a number of permissions to check on hierarchy elements.
 */
class PermissionChecker(
    /** The required permissions on organization level. */
    val organizationPermissions: Set<OrganizationPermission> = emptySet(),

    /** The required permissions on product level. */
    val productPermissions: Set<ProductPermission> = emptySet(),

    /** The required permissions on repository level. */
    val repositoryPermissions: Set<RepositoryPermission> = emptySet()
) {
    /**
     * Check whether the given [role] contains all permissions required by this [PermissionChecker].
     */
    operator fun invoke(role: Role): Boolean =
        organizationPermissions.all { it in role.organizationPermissions } &&
                productPermissions.all { it in role.productPermissions } &&
                repositoryPermissions.all { it in role.repositoryPermissions }
}

/**
 * Alias for a [Map] that groups [CompoundHierarchyId]s by their hierarchy level. The keys correspond to constants
 * defined by [CompoundHierarchyId].
 */
typealias IdsByLevel = Map<HierarchyLevel, List<CompoundHierarchyId>>

/**
 * A class to manage permissions on different levels of the hierarchy.
 *
 * This class controls the effect of role assignments to users and how permissions are inherited through the hierarchy.
 * It implements the following rules:
 * - Role assignments on higher levels in the hierarchy inherit downwards to lower levels. For example, if a user is
 *   granted the `WRITER` role on an organization, they automatically have `WRITER` permissions on all products and
 *   repositories within that organization.
 * - Role assignments on lower levels in the hierarchy can widen the permissions inherited from higher levels, but not
 *   restrict them. For example, a `WRITER` role assignment for a user on product level could be turned into an `ADMIN`
 *   role assignment on repository level. However, a `READER` role assignment on repository level would be ignored if
 *   the user already has a `WRITER` role assignment on the parent product.
 * - Role assignments on lower levels of the hierarchy can trigger implicit permissions on higher levels. For example,
 *   if a user has access to a repository, they implicitly have at least `READER` access to the parent product and
 *   organization.
 *
 * An instance of this class is created for a given set of role assignments and for a specific set of permissions
 * controlled by a [PermissionChecker]. The functions of this class can then be used to find out on which hierarchy
 * elements these permissions are granted.
 */
sealed interface HierarchyPermissions {
    companion object {
        /**
         * Create a new [HierarchyPermissions] instance for the given collection of role [roleAssignments] that
         * evaluates the permissions controlled by the given [checker] function.
         */
        fun create(
            roleAssignments: Collection<Pair<CompoundHierarchyId, Role>>,
            checker: PermissionChecker
        ): HierarchyPermissions {
            val assignmentsByLevel = roleAssignments.groupBy { it.first.level }

            return assignmentsByLevel[HierarchyLevel.WILDCARD]?.singleOrNull()
                ?.takeIf { it.second == OrganizationRole.ADMIN }?.let { SuperuserPermissions }
                ?: StandardHierarchyPermissions(assignmentsByLevel, checker)
        }

        /**
         * Return a [PermissionChecker] that checks for the presence of all given organization permissions [ps].
         */
        fun permissions(vararg ps: OrganizationPermission): PermissionChecker =
            PermissionChecker(organizationPermissions = ps.toSet())

        /**
         * Return a [PermissionChecker] that checks for the presence of all given product permissions [ps].
         */
        fun permissions(vararg ps: ProductPermission): PermissionChecker =
            PermissionChecker(productPermissions = ps.toSet())

        /**
         * Return a [PermissionChecker] that checks for the presence of all given repository permissions [ps].
         */
        fun permissions(vararg ps: RepositoryPermission): PermissionChecker =
            PermissionChecker(repositoryPermissions = ps.toSet())

        /**
         * Return a [PermissionChecker] that checks for the presence of all permissions defined by the given [role].
         */
        fun permissions(role: Role): PermissionChecker =
            PermissionChecker(
                organizationPermissions = role.organizationPermissions,
                productPermissions = role.productPermissions,
                repositoryPermissions = role.repositoryPermissions
            )
    }

    /**
     * Check whether the permissions evaluated by this instance are granted on the hierarchy element identified by the
     * given [compoundHierarchyId].
     */
    fun hasPermission(compoundHierarchyId: CompoundHierarchyId): Boolean =
        permissionGrantedOnLevel(compoundHierarchyId) != null

    /**
     * Check whether the permissions evaluated by this instance are granted on the hierarchy element identified by the
     * given [compoundHierarchyId], and return the ID of the hierarchy element from where they are granted. Return
     * *null* if no permissions are available. With this function, it is possible to distinguish between explicitly
     * granted permissions on a specific level and those that are inherited through the hierarchy.
     */
    fun permissionGrantedOnLevel(compoundHierarchyId: CompoundHierarchyId): CompoundHierarchyId?

    /**
     * Return a [Map] with the IDs of all hierarchy elements for which a role assignment exists that grants the
     * permissions evaluated by this instance. The result is grouped by hierarchy level. This can be used to generate
     * filter conditions for database queries selecting elements in the hierarchy.
     */
    fun includes(): IdsByLevel

    /**
     * Return a [Map] with the IDs of hierarchy elements for which the permissions evaluated by this instance are
     * implicitly granted due to role assignments on lower levels in the hierarchy. For instance, if READ access is
     * granted on a repository, READ access is also needed on the parent product and organization. Such implicit
     * permissions are different from explicitly granted ones, since they do not inherit downwards in the hierarchy.
     * The result is grouped by hierarchy level. The resulting IDs do not include those returned by [includes]. When
     * constructing database query filters, these IDs need to be included alongside those from [includes].
     */
    fun implicitIncludes(): IdsByLevel

    /**
     * Return a flag whether this instance represents superuser permissions.
     */
    fun isSuperuser(): Boolean
}

/** An implementation of [HierarchyPermissions] for superusers which grants all permissions. */
private object SuperuserPermissions : HierarchyPermissions {
    override fun permissionGrantedOnLevel(compoundHierarchyId: CompoundHierarchyId): CompoundHierarchyId =
        CompoundHierarchyId.WILDCARD

    override fun includes(): IdsByLevel =
        mapOf(HierarchyLevel.WILDCARD to listOf(CompoundHierarchyId.WILDCARD))

    override fun implicitIncludes(): IdsByLevel = emptyMap()

    override fun isSuperuser(): Boolean = true
}

/**
 * An implementation of [HierarchyPermissions] for standard users based on the given
 * [role assignments][assignmentsByLevel] and [permission checker][checker].
 */
private class StandardHierarchyPermissions(
    assignmentsByLevel: Map<HierarchyLevel, List<Pair<CompoundHierarchyId, Role>>>,
    checker: PermissionChecker
) : HierarchyPermissions {
    /**
     * A set of [CompoundHierarchyId]s for which the permissions required by [checker] are directly granted due to role
     * assignments.
     */
    private val directGrants = buildSet {
        for (level in HierarchyLevel.DEFINED_LEVELS_TOP_DOWN) {
            assignmentsByLevel[level].orEmpty().forEach { (id, role) ->
                val isPresent = checker(role)
                val isPresentOnParent = findAssignment(this, id.parent) != null

                // If this assignment does not change the status from a higher level, it can be skipped.
                if (isPresent && !isPresentOnParent) add(id)
            }
        }
    }

    /**
     * Sets of [CompoundHierarchyId]s for which the permissions required by [checker] are implicitly granted due to role
     * assignments on lower levels of the hierarchy, grouped by their hierarchy level.
     */
    private val implicitGrants: IdsByLevel

    /**
     * A map assigning [CompoundHierarchyId]s with [implicitly][implicitGrants] granted permissions to the
     * [CompoundHierarchyId]s on lower levels of the hierarchy whose role assignments caused these implicit permissions.
     */
    private val causesForImplicitGrants: Map<CompoundHierarchyId, CompoundHierarchyId>

    init {
        val implicitIds = mutableSetOf<CompoundHierarchyId>()
        val causingIds = mutableMapOf<CompoundHierarchyId, CompoundHierarchyId>()

        listOf(HierarchyLevel.PRODUCT, HierarchyLevel.REPOSITORY).forEach { level ->
            assignmentsByLevel[level].orEmpty().filter { (_, role) -> checker(role) }
                .forEach { (id, _) ->
                    val parents = id.parents
                    if (parents.none { it in directGrants }) {
                        implicitIds += parents
                        parents.forEach { causingIds[it] = id }
                    }
                }
        }

        implicitGrants = implicitIds.groupBy { it.level }
        causesForImplicitGrants = causingIds
    }

    override fun permissionGrantedOnLevel(compoundHierarchyId: CompoundHierarchyId): CompoundHierarchyId? =
        findAssignment(directGrants, compoundHierarchyId) ?: causesForImplicitGrants[compoundHierarchyId]

    override fun includes(): IdsByLevel = directGrants.groupBy { it.level }

    override fun implicitIncludes(): IdsByLevel = implicitGrants

    override fun isSuperuser(): Boolean = false
}

/**
 * Return the [CompoundHierarchyId] of the closest permission check result for the given [id] by traversing up the
 * hierarchy if necessary. If no assignment is found for the given [id] or any of its parents, assume that the
 * permissions are not present and return *null*.
 */
private tailrec fun findAssignment(
    assignments: Set<CompoundHierarchyId>,
    id: CompoundHierarchyId?
): CompoundHierarchyId? = when (id) {
    null -> null
    in assignments -> id
    else -> findAssignment(assignments, id.parent)
}
