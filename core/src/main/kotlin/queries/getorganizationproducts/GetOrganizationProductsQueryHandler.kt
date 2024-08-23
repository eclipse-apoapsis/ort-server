/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.queries.getorganizationproducts

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.core.queries.QueryHandler
import org.eclipse.apoapsis.ortserver.core.utils.toSortOrder
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductDao
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult

import org.jetbrains.exposed.sql.transactions.transaction

class GetOrganizationProductsQueryHandler : QueryHandler<GetOrganizationProductsQuery, ListQueryResult<Product>> {
    companion object {
        val SORTABLE_FIELDS = mapOf(
            "id" to ProductsTable.id,
            "name" to ProductsTable.name
        )
    }

    override suspend fun execute(query: GetOrganizationProductsQuery): Result<ListQueryResult<Product>> =
        runCatching {
            transaction {
                // TODO: Problem: How can we define which fields are sortable, fulfilling these conditions:
                // 1. We need the names for the API docs.
                // 2. We need the specific columns for the SQL query.
                var sqlQuery = ProductsTable.select(ProductsTable.columns)
                    .where { ProductsTable.organizationId eq query.organizationId }

                val total = sqlQuery.count()

                val orderColumns = query.pagingOptions.sortProperties.orEmpty().map {
                    val column = SORTABLE_FIELDS[it.name]
                        ?: throw IllegalArgumentException("Unknown sort field '${it.name}'.")

                    column to it.direction.toSortOrder()
                }

                if (orderColumns.isNotEmpty()) {
                    sqlQuery = sqlQuery.orderBy(*orderColumns.toTypedArray())
                }

                query.pagingOptions.limit?.let { sqlQuery = sqlQuery.limit(it) }
                query.pagingOptions.offset?.let { sqlQuery = sqlQuery.offset(it) }

                val products = sqlQuery.map { ProductDao.wrapRow(it).mapToModel() }

                ListQueryResult(products, query.pagingOptions.mapToModel(), total)
            }
        }
}
