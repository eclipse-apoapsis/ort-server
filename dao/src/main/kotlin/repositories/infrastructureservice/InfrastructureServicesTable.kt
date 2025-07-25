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

package org.eclipse.apoapsis.ortserver.dao.repositories.infrastructureservice

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductDao
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoryDao
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.SecretDao
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.SecretsTable
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID

/**
 * A table to store infrastructure services, such as source code or artifact repositories.
 */
object InfrastructureServicesTable : SortableTable("infrastructure_services") {
    val name = text("name").sortable()
    val url = text("url")
    val description = text("description").nullable()
    val credentialsType = text("credentials_type").nullable()

    val usernameSecretId = reference("username_secret_id", SecretsTable)
    val passwordSecretId = reference("password_secret_id", SecretsTable)

    val organizationId = reference("organization_id", OrganizationsTable).nullable()
    val productId = reference("product_id", ProductsTable).nullable()
    val repositoryId = reference("repository_id", RepositoriesTable).nullable()
}

class InfrastructureServicesDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<InfrastructureServicesDao>(InfrastructureServicesTable) {
        /**
         * Convert a set of [CredentialsType]s to a string representation.
         */
        private fun toCredentialsTypeString(types: Set<CredentialsType>): String? =
            types.takeUnless { it.isEmpty() }?.toSortedSet()?.joinToString(",") { it.name }

        /**
         * Return a set of [CredentialsType]s from a string representation.
         */
        private fun fromCredentialsTypeString(typeString: String?): Set<CredentialsType> =
            typeString?.split(",")
                ?.mapTo(EnumSet.noneOf(CredentialsType::class.java)) { CredentialsType.valueOf(it) }.orEmpty()
    }

    var name by InfrastructureServicesTable.name
    var url by InfrastructureServicesTable.url
    var description by InfrastructureServicesTable.description

    var credentialsTypes by InfrastructureServicesTable.credentialsType.transform(
        { toCredentialsTypeString(it) },
        { fromCredentialsTypeString(it) }
    )

    var usernameSecretId by InfrastructureServicesTable.usernameSecretId.transformToEntityId()
    var usernameSecret by SecretDao referencedOn InfrastructureServicesTable.usernameSecretId
    var passwordSecretId by InfrastructureServicesTable.passwordSecretId.transformToEntityId()
    var passwordSecret by SecretDao referencedOn InfrastructureServicesTable.passwordSecretId

    var organizationId by InfrastructureServicesTable.organizationId.transformToEntityId()
    var organization by OrganizationDao optionalReferencedOn InfrastructureServicesTable.organizationId
    var productId by InfrastructureServicesTable.productId.transformToEntityId()
    var product by ProductDao optionalReferencedOn InfrastructureServicesTable.productId
    var repositoryId by InfrastructureServicesTable.repositoryId.transformToEntityId()
    var repository by RepositoryDao optionalReferencedOn InfrastructureServicesTable.repositoryId

    fun mapToModel() = InfrastructureService(
        name,
        url,
        description,
        usernameSecret.mapToModel(),
        passwordSecret.mapToModel(),
        organization?.mapToModel(),
        product?.mapToModel(),
        repository?.mapToModel(),
        credentialsTypes
    )
}
