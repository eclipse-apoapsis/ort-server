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

import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.Product

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID

/**
 * A product is a collection of repositories which are combined into one software product.
 */
object ProductsTable : SortableTable("products") {
    val organizationId = reference("organization_id", OrganizationsTable)

    val name = text("name").sortable()
    val description = text("description").nullable()
}

class ProductDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<ProductDao>(ProductsTable)

    var organizationId by ProductsTable.organizationId.transformToEntityId()
    var organization by OrganizationDao referencedOn ProductsTable.organizationId

    var name by ProductsTable.name
    var description by ProductsTable.description

    fun mapToModel() =
        Product(id = id.value, organizationId = organization.id.value, name = name, description = description)
}
