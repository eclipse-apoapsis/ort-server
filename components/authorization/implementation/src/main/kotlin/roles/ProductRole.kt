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

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.util.extractIdAfterPrefix

/**
 * This enum contains the available roles for [products][Product]. These roles are used to create composite Keycloak
 * roles containing the roles representing the [permissions]. They are also used to create Keycloak groups to improve
 * the overview when assigning users to roles.
 */
enum class ProductRole(
    /** The [ProductPermission]s granted by this role. */
    val permissions: Set<ProductPermission>,

    /** The [RepositoryRole] that is granted for each [Repository] of this [Product]. */
    val includedRepositoryRole: RepositoryRole
) {
    /** A role that grants read permissions for a [Product]. */
    READER(
        permissions = setOf(
            ProductPermission.READ,
            ProductPermission.READ_REPOSITORIES
        ),
        includedRepositoryRole = RepositoryRole.READER
    ),

    /** A role that grants write permissions for a [Product]. */
    WRITER(
        permissions = setOf(
            ProductPermission.READ,
            ProductPermission.WRITE,
            ProductPermission.READ_REPOSITORIES,
            ProductPermission.CREATE_REPOSITORY,
            ProductPermission.TRIGGER_ORT_RUN
        ),
        includedRepositoryRole = RepositoryRole.WRITER
    ),

    /** A role that grants all permissions for a [Product]. */
    ADMIN(
        permissions = ProductPermission.values().toSet(),
        includedRepositoryRole = RepositoryRole.ADMIN
    );

    companion object {
        /** The prefix for the groups used by products. */
        private const val GROUP_PREFIX = "PRODUCT_"

        /** The prefix for the roles used by products. */
        private const val ROLE_PREFIX = "role_product_"

        /** Get all group names for the provided [productId]. */
        fun getGroupsForProduct(productId: Long) =
            enumValues<ProductRole>().map { it.groupName(productId) }

        /** Get all role names for the provided [productId]. */
        fun getRolesForProduct(productId: Long) =
            enumValues<ProductRole>().map { it.roleName(productId) }

        /** A unique prefix for the groups for the provided [productId]. */
        fun groupPrefix(productId: Long) = "$GROUP_PREFIX${productId}_"

        /** A unique prefix for the roles for the provided [productId]. */
        fun rolePrefix(productId: Long) = "$ROLE_PREFIX${productId}_"

        /**
         * Return the ID of the product this [roleName] belongs to or *null* if [roleName] does not reference a
         * product role.
         */
        fun extractProductIdFromRole(roleName: String): Long? =
            roleName.extractIdAfterPrefix(ROLE_PREFIX)

        /**
         * Return the ID of the product this [groupName] belongs to or *null* if [groupName] does not reference a
         * product group.
         */
        fun extractProductIdFromGroup(groupName: String): Long? =
            groupName.extractIdAfterPrefix(GROUP_PREFIX)
    }

    /** A unique name for this role to be used to represent the role as a group in Keycloak. */
    fun groupName(productId: Long): String = "${groupPrefix(productId)}${name.uppercase()}S"

    /** A unique name for this role to be used to represent the role as a role in Keycloak. */
    fun roleName(productId: Long): String = "${rolePrefix(productId)}${name.lowercase()}"
}
