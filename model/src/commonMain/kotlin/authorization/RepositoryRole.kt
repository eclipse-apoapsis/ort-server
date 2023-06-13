/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.model.authorization

import org.ossreviewtoolkit.server.model.Repository

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
        /** Get all group names for the provided [repositoryId]. */
        fun getGroupsForRepository(repositoryId: Long) =
            enumValues<RepositoryRole>().map { it.groupName(repositoryId) }

        /** Get all role names for the provided [repositoryId]. */
        fun getRolesForRepository(repositoryId: Long) =
            enumValues<RepositoryRole>().map { it.roleName(repositoryId) }

        /** A unique prefix for the groups for the provided [repositoryId]. */
        fun groupPrefix(repositoryId: Long) = "REPOSITORY_${repositoryId}_"

        /** A unique prefix for the roles for the provided [repositoryId]. */
        fun rolePrefix(repositoryId: Long) = "role_repository_${repositoryId}_"
    }

    /** A unique name for this role to be used to represent the role as a group in Keycloak. */
    fun groupName(repositoryId: Long): String = "${groupPrefix(repositoryId)}${name.uppercase()}S"

    /** A unique name for this role to be used to represent the role as a role in Keycloak. */
    fun roleName(repositoryId: Long): String = "${rolePrefix(repositoryId)}${name.lowercase()}"
}
