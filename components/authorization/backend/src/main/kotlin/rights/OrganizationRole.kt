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
 * This enum contains the available roles for [organizations][org.eclipse.apoapsis.ortserver.model.Organization]. It
 * maps the permissions available for an organization to the default roles [READER], [WRITER], and [ADMIN].
 *
 * Notes:
 * - Permissions are inherited down the hierarchy. This is implemented by including the lower level permissions
 *   of the corresponding roles in the sets of permissions managed by the single instances.
 * - The constants are expected to be listed in increasing order of permissions.
 */
enum class OrganizationRole(
    override val organizationPermissions: Set<OrganizationPermission>,
    override val productPermissions: Set<ProductPermission>,
    override val repositoryPermissions: Set<RepositoryPermission>
) : Role {
    /** A role that grants read permissions for an [org.eclipse.apoapsis.ortserver.model.Organization]. */
    READER(
        organizationPermissions = organizationReadPermissions,
        productPermissions = ProductRole.READER.productPermissions,
        repositoryPermissions = RepositoryRole.READER.repositoryPermissions
    ),

    /** A role that grants write permissions for an [org.eclipse.apoapsis.ortserver.model.Organization]. */
    WRITER(
        organizationPermissions = setOf(
            OrganizationPermission.READ,
            OrganizationPermission.WRITE,
            OrganizationPermission.READ_PRODUCTS,
            OrganizationPermission.CREATE_PRODUCT
        ),
        productPermissions = ProductRole.WRITER.productPermissions,
        repositoryPermissions = RepositoryRole.WRITER.repositoryPermissions
    ),

    /**
     * A role that grants all permissions for an [org.eclipse.apoapsis.ortserver.model.Organization]. Note that this
     * inherits to lower levels in the hierarchy.
     */
    ADMIN(
        organizationPermissions = OrganizationPermission.entries.toSet(),
        productPermissions = ProductPermission.entries.toSet(),
        repositoryPermissions = RepositoryPermission.entries.toSet()
    );

    override val level = CompoundHierarchyId.ORGANIZATION_LEVEL
}
