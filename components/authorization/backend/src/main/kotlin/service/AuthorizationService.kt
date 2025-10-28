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

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.PermissionChecker
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyId

/**
 * A service interface providing functionality to query and manage roles and permissions for users on entities in the
 * hierarchy.
 */
interface AuthorizationService {
    /**
     * Check whether the user identified by [userId] has the permissions defined by the given [checker] on the element
     * identified by [compoundHierarchyId]. If the check is successful, the function returns an [EffectiveRole] object
     * with the requested permissions and the [compoundHierarchyId]; otherwise, it returns *null*.
     */
    suspend fun checkPermissions(
        userId: String,
        compoundHierarchyId: CompoundHierarchyId,
        checker: PermissionChecker
    ): EffectiveRole?

    /**
     * Check whether the user identified by [userId] has the permissions defined by the given [checker] on the element
     * identified by [hierarchyId]. This function constructs a [CompoundHierarchyId] based on the passed in
     * [hierarchyId]. If such a [CompoundHierarchyId] is already known, using the overloaded function is more
     * efficient.
     */
    suspend fun checkPermissions(
        userId: String,
        hierarchyId: HierarchyId,
        checker: PermissionChecker
    ): EffectiveRole?

    /**
     * Return an [EffectiveRole] object for the specified [userId] that contains all permissions for this user on the
     * element identified by [compoundHierarchyId].
     */
    suspend fun getEffectiveRole(userId: String, compoundHierarchyId: CompoundHierarchyId): EffectiveRole

    /**
     * Return an [EffectiveRole] object for the specified [userId] that contains all permissions for this user on the
     * element identified by [hierarchyId]. This function constructs a [CompoundHierarchyId] based on the passed in
     * [hierarchyId]. If such a [CompoundHierarchyId] is already known, using the overloaded function is more
     * efficient.
     */
    suspend fun getEffectiveRole(userId: String, hierarchyId: HierarchyId): EffectiveRole

    /**
     * Assign the given [role] to the user identified by [userId] on the hierarchy element identified by the given
     * [compoundHierarchyId]. An already existing role assignment on this hierarchy level is overridden.
     */
    suspend fun assignRole(userId: String, role: Role, compoundHierarchyId: CompoundHierarchyId)

    /**
     * Remove any role assignment for the given [userId] on the hierarchy element identified by the given
     * [compoundHierarchyId]. This means that this user no longer has any permissions on this hierarchy level, unless
     * there are role assignments on higher levels in the hierarchy that inherit downwards. The return value indicates
     * whether a role assignment existed on this level that was actually removed.
     */
    suspend fun removeAssignment(userId: String, compoundHierarchyId: CompoundHierarchyId): Boolean

    /**
     * Return a [Set] with the IDs of all users who are assigned the given [role] on the hierarchy element identified
     * by the specified [compoundHierarchyId]. The function returns only users with an explicit assignment of this
     * role; this does not include users inheriting this role from higher levels in the hierarchy.
     */
    suspend fun listUsersWithRole(role: Role, compoundHierarchyId: CompoundHierarchyId): Set<String>

    /**
     * Return a [Map] with the IDs of all users and their assigned roles on the hierarchy element identified by the
     * given [compoundHierarchyId]. The result includes users who inherit access rights on this hierarchy element from
     * higher levels, such as organization admins.
     */
    suspend fun listUsers(compoundHierarchyId: CompoundHierarchyId): Map<String, Set<Role>>
}
