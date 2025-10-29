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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ProductRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.max

/**
 * A service providing functions for working with [products][Product].
 */
class ProductService(
    private val db: Database,
    private val productRepository: ProductRepository,
    private val repositoryRepository: RepositoryRepository,
    private val ortRunRepository: OrtRunRepository,
) {
    /**
     * Create a repository inside a [product][productId].
     */
    suspend fun createRepository(
        type: RepositoryType,
        url: String,
        productId: Long,
        description: String?
    ): Repository = db.dbQuery {
        repositoryRepository.create(type, url, productId, description)
    }

    /**
     * Delete a [product][productId] with its [repositories][Repository] and [OrtRun]s.
     */
    suspend fun deleteProduct(productId: Long): Unit = db.dbQuery {
        ortRunRepository.deleteByProduct(productId)
        repositoryRepository.deleteByProduct(productId)

        productRepository.delete(productId)
    }

    /**
     * Get a product by [productId]. Returns null if the product is not found.
     */
    suspend fun getProduct(productId: Long): Product? = db.dbQuery {
        productRepository.get(productId)
    }

    /**
     * List all repositories for a [product][productId] according to the given [parameters].
     */
    suspend fun listRepositoriesForProduct(
        productId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filter: FilterParameter? = null
    ): ListQueryResult<Repository> = db.dbQuery {
        repositoryRepository.listForProduct(productId, parameters, filter)
    }

    /**
     * Update a product by [productId] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateProduct(
        productId: Long,
        name: OptionalValue<String> = OptionalValue.Absent,
        description: OptionalValue<String?> = OptionalValue.Absent
    ): Product = db.dbQuery {
        productRepository.update(productId, name, description)
    }

    suspend fun getRepositoryIdsForProduct(productId: Long): List<Long> = db.dbQuery {
        RepositoriesTable
            .select(RepositoriesTable.id)
            .where { RepositoriesTable.productId eq productId }
            .map { it[RepositoriesTable.id].value }
    }

    /**
     * Get the ID of the latest ORT run of the repository where the status is failed.
     **/
    suspend fun getLatestOrtRunWithFailedStatusForProduct(productId: Long): List<Long> = db.dbQuery {
        RepositoriesTable
            .innerJoin(OrtRunsTable)
            .select(
                RepositoriesTable.id,
            )
            .where {
                (RepositoriesTable.productId eq productId) and
                        (
                          OrtRunsTable.index eqSubQuery OrtRunsTable
                            .select(OrtRunsTable.index.max())
                            .where { OrtRunsTable.repositoryId eq RepositoriesTable.id }
                                and (OrtRunsTable.status eq OrtRunStatus.FAILED)
                                )
            }
            .orderBy(OrtRunsTable.index, SortOrder.DESC)
            .map { it[RepositoriesTable.id].value }
    }
}
