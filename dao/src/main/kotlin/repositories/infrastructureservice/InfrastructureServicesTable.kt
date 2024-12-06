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
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.SecretDao
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.SecretsTable
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

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
                        (
                                InfrastructureServicesTable.credentialsType eq
                                    toCredentialsTypeString(service.credentialsTypes)
                                ) and
                        (InfrastructureServicesTable.organizationId eq service.organization?.id) and
                        (InfrastructureServicesTable.productId eq service.product?.id)
            }.firstOrNull()

        /**
         * Return an entity with properties matching the ones of the given [service]. If no such entity exists yet, a
         * new one is created now.
         */
        fun getOrPut(service: InfrastructureService): InfrastructureServicesDao =
            findByInfrastructureService(service) ?: new {
                name = service.name
                url = service.url
                description = service.description
                credentialsTypes = service.credentialsTypes
                usernameSecret = SecretDao[service.usernameSecret.id]
                passwordSecret = SecretDao[service.passwordSecret.id]
            }

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
        product?.mapToModel(),
        credentialsTypes
    )
}
