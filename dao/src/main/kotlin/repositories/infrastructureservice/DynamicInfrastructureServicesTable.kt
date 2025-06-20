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

import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.DynamicInfrastructureService

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

/**
 * A table to store dynamic infrastructure services that are defined by environment file .ort.env.yml for each new
 * ORT run on a repository.
 */
object DynamicInfrastructureServicesTable : SortableTable("dynamic_infrastructure_services") {
    val name = text("name").sortable()
    val url = text("url")
    val credentialsType = text("credentials_type").nullable()

    val usernameSecretName = text("username_secret_name")
    val passwordSecretName = text("password_secret_name")
}

class DynamicInfrastructureServicesDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<DynamicInfrastructureServicesDao>(DynamicInfrastructureServicesTable) {
        /**
         * Try to find an entity with properties matching the ones of the given [service].
         */
        fun findByInfrastructureService(service: DynamicInfrastructureService): DynamicInfrastructureServicesDao? =
            find {
                DynamicInfrastructureServicesTable.name eq service.name and
                        (DynamicInfrastructureServicesTable.url eq service.url) and
                        (DynamicInfrastructureServicesTable.usernameSecretName eq service.usernameSecretName) and
                        (DynamicInfrastructureServicesTable.passwordSecretName eq service.passwordSecretName) and
                        (
                                DynamicInfrastructureServicesTable.credentialsType eq
                                        toCredentialsTypeString(service.credentialsTypes)
                                )
            }.firstOrNull()

        /**
         * Return an entity with properties matching the ones of the given [service]. If no such entity exists yet, a
         * new one is created now.
         */
        fun getOrPut(service: DynamicInfrastructureService): DynamicInfrastructureServicesDao =
            findByInfrastructureService(service) ?: new {
                name = service.name
                url = service.url
                credentialsTypes = service.credentialsTypes
                usernameSecretName = service.usernameSecretName
                passwordSecretName = service.passwordSecretName
            }

        /**
         * Convert a set of [CredentialsType]s to a string representation.
         */
        private fun toCredentialsTypeString(types: Set<CredentialsType>): String? =
            types.takeUnless { it.isEmpty() }?.toSortedSet()?.joinToString(",") { it.name }

        /**
         * Return a set of [CredentialsType]s from a string representation.
         */
        fun fromCredentialsTypeString(typeString: String?): Set<CredentialsType> =
            typeString?.split(",")
                ?.mapTo(EnumSet.noneOf(CredentialsType::class.java)) { CredentialsType.valueOf(it) }.orEmpty()
    }

    var name by DynamicInfrastructureServicesTable.name
    var url by DynamicInfrastructureServicesTable.url

    var credentialsTypes by DynamicInfrastructureServicesTable.credentialsType.transform(
        { toCredentialsTypeString(it) },
        { fromCredentialsTypeString(it) }
    )

    var usernameSecretName by DynamicInfrastructureServicesTable.usernameSecretName
    var passwordSecretName by DynamicInfrastructureServicesTable.passwordSecretName

    fun mapToModel() = DynamicInfrastructureService(
        name,
        url,
        usernameSecretName,
        passwordSecretName,
        credentialsTypes
    )
}
