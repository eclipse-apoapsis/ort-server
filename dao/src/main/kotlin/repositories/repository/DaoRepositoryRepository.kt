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

package org.eclipse.apoapsis.ortserver.dao.repositories.repository

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.applyIRegex
import org.eclipse.apoapsis.ortserver.dao.utils.extractIds
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere

class DaoRepositoryRepository(private val db: Database) : RepositoryRepository {
    override fun create(type: RepositoryType, url: String, productId: Long, description: String?) = db.blockingQuery {
        RepositoryDao.new {
            this.type = type.name
            this.url = url
            this.productId = productId
            this.description = description
        }.mapToModel()
    }

    override fun get(id: Long) = db.entityQuery { RepositoryDao[id].mapToModel() }

    override fun getHierarchy(id: Long): Hierarchy = db.blockingQuery {
        val repository = RepositoryDao[id]
        val product = repository.product
        val organization = product.organization

        Hierarchy(repository.mapToModel(), product.mapToModel(), organization.mapToModel())
    }

    override fun list(parameters: ListQueryParameters, urlFilter: FilterParameter?, hierarchyFilter: HierarchyFilter) =
        db.blockingQuery {
            val urlCondition = urlFilter?.let {
                RepositoriesTable.url.applyIRegex(it.value)
            } ?: Op.TRUE

            val builder = hierarchyFilter.apply(urlCondition) { level, ids, _ ->
                generateHierarchyCondition(level, ids)
            }

            RepositoryDao.listQuery(parameters, RepositoryDao::mapToModel, builder)
        }

    override fun listForProduct(productId: Long, parameters: ListQueryParameters, filter: FilterParameter?) =
        db.blockingQuery {
        RepositoryDao.listQuery(parameters, RepositoryDao::mapToModel) {
            if (filter !== null) {
                RepositoriesTable.productId eq productId and RepositoriesTable.url.applyIRegex(filter.value)
            } else {
                RepositoriesTable.productId eq productId
            }
        }
    }

    override fun update(
        id: Long,
        type: OptionalValue<RepositoryType>,
        url: OptionalValue<String>,
        description: OptionalValue<String?>
    ) = db.blockingQuery {
        val repository = RepositoryDao[id]

        type.ifPresent { repository.type = it.name }
        url.ifPresent { repository.url = it }
        description.ifPresent { repository.description = it }

        RepositoryDao[id].mapToModel()
    }

    override fun delete(id: Long) = db.blockingQuery { RepositoryDao[id].delete() }

    override fun deleteByProduct(productId: Long): Int = db.blockingQuery {
        RepositoriesTable.deleteWhere { RepositoriesTable.productId eq productId }
    }
}

/**
 * Generate a condition defined by a [HierarchyFilter] for the given [level] and [ids].
 */
private fun SqlExpressionBuilder.generateHierarchyCondition(
    level: HierarchyLevel,
    ids: List<CompoundHierarchyId>
): Op<Boolean> =
    when (level) {
        HierarchyLevel.REPOSITORY ->
            RepositoriesTable.id inList ids.extractIds(HierarchyLevel.REPOSITORY)

        HierarchyLevel.PRODUCT ->
            RepositoriesTable.productId inList ids.extractIds(HierarchyLevel.PRODUCT)

        HierarchyLevel.ORGANIZATION -> {
            val subquery = ProductsTable.select(ProductsTable.id).where {
                ProductsTable.organizationId inList ids.extractIds(HierarchyLevel.ORGANIZATION)
            }
            RepositoriesTable.productId inSubQuery subquery
        }

        else -> Op.FALSE
    }
