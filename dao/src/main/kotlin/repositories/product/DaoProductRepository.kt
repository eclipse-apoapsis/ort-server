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

package org.eclipse.apoapsis.ortserver.dao.repositories.product

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.utils.applyRegex
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.repositories.ProductRepository
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and

class DaoProductRepository(private val db: Database) : ProductRepository {
    override fun create(name: String, description: String?, organizationId: Long) = db.blockingQuery {
        ProductDao.new {
            this.name = name
            this.description = description
            this.organizationId = organizationId
        }.mapToModel()
    }

    override fun get(id: Long) = db.entityQuery { ProductDao[id].mapToModel() }

    override fun list(parameters: ListQueryParameters, filter: FilterParameter?) =
        db.blockingQuery {
            ProductDao.listQuery(parameters, ProductDao::mapToModel) {
                var condition: Op<Boolean> = Op.TRUE
                filter?.let {
                    condition = condition and ProductsTable.name.applyRegex(
                        it.value
                    )
                }
                condition
            }
        }

    override fun countForOrganization(organizationId: Long) =
        ProductDao.count(ProductsTable.organizationId eq organizationId)

    override fun listForOrganization(organizationId: Long, parameters: ListQueryParameters, filter: FilterParameter?) =
        db.blockingQuery {
        ProductDao.listQuery(parameters, ProductDao::mapToModel) {
            if (filter != null) {
                ProductsTable.organizationId eq organizationId and ProductsTable.name.applyRegex(filter.value)
            } else {
                ProductsTable.organizationId eq organizationId
            }
        }
    }

    override fun update(id: Long, name: OptionalValue<String>, description: OptionalValue<String?>) = db.blockingQuery {
        val product = ProductDao[id]

        name.ifPresent { product.name = it }
        description.ifPresent { product.description = it }

        ProductDao[id].mapToModel()
    }

    override fun delete(id: Long) = db.blockingQuery { ProductDao[id].delete() }
}
