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
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.ProductDao
import org.ossreviewtoolkit.server.dao.tables.RepositoriesTable
import org.ossreviewtoolkit.server.dao.tables.RepositoryDao
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue

class DaoRepositoryRepository : RepositoryRepository {
    override fun create(type: RepositoryType, url: String, productId: Long) = blockingQuery {
        RepositoryDao.new {
            this.type = type
            this.url = url
            product = ProductDao[productId]
        }.mapToModel()
    }.onFailure {
        if (it is ExposedSQLException) {
            when (it.sqlState) {
                PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value -> {
                    throw UniqueConstraintException(
                        "Failed to create repository for '$url', as a repository for this url already exists in the " +
                                "product '$productId'.",
                        it
                    )
                }
            }
        }

        throw it
    }.getOrThrow()

    override fun get(id: Long) = blockingQuery { RepositoryDao[id].mapToModel() }.getOrNull()

    override fun listForProduct(productId: Long) = blockingQuery {
        RepositoryDao.find { RepositoriesTable.product eq productId }.map { it.mapToModel() }
    }.getOrDefault(emptyList())

    override fun update(id: Long, type: OptionalValue<RepositoryType>, url: OptionalValue<String>) = blockingQuery {
        val repository = RepositoryDao[id]

        type.ifPresent { repository.type = it }
        url.ifPresent { repository.url = it }

        RepositoryDao[id].mapToModel()
    }.onFailure {
        if (it is ExposedSQLException) {
            when (it.sqlState) {
                PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value -> {
                    throw UniqueConstraintException(
                        "Failed to update repository '$id', as a repository with the url '$url' already exists.",
                        it
                    )
                }
            }
        }

        throw it
    }.getOrThrow()

    override fun delete(id: Long) = blockingQuery { RepositoryDao[id].delete() }.getOrThrow()
}
