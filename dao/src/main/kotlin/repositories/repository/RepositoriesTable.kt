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

import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductDao
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID

/**
 * A table to represent a repository inside a product.
 */
object RepositoriesTable : SortableTable("repositories") {
    val productId = reference("product_id", ProductsTable)

    val type = text("type").sortable()
    val url = text("url").sortable()
    val description = text("description").nullable()
}

class RepositoryDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<RepositoryDao>(RepositoriesTable)

    var productId by RepositoriesTable.productId.transformToEntityId()
    var product by ProductDao referencedOn RepositoriesTable.productId

    var type by RepositoriesTable.type
    var url by RepositoriesTable.url
    var description by RepositoriesTable.description

    fun mapToModel() = Repository(
        id = id.value,
        organizationId = product.organization.id.value,
        productId = product.id.value,
        type = RepositoryType.forName(type),
        url = url,
        description = description
    )
}
