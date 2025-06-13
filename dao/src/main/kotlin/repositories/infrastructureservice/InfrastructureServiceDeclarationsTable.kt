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
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

/**
 * A table to store dynamic infrastructure services that are defined by environment file .ort.env.yml for each new
 * ORT run on a repository.
 */
object InfrastructureServiceDeclarationsTable : SortableTable("infrastructure_service_declarations") {
    val name = text("name").sortable()
    val url = text("url")
    val description = text("description").nullable()
    val credentialsType = text("credentials_type").nullable()

    val usernameSecret = text("username_secret")
    val passwordSecret = text("password_secret")
}

class InfrastructureServiceDeclarationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<InfrastructureServiceDeclarationDao>(
        InfrastructureServiceDeclarationsTable
    ) {
        /**
         * Try to find an entity with properties matching the ones of the given [service].
         */
        fun findByInfrastructureServiceDeclaration(service: InfrastructureServiceDeclaration):
                InfrastructureServiceDeclarationDao? =
            find {
                InfrastructureServiceDeclarationsTable.name eq service.name and
                        (InfrastructureServiceDeclarationsTable.url eq service.url) and
                        (InfrastructureServiceDeclarationsTable.description eq service.description) and
                        (InfrastructureServiceDeclarationsTable.usernameSecret eq service.usernameSecret) and
                        (InfrastructureServiceDeclarationsTable.passwordSecret eq service.passwordSecret) and
                        (
                                InfrastructureServiceDeclarationsTable.credentialsType eq
                                        toCredentialsTypeString(service.credentialsTypes)
                                )
            }.firstOrNull()

        /**
         * Return an entity with properties matching the ones of the given [service]. If no such entity exists yet, a
         * new one is created now.
         */
        fun getOrPut(service: InfrastructureServiceDeclaration): InfrastructureServiceDeclarationDao =
            findByInfrastructureServiceDeclaration(service) ?: new {
                name = service.name
                url = service.url
                description = service.description
                credentialsTypes = service.credentialsTypes
                usernameSecret = service.usernameSecret
                passwordSecret = service.passwordSecret
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

    var name by InfrastructureServiceDeclarationsTable.name
    var url by InfrastructureServiceDeclarationsTable.url
    var description by InfrastructureServiceDeclarationsTable.description

    var credentialsTypes by InfrastructureServiceDeclarationsTable.credentialsType.transform(
        { toCredentialsTypeString(it) },
        { fromCredentialsTypeString(it) }
    )

    var usernameSecret by InfrastructureServiceDeclarationsTable.usernameSecret
    var passwordSecret by InfrastructureServiceDeclarationsTable.passwordSecret

    fun mapToModel() = InfrastructureServiceDeclaration(
        name,
        url,
        description,
        usernameSecret,
        passwordSecret,
        credentialsTypes
    )
}
