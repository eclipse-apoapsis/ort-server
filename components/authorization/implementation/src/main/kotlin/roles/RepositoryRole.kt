/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.authorization.roles

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.util.extractIdAfterPrefix

/**
 * This enum contains the available roles for [repositories][Repository]. These roles are used to create composite
 * Keycloak roles containing the roles representing the [permissions]. They are also used to create Keycloak groups to
 * improve the overview when assigning users to roles.
 */
enum class RepositoryRole(
    /** The [RepositoryPermission]s granted by this role. */
    val permissions: Set<RepositoryPermission>
) {
    /** A role that grants read permissions for a [Repository]. */
    READER(
        permissions = setOf(
            RepositoryPermission.READ,
            RepositoryPermission.READ_ORT_RUNS
        )
    ),

    /** A role that grants write permissions for a [Repository]. */
    WRITER(
        permissions = setOf(
            RepositoryPermission.READ,
            RepositoryPermission.WRITE,
            RepositoryPermission.READ_ORT_RUNS,
            RepositoryPermission.TRIGGER_ORT_RUN
        )
    ),

    /** A role that grants all permissions for a [Repository]. */
    ADMIN(
        permissions = RepositoryPermission.values().toSet()
    );

    companion object {
        /** The prefix for the groups used for repositories. */
        private const val GROUP_PREFIX = "REPOSITORY_"

        /** The prefix for the roles used for repositories. */
        private const val ROLE_PREFIX = "role_repository_"

        /** Get all group names for the provided [repositoryId]. */
        fun getGroupsForRepository(repositoryId: Long) =
            enumValues<RepositoryRole>().map { it.groupName(repositoryId) }

        /** Get all role names for the provided [repositoryId]. */
        fun getRolesForRepository(repositoryId: Long) =
            enumValues<RepositoryRole>().map { it.roleName(repositoryId) }

        /** A unique prefix for the groups for the provided [repositoryId]. */
        fun groupPrefix(repositoryId: Long) = "$GROUP_PREFIX${repositoryId}_"

        /** A unique prefix for the roles for the provided [repositoryId]. */
        fun rolePrefix(repositoryId: Long) = "$ROLE_PREFIX${repositoryId}_"

        /**
         * Return the ID of the repository this [roleName] belongs to or *null* if [roleName] does not reference a
         * repository role.
         */
        fun extractRepositoryIdFromRole(roleName: String): Long? =
            roleName.extractIdAfterPrefix(ROLE_PREFIX)

        /**
         * Return the ID of the repository this [groupName] belongs to or *null* if [groupName] does not reference a
         * repository group.
         */
        fun extractRepositoryIdFromGroup(groupName: String): Long? =
            groupName.extractIdAfterPrefix(GROUP_PREFIX)
    }

    /** A unique name for this role to be used to represent the role as a group in Keycloak. */
    fun groupName(repositoryId: Long): String = "${groupPrefix(repositoryId)}${name.uppercase()}S"

    /** A unique name for this role to be used to represent the role as a role in Keycloak. */
    fun roleName(repositoryId: Long): String = "${rolePrefix(repositoryId)}${name.lowercase()}"
}
