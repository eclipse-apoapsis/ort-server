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
import org.ossreviewtoolkit.server.dao.tables.ProductDao
import org.ossreviewtoolkit.server.dao.tables.RepositoriesTable
import org.ossreviewtoolkit.server.dao.tables.RepositoryDao
import org.ossreviewtoolkit.server.shared.models.api.CreateRepository
import org.ossreviewtoolkit.server.shared.models.api.UpdateRepository

object RepositoriesRepository {
    /**
     * Retrieve all repositories for the [product][productId].
     */
    suspend fun listRepositoriesForProduct(productId: Long) = dbQuery {
        RepositoryDao.find { RepositoriesTable.product eq productId }.map { it.mapToEntity() }
    }.getOrDefault(emptyList())

    /**
     * Create a repository and assign it to the [product][productId].
     */
    suspend fun createRepository(productId: Long, createRepository: CreateRepository) = dbQuery {
        RepositoryDao.new {
            type = createRepository.type
            url = createRepository.url
            product = ProductDao[productId]
        }.mapToEntity()
    }.onFailure {
        if (it is ExposedSQLException) {
            when (it.sqlState) {
                PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value -> {
                    throw UniqueConstraintException(
                        "Failed to create repository for '${createRepository.url}', as a repository for this url " +
                                "already exists in the product '$productId'.",
                        it
                    )
                }
            }
        }

        throw it
    }.getOrThrow()

    /**
     * Retrieve a single repository by its [id]. Return null if no repository with the given [id] exists.
     */
    suspend fun getRepository(id: Long) = dbQuery {
        RepositoryDao[id].mapToEntity()
    }.getOrNull()

    /**
     * Update the values of a [repository][updateRepository] identified by [id].
     */
    suspend fun updateRepository(id: Long, updateRepository: UpdateRepository) = dbQuery {
        val repository = RepositoryDao[id]

        with(updateRepository) {
            type.ifPresent { repository.type = it }
            url.ifPresent { repository.url = it }
        }

        RepositoryDao[id].mapToEntity()
    }.onFailure {
        if (it is ExposedSQLException) {
            when (it.sqlState) {
                PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value -> {
                    throw UniqueConstraintException(
                        "Failed to update repository '$id', as a repository with the url '${updateRepository.url}' " +
                                "already exists.",
                        it
                    )
                }
            }
        }

        throw it
    }.getOrThrow()

    /**
     * Delete a repository with the given [id].
     */
    suspend fun deleteRepository(id: Long) = dbQuery {
        RepositoryDao[id].delete()
    }.getOrThrow()
}
