/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.repositories

import org.jetbrains.exposed.exceptions.ExposedSQLException

import org.ossreviewtoolkit.server.dao.PostgresErrorCodes
import org.ossreviewtoolkit.server.dao.UniqueConstraintException
import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.dao.tables.OrganizationDao
import org.ossreviewtoolkit.server.dao.tables.ProductDao
import org.ossreviewtoolkit.server.dao.tables.ProductsTable
import org.ossreviewtoolkit.server.shared.models.api.CreateProduct
import org.ossreviewtoolkit.server.shared.models.api.UpdateProduct

object ProductsRepository {
    /**
     * Create a product and assign it to the [organization][orgId].
     */
    suspend fun createProduct(orgId: Long, createProduct: CreateProduct) = dbQuery {
        ProductDao.new {
            name = createProduct.name
            description = createProduct.description
            organization = OrganizationDao[orgId]
        }.mapToEntity()
    }.onFailure {
        if (it is ExposedSQLException) {
            when (it.sqlState) {
                PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value -> {
                    throw UniqueConstraintException(
                        "Failed to create product '${createProduct.name}', as a product with this name already " +
                                "exists in the organization '$orgId'.",
                        it
                    )
                }
            }
        }

        throw it
    }.getOrThrow()

    /**
     * Retrieve a single product by its [id]. Returns null if no product with this [id] exists.
     */
    suspend fun getProduct(id: Long) = dbQuery {
        ProductDao[id].mapToEntity()
    }.getOrNull()

    /**
     * Retrieve all products for the [organization][orgId].
     */
    suspend fun listProductsForOrg(orgId: Long) = dbQuery {
        ProductDao.find { ProductsTable.organization eq orgId }.map { it.mapToEntity() }
    }.getOrDefault(emptyList())

    /**
     * Update the values of a [product][updateProduct] identified by [id].
     */
    suspend fun updateProduct(id: Long, updateProduct: UpdateProduct) = dbQuery {
        val product = ProductDao[id]

        updateProduct.name.ifPresent { product.name = it }
        updateProduct.description.ifPresent { product.description = it }

        ProductDao[id].mapToEntity()
    }.getOrThrow()

    /**
     * Delete a product.
     */
    suspend fun deleteProduct(id: Long) = dbQuery {
        ProductDao[id].delete()
    }.getOrThrow()
}
