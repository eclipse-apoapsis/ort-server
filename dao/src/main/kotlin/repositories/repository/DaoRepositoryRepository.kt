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
import org.eclipse.apoapsis.ortserver.dao.tables.ProductDao
import org.eclipse.apoapsis.ortserver.dao.tables.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.RepositoryDao
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database

class DaoRepositoryRepository(private val db: Database) : RepositoryRepository {
    override fun create(type: RepositoryType, url: String, productId: Long) = db.blockingQuery {
        RepositoryDao.new {
            this.type = type.name
            this.url = url
            product = ProductDao[productId]
        }.mapToModel()
    }

    override fun get(id: Long) = db.entityQuery { RepositoryDao[id].mapToModel() }

    override fun getHierarchy(id: Long): Hierarchy = db.blockingQuery {
        val repository = RepositoryDao[id]
        val product = repository.product
        val organization = product.organization

        Hierarchy(repository.mapToModel(), product.mapToModel(), organization.mapToModel())
    }

    override fun list(parameters: ListQueryParameters) =
        db.blockingQuery { RepositoryDao.list(parameters).map { it.mapToModel() } }

    override fun listForProduct(productId: Long, parameters: ListQueryParameters) = db.blockingQuery {
        RepositoryDao.listQuery(parameters, RepositoryDao::mapToModel) { RepositoriesTable.productId eq productId }
    }

    override fun update(id: Long, type: OptionalValue<RepositoryType>, url: OptionalValue<String>) = db.blockingQuery {
        val repository = RepositoryDao[id]

        type.ifPresent { repository.type = it.name }
        url.ifPresent { repository.url = it }

        RepositoryDao[id].mapToModel()
    }

    override fun delete(id: Long) = db.blockingQuery { RepositoryDao[id].delete() }
}
