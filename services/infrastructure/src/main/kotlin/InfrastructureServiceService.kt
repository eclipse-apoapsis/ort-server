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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.sql.Database

/**
 * A service providing functionality for managing [infrastructure services][InfrastructureService].
 *
 * Infrastructure services can be manually assigned to organizations and products. On the repository level, they are
 * defined using a configuration file that is part of the repository and read during the analysis phase.
 */
class InfrastructureServiceService(
    /** Reference to the database. */
    private val db: Database,

    /** The repository for infrastructure services. */
    private val infrastructureServiceRepository: InfrastructureServiceRepository,

    /** The service to manage secrets. */
    private val secretService: SecretService
) {
    /**
     * Create an [InfrastructureService] with the given properties for the hierarchy entity [id].
     */
    suspend fun createForId(
        id: HierarchyId,
        name: String,
        url: String,
        description: String?,
        usernameSecretRef: String,
        passwordSecretRef: String,
        credentialsTypes: Set<CredentialsType>
    ): InfrastructureService {
        val usernameSecret = resolveSecret(id, usernameSecretRef)
        val passwordSecret = resolveSecret(id, passwordSecretRef)

        return db.dbQuery {
            infrastructureServiceRepository.create(
                name,
                url,
                description,
                usernameSecret,
                passwordSecret,
                credentialsTypes,
                id
            )
        }
    }

    /**
     * Update the [InfrastructureService] with the given [name] and hierarchy entity [id].
     */
    suspend fun updateForId(
        id: HierarchyId,
        name: String,
        url: OptionalValue<String>,
        description: OptionalValue<String?>,
        usernameSecretRef: OptionalValue<String>,
        passwordSecretRef: OptionalValue<String>,
        credentialsTypes: OptionalValue<Set<CredentialsType>>
    ): InfrastructureService {
        val usernameSecret = resolveSecretOptional(id, usernameSecretRef)
        val passwordSecret = resolveSecretOptional(id, passwordSecretRef)

        return db.dbQuery {
            infrastructureServiceRepository.updateForIdAndName(
                id,
                name,
                url,
                description,
                usernameSecret,
                passwordSecret,
                credentialsTypes
            )
        }
    }

    /**
     * Delete the [InfrastructureService] with the given [name] and hierarchy entity [id].
     */
    suspend fun deleteForId(id: HierarchyId, name: String) {
        db.dbQuery {
            infrastructureServiceRepository.deleteForIdAndName(id, name)
        }
    }

    /**
     * Return a list with [InfrastructureService]s assigned to the hierarchy entity [id], applying the provided
     * [parameters].
     */
    suspend fun listForId(
        id: HierarchyId,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<InfrastructureService> = db.dbQuery {
        infrastructureServiceRepository.listForId(id, parameters)
    }

    /**
     * Resolve a secret reference for the given hierarchy entity [id] and [secretName]. Throw an exception if the
     * reference cannot be resolved.
     */
    private suspend fun resolveSecret(id: HierarchyId, secretName: String): Secret =
        secretService.getSecretByIdAndName(id, secretName)
            ?: throw InvalidSecretReferenceException(secretName)

    /**
     * Resolve a secret reference in an [OptionalValue] for the given hierarchy entity [id]. Note that this cannot be
     * done through the [OptionalValue.map] function, because the transformation function is not a suspend function.
     */
    private suspend fun resolveSecretOptional(
        id: HierarchyId,
        secretName: OptionalValue<String>
    ): OptionalValue<Secret> =
        when (secretName) {
            is OptionalValue.Present -> resolveSecret(id, secretName.value).asPresent()
            else -> OptionalValue.Absent
        }
}

/**
 * An exception class that is thrown if the reference to a secret cannot be resolved.
 */
class InvalidSecretReferenceException(reference: String) :
    Exception("Could not resolve secret reference '$reference'.")
