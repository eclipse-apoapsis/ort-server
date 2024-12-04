/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.repositories

import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

/**
 * A repository of [products][Product].
 */
interface ProductRepository {
    /**
     * Create a product.
     */
    fun create(name: String, description: String?, organizationId: Long): Product

    /**
     * Get a product by [id]. Returns null if the product is not found.
     */
    fun get(id: Long): Product?

    /**
     * List all products according to the given [parameters].
     */
    fun list(parameters: ListQueryParameters = ListQueryParameters.DEFAULT): List<Product>

    /**
     * List all products for an [organization][organizationId] according to the given [parameters].
     */
    fun listForOrganization(
        organizationId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<Product>

    /**
     * Count the products associated to an [organization][organizationId].
     */
    fun countForOrganization(organizationId: Long): Long

    /**
     * Update a product by [id] with the [present][OptionalValue.Present] values.
     */
    fun update(
        id: Long,
        name: OptionalValue<String> = OptionalValue.Absent,
        description: OptionalValue<String?> = OptionalValue.Absent
    ): Product

    /**
     * Delete a product by [id].
     */
    fun delete(id: Long)
}
