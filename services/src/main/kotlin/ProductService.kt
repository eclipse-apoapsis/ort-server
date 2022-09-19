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

package org.ossreviewtoolkit.server.services

import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue

/**
 * A service providing functions for working with [products][Product].
 */
class ProductService(
    private val productRepository: ProductRepository,
    private val repositoryRepository: RepositoryRepository
) {
    /**
     * Create a repository inside a [product][productId].
     */
    suspend fun createRepository(type: RepositoryType, url: String, productId: Long): Repository = dbQuery {
        repositoryRepository.create(type, url, productId)
    }.getOrThrow()

    /**
     * Delete a product by [productId].
     */
    suspend fun deleteProduct(productId: Long): Unit = dbQuery {
        productRepository.delete(productId)
    }.getOrThrow()

    /**
     * Get a product by [productId]. Returns null if the product is not found.
     */
    suspend fun getProduct(productId: Long): Product? = dbQuery {
        productRepository.get(productId)
    }.getOrNull()

    /**
     * List all repositories for a [product][productId].
     */
    suspend fun listRepositoriesForProduct(productId: Long): List<Repository> = dbQuery {
        repositoryRepository.listForProduct(productId)
    }.getOrThrow()

    /**
     * Update a product by [productId] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateProduct(
        productId: Long,
        name: OptionalValue<String> = OptionalValue.Absent,
        description: OptionalValue<String?> = OptionalValue.Absent
    ): Product =
        dbQuery {
            productRepository.update(productId, name, description)
        }.getOrThrow()
}
