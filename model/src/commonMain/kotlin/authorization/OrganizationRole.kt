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

package org.eclipse.apoapsis.ortserver.model.authorization

import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.Product

/**
 * This enum contains the available roles for [organizations][Organization]. These roles are used to create composite
 * Keycloak roles containing the roles representing the [permissions]. They are also used to create Keycloak groups to
 * improve the overview when assigning users to roles.
 */
enum class OrganizationRole(
    /** The [OrganizationPermission]s granted by this role. */
    val permissions: Set<OrganizationPermission>,

    /** A [ProductRole] that is granted for each [Product] of this [Organization]. */
    val includedProductRole: ProductRole
) {
    /** A role that grants read permissions for an [Organization]. */
    READER(
        permissions = setOf(
            OrganizationPermission.READ,
            OrganizationPermission.READ_PRODUCTS
        ),
        includedProductRole = ProductRole.READER
    ),

    /** A role that grants write permissions for an [Organization]. */
    WRITER(
        permissions = setOf(
            OrganizationPermission.READ,
            OrganizationPermission.WRITE,
            OrganizationPermission.READ_PRODUCTS,
            OrganizationPermission.CREATE_PRODUCT
        ),
        includedProductRole = ProductRole.WRITER
    ),

    /** A role that grants all permissions for an [Organization]. */
    ADMIN(
        permissions = OrganizationPermission.values().toSet(),
        includedProductRole = ProductRole.ADMIN
    );

    companion object {
        /** Get all group names for the provided [organizationId]. */
        fun getGroupsForOrganization(organizationId: Long) =
            enumValues<OrganizationRole>().map { it.groupName(organizationId) }

        /** Get all role names for the provided [organizationId]. */
        fun getRolesForOrganization(organizationId: Long) =
            enumValues<OrganizationRole>().map { it.roleName(organizationId) }

        /** A unique prefix for the groups for the provided [organizationId]. */
        fun groupPrefix(organizationId: Long) = "ORGANIZATION_${organizationId}_"

        /** A unique prefix for the roles for the provided [organizationId]. */
        fun rolePrefix(organizationId: Long) = "role_organization_${organizationId}_"
    }

    /** A unique name for this role to be used to represent the role as a group in Keycloak. */
    fun groupName(organizationId: Long): String = "${groupPrefix(organizationId)}${name.uppercase()}S"

    /** A unique name for this role to be used to represent the role as a role in Keycloak. */
    fun roleName(organizationId: Long): String = "${rolePrefix(organizationId)}${name.lowercase()}"
}
