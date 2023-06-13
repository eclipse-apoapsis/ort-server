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

import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository

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
            ProductPermission.CREATE_REPOSITORY
        ),
        includedRepositoryRole = RepositoryRole.WRITER
    ),

    /** A role that grants all permissions for a [Product]. */
    ADMIN(
        permissions = ProductPermission.values().toSet(),
        includedRepositoryRole = RepositoryRole.ADMIN
    );

    companion object {
        /** Get all group names for the provided [productId]. */
        fun getGroupsForProduct(productId: Long) =
            enumValues<ProductRole>().map { it.groupName(productId) }

        /** Get all role names for the provided [productId]. */
        fun getRolesForProduct(productId: Long) =
            enumValues<ProductRole>().map { it.roleName(productId) }

        /** A unique prefix for the roles for the provided [productId]. */
        fun rolePrefix(productId: Long) = "role_product_${productId}_"
    }

    /** A unique name for this role to be used to represent the role as a group in Keycloak. */
    fun groupName(productId: Long): String = "PRODUCT_${productId}_${name.uppercase()}S"

    /** A unique name for this role to be used to represent the role as a role in Keycloak. */
    fun roleName(productId: Long): String = "${rolePrefix(productId)}${name.lowercase()}"
}
