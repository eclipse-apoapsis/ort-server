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
typealias IdsByLevel = Map<Int, List<CompoundHierarchyId>>

/**
 * A class to manage permissions on different levels of the hierarchy.
 *
 * This class controls the effect of role assignments to users and how permissions are inherited through the hierarchy.
 * It implements the following rules:
 * - Role assignments on higher levels in the hierarchy inherit downwards to lower levels. So, if a user is granted
 *   the `WRITER` role on an organization, they automatically have `WRITER` permissions on all products and
 *   repositories within that organization.
 * - Role assignments on lower levels in the hierarchy can widen the permissions inherited from higher levels, but not
 *   restrict them. For example, a `WRITER` role assignment for a user on product level could be turned into an `ADMIN`
 *   role assignment on repository level. However, a `READER` role assignment on repository level would be ignored if
 *   the user already has `WRITER` permissions on the parent product.
 * - Role assignments on lower levels of the hierarchy can trigger implicit permissions on higher levels. So, if a user
 *   has access to a repository, they implicitly have at least `READER` access to the parent product and organization.
 *
 * An instance of this class is created for a given set of role assignments and for a specific set of permissions
 * controlled by a [PermissionChecker]. The functions of this class can then be used to find out on which hierarchy
 * elements these permissions are granted.
 */
interface HierarchyPermissions {
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

            return assignmentsByLevel[CompoundHierarchyId.WILDCARD_LEVEL]?.singleOrNull()
                ?.takeIf { it.second == OrganizationRole.ADMIN }?.let { superuserInstance }
                ?: createStandardInstance(assignmentsByLevel, checker)
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
    fun hasPermission(compoundHierarchyId: CompoundHierarchyId): Boolean

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

/**
 * A special instance of [HierarchyPermissions] that is returned by [HierarchyPermissions.create] when an assignment
 * of superuser permissions is detected. This instance grants all permissions and returns corresponding filters.
 */
private val superuserInstance = object : HierarchyPermissions {
    override fun hasPermission(compoundHierarchyId: CompoundHierarchyId): Boolean = true

    override fun includes(): IdsByLevel =
        mapOf(CompoundHierarchyId.WILDCARD_LEVEL to listOf(CompoundHierarchyId.WILDCARD))

    override fun implicitIncludes(): IdsByLevel = emptyMap()

    override fun isSuperuser(): Boolean = true
}

/**
 * Create an instance of [HierarchyPermissions] for standard users based on the given [Map] with
 * [assignmentsByLevel] and the [checker] function.
 */
private fun createStandardInstance(
    assignmentsByLevel: Map<Int, List<Pair<CompoundHierarchyId, Role>>>,
    checker: PermissionChecker
): HierarchyPermissions {
    val assignmentsMap = constructAssignmentsMap(assignmentsByLevel, checker)
    val implicits = computeImplicitIncludes(assignmentsMap, assignmentsByLevel, checker)
    val implicitIds = implicits.values.flatMapTo(mutableSetOf()) { it }

    return object : HierarchyPermissions {
        override fun hasPermission(compoundHierarchyId: CompoundHierarchyId): Boolean =
            findAssignment(assignmentsMap, compoundHierarchyId) || compoundHierarchyId in implicitIds

        override fun includes(): IdsByLevel =
            assignmentsMap.filter { e -> e.value }
                .keys
                .byLevel()

        override fun implicitIncludes(): IdsByLevel = implicits

        override fun isSuperuser(): Boolean = false
    }
}

/**
 * Return the closest permission check result for the given [id] by traversing up the hierarchy if necessary. If no
 * assignment is found for the given [id] or any of its parents, assume that the permissions are not present.
 */
private tailrec fun findAssignment(
    assignments: Map<CompoundHierarchyId, Boolean>,
    id: CompoundHierarchyId?
): Boolean =
    if (id == null) {
        false
    } else {
        assignments[id] ?: findAssignment(assignments, id.parent)
    }

/**
 * Construct the [Map] with information about available permissions on different levels in the hierarchy based on
 * the given [assignmentsByLevel] and the [checker] function.
 */
private fun constructAssignmentsMap(
    assignmentsByLevel: Map<Int, List<Pair<CompoundHierarchyId, Role>>>,
    checker: PermissionChecker
): Map<CompoundHierarchyId, Boolean> = buildMap {
    for (level in CompoundHierarchyId.ORGANIZATION_LEVEL..CompoundHierarchyId.REPOSITORY_LEVEL) {
        val levelAssignments = assignmentsByLevel[level].orEmpty()
        levelAssignments.forEach { (id, role) ->
            val isPresent = checker(role)
            val isPresentOnParent = findAssignment(this, id.parent)

            // If this assignment does not change the status from a higher level, it can be skipped.
            if (isPresent && !isPresentOnParent) {
                put(id, true)
            }
        }
    }
}

/**
 * Find the IDs of all hierarchy elements from [assignmentsByLevel] that are granted implicit permissions due to role
 * assignments on lower levels in the hierarchy. The given [assignmentsMap] has already been populated with explicit
 * role assignments. Use the given [checker] function to determine whether permissions are granted.
 */
private fun computeImplicitIncludes(
    assignmentsMap: Map<CompoundHierarchyId, Boolean>,
    assignmentsByLevel: Map<Int, List<Pair<CompoundHierarchyId, Role>>>,
    checker: PermissionChecker
): IdsByLevel = buildSet {
    for (level in CompoundHierarchyId.PRODUCT_LEVEL..CompoundHierarchyId.REPOSITORY_LEVEL) {
        assignmentsByLevel[level].orEmpty().filter { (_, role) -> checker(role) }
            .forEach { (id, _) ->
                val parents = id.parents()
                if (parents.none { it in assignmentsMap }) {
                    addAll(parents)
                }
            }
    }
}.byLevel()

/**
 * Group the IDs contained in this [Collection] by their hierarchy level.
 */
private fun Collection<CompoundHierarchyId>.byLevel(): IdsByLevel =
    this.groupBy { it.level }

/**
 * Return a list with the IDs of all parents of this [CompoundHierarchyId].
 */
private fun CompoundHierarchyId.parents(): List<CompoundHierarchyId> {
    val parents = mutableListOf<CompoundHierarchyId>()

    tailrec fun findParents(id: CompoundHierarchyId?) {
        if (id != null) {
            parents += id
            findParents(id.parent)
        }
    }

    findParents(parent)
    return parents
}
