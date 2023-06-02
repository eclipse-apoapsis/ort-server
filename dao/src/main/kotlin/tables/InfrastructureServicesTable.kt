/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.dao.utils.SortableEntityClass
import org.ossreviewtoolkit.server.dao.utils.SortableTable
import org.ossreviewtoolkit.server.model.InfrastructureService

/**
 * A table to store infrastructure services, such as source code or artifact repositories.
 */
object InfrastructureServicesTable : SortableTable("infrastructure_services") {
    val name = text("name").sortable()
    val url = text("url")
    val description = text("description").nullable()

    val usernameSecretId = reference("username_secret_id", SecretsTable)
    val passwordSecretId = reference("password_secret_id", SecretsTable)

    val organizationId = reference("organization_id", OrganizationsTable).nullable()
    val productId = reference("product_id", ProductsTable).nullable()
}

class InfrastructureServicesDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<InfrastructureServicesDao>(InfrastructureServicesTable) {
        /**
         * Try to find an entity with properties matching the ones of the given [service].
         */
        fun findByInfrastructureService(service: InfrastructureService): InfrastructureServicesDao? =
            find {
                InfrastructureServicesTable.name eq service.name and
                        (InfrastructureServicesTable.url eq service.url) and
                        (InfrastructureServicesTable.description eq service.description) and
                        (InfrastructureServicesTable.usernameSecretId eq service.usernameSecret.id) and
                        (InfrastructureServicesTable.passwordSecretId eq service.passwordSecret.id) and
                        (InfrastructureServicesTable.organizationId eq service.organization?.id) and
                        (InfrastructureServicesTable.productId eq service.product?.id)
            }.singleOrNull()

        /**
         * Return an entity with properties matching the ones of the given [service]. If no such entity exists yet, a
         * new one is created now.
         */
        fun getOrPut(service: InfrastructureService): InfrastructureServicesDao =
            findByInfrastructureService(service) ?: new {
                name = service.name
                url = service.url
                description = service.description
                usernameSecret = SecretDao[service.usernameSecret.id]
                passwordSecret = SecretDao[service.passwordSecret.id]
            }
    }

    var name by InfrastructureServicesTable.name
    var url by InfrastructureServicesTable.url
    var description by InfrastructureServicesTable.description

    var usernameSecret by SecretDao referencedOn InfrastructureServicesTable.usernameSecretId
    var passwordSecret by SecretDao referencedOn InfrastructureServicesTable.passwordSecretId

    var organization by OrganizationDao optionalReferencedOn InfrastructureServicesTable.organizationId
    var product by ProductDao optionalReferencedOn InfrastructureServicesTable.productId

    fun mapToModel() = InfrastructureService(
        name,
        url,
        description,
        usernameSecret.mapToModel(),
        passwordSecret.mapToModel(),
        organization?.mapToModel(),
        product?.mapToModel()
    )
}
