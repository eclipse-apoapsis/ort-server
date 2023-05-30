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

enum class ProductPermission {
    /** Permission to read the [Product] details. */
    READ,

    /** Permission to write the [Product] details. */
    WRITE,

    /** Permission to write the [Product] secrets. */
    WRITE_SECRETS,

    /** Permission to read the list of [repositories][Repository] of the [Product]. */
    READ_REPOSITORIES,

    /** Permission to create a [Repository] for the [Product]. */
    CREATE_REPOSITORY,

    /** Permission to delete the [Product]. */
    DELETE;

    companion object {
        /**
         * Get all [role names][roleName] for the provided [productId].
         */
        fun getRolesForProduct(productId: Long) =
            enumValues<ProductPermission>().map { it.roleName(productId) }

        /**
         * A unique prefix for the roles for the provided [productId].
         */
        fun rolePrefix(productId: Long) = "permission_product_$productId"
    }

    /** A unique name for this permission to be used to represent the permission as a role in Keycloak. */
    fun roleName(productId: Long): String = "${rolePrefix(productId)}_${name.lowercase()}"
}
