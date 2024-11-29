/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.secret

import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductDao
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoryDao
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.Secret

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID

object SecretsTable : SortableTable("secrets") {
    val organizationId = reference("organization_id", OrganizationsTable).nullable()
    val productId = reference("product_id", ProductsTable).nullable()
    val repositoryId = reference("repository_id", RepositoriesTable).nullable()

    val path = text("path")
    val name = text("name").sortable()
    val description = text("description").nullable()
}

class SecretDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<SecretDao>(SecretsTable)

    var organizationId by SecretsTable.organizationId.transformToEntityId()
    var organization by OrganizationDao optionalReferencedOn SecretsTable.organizationId
    var productId by SecretsTable.productId.transformToEntityId()
    var product by ProductDao optionalReferencedOn SecretsTable.productId
    var repositoryId by SecretsTable.repositoryId.transformToEntityId()
    var repository by RepositoryDao optionalReferencedOn SecretsTable.repositoryId

    var path by SecretsTable.path
    var name by SecretsTable.name
    var description by SecretsTable.description

    fun mapToModel() = Secret(
        id = id.value,
        path = path,
        name = name,
        description = description,
        organization = organization?.mapToModel(),
        product = product?.mapToModel(),
        repository = repository?.mapToModel()
    )
}
