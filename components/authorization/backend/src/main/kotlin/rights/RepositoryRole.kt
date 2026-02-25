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

import org.eclipse.apoapsis.ortserver.model.HierarchyLevel

/**
 * This enum contains the available roles for [repositories][org.eclipse.apoapsis.ortserver.model.Repository]. It
 * maps the permissions available for a product to the default roles [READER], [WRITER], and [ADMIN].
 */
enum class RepositoryRole(
    override val organizationPermissions: Set<OrganizationPermission> = organizationReadPermissions,
    override val productPermissions: Set<ProductPermission> = productReadPermissions,
    override val repositoryPermissions: Set<RepositoryPermission>
) : Role {
    /** A role that grants read permissions for a [org.eclipse.apoapsis.ortserver.model.Repository]. */
    READER(
        repositoryPermissions = setOf(
            RepositoryPermission.READ,
            RepositoryPermission.READ_ORT_RUNS
        )
    ),

    /** A role that grants write permissions for a [org.eclipse.apoapsis.ortserver.model.Repository]. */
    WRITER(
        repositoryPermissions = setOf(
            RepositoryPermission.READ,
            RepositoryPermission.WRITE,
            RepositoryPermission.MANAGE_RESOLUTIONS,
            RepositoryPermission.READ_ORT_RUNS,
            RepositoryPermission.TRIGGER_ORT_RUN
        )
    ),

    /** A role that grants all permissions for a [org.eclipse.apoapsis.ortserver.model.Repository]. */
    ADMIN(
        repositoryPermissions = RepositoryPermission.entries.toSet()
    );

    override val level = HierarchyLevel.REPOSITORY
}
