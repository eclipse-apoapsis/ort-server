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

package org.eclipse.apoapsis.ortserver.components.authorization.permissions

import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.util.extractIdAfterPrefix

/**
 * This enum contains the available permissions for [products][Product]. These permissions are used by the API to
 * control access to the [Product] endpoints.
 */
enum class ProductPermission {
    /** Permission to read the [Product] details. */
    READ,

    /** Permission to write the [Product] details. */
    WRITE,

    /** Permission to write the [Product] secrets. */
    WRITE_SECRETS,

    /** Permission to manage [Product] groups. */
    MANAGE_GROUPS,

    /** Permission to read the list of [repositories][Repository] of the [Product]. */
    READ_REPOSITORIES,

    /** Permission to create a [Repository] for the [Product]. */
    CREATE_REPOSITORY,

    /** Permission to trigger an ORT run for the [repositories][Repository] of the [Product]. */
    TRIGGER_ORT_RUN,

    /** Permission to delete the [Product]. */
    DELETE;

    companion object {
        /** The prefix for the permission-related roles used for products. */
        private const val ROLE_PREFIX = "permission_product_"

        /**
         * Get all [role names][roleName] for the provided [productId].
         */
        fun getRolesForProduct(productId: Long) =
            enumValues<ProductPermission>().map { it.roleName(productId) }

        /**
         * A unique prefix for the roles for the provided [productId].
         */
        fun rolePrefix(productId: Long) = "$ROLE_PREFIX${productId}_"

        /**
         * Return the ID of the product this [roleName] belongs to or *null* if this role does not represent a
         * permission for a product.
         */
        fun extractProductIdFromRole(roleName: String): Long? =
            roleName.extractIdAfterPrefix(ROLE_PREFIX)
    }

    /** A unique name for this permission to be used to represent the permission as a role in Keycloak. */
    fun roleName(productId: Long): String = "${rolePrefix(productId)}${name.lowercase()}"
}
