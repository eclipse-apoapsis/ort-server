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
 * This enum contains the available roles for [products][org.eclipse.apoapsis.ortserver.model.Product]. It
 * maps the permissions available for a product to the default roles [READER], [WRITER], and [ADMIN].
 *
 * Notes:
 * - Permissions are inherited down the hierarchy. This is implemented by including the lower level permissions
 *   of the corresponding roles in the sets of permissions managed by the single instances.
 * - The constants are expected to be listed in increasing order of permissions.
 */
enum class ProductRole(
    override val organizationPermissions: Set<OrganizationPermission> = organizationReadPermissions,
    override val productPermissions: Set<ProductPermission>,
    override val repositoryPermissions: Set<RepositoryPermission>
) : Role {
    /** A role that grants read permissions for a [org.eclipse.apoapsis.ortserver.model.Product]. */
    READER(
        productPermissions = productReadPermissions,
        repositoryPermissions = RepositoryRole.READER.repositoryPermissions
    ),

    /** A role that grants write permissions for a [org.eclipse.apoapsis.ortserver.model.Product]. */
    WRITER(
        productPermissions = setOf(
            ProductPermission.READ,
            ProductPermission.WRITE,
            ProductPermission.READ_REPOSITORIES,
            ProductPermission.CREATE_REPOSITORY,
            ProductPermission.TRIGGER_ORT_RUN
        ),
        repositoryPermissions = RepositoryRole.WRITER.repositoryPermissions
    ),

    /**
     * A role that grants all permissions for a [org.eclipse.apoapsis.ortserver.model.Product]. Note that this
     * inherits to lower levels in the hierarchy.
     */
    ADMIN(
        productPermissions = ProductPermission.entries.toSet(),
        repositoryPermissions = RepositoryPermission.entries.toSet()
    );

    override val level = CompoundHierarchyId.PRODUCT_LEVEL
}
