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
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.OrganizationId
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
     * Create an [InfrastructureService] with the given properties and assign it to the organization with the given
     * [organizationId].
     */
    suspend fun createForOrganization(
        organizationId: Long,
        name: String,
        url: String,
        description: String?,
        usernameSecretRef: String,
        passwordSecretRef: String,
        credentialsTypes: Set<CredentialsType>
    ): InfrastructureService {
        val usernameSecret = resolveOrganizationSecret(organizationId, usernameSecretRef)
        val passwordSecret = resolveOrganizationSecret(organizationId, passwordSecretRef)

        return db.dbQuery {
            infrastructureServiceRepository.create(
                name,
                url,
                description,
                usernameSecret,
                passwordSecret,
                credentialsTypes,
                organizationId,
                null
            )
        }
    }

    /**
     * Update the [InfrastructureService] with the given [name] assigned to the organization with the given
     * [organizationId] based on the provided properties.
     */
    suspend fun updateForOrganization(
        organizationId: Long,
        name: String,
        url: OptionalValue<String>,
        description: OptionalValue<String?>,
        usernameSecretRef: OptionalValue<String>,
        passwordSecretRef: OptionalValue<String>,
        credentialsTypes: OptionalValue<Set<CredentialsType>>
    ): InfrastructureService {
        val usernameSecret = resolveOrganizationSecretOptional(organizationId, usernameSecretRef)
        val passwordSecret = resolveOrganizationSecretOptional(organizationId, passwordSecretRef)

        return db.dbQuery {
            infrastructureServiceRepository.updateForOrganizationAndName(
                organizationId,
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
     * Delete the [InfrastructureService] with the given [name] that is assigned to the organization with the given
     * [organizationId].
     */
    suspend fun deleteForOrganization(organizationId: Long, name: String) {
        db.dbQuery {
            infrastructureServiceRepository.deleteForOrganizationAndName(organizationId, name)
        }
    }

    /**
     * Return a list with the [InfrastructureService]s assigned to the organization with the given [organizationId]
     * according to the provided [parameters].
     */
    suspend fun listForOrganization(
        organizationId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<InfrastructureService> = db.dbQuery {
        infrastructureServiceRepository.listForOrganization(organizationId, parameters)
    }

    /**
     * Resolve a secret reference for the given [organizationId] and [secretName]. Throw an exception if the
     * reference cannot be resolved.
     */
    private suspend fun resolveOrganizationSecret(organizationId: Long, secretName: String): Secret =
        secretService.getSecretByIdAndName(OrganizationId(organizationId), secretName)
            ?: throw InvalidSecretReferenceException(secretName)

    /**
     * Resolve a secret reference in an [OptionalValue] for the given [organizationId]. Note that this cannot be
     * done through the [OptionalValue.map] function, because the transformation function is not a suspend function.
     */
    private suspend fun resolveOrganizationSecretOptional(
        organizationId: Long,
        secretName: OptionalValue<String>
    ): OptionalValue<Secret> =
        when (secretName) {
            is OptionalValue.Present -> resolveOrganizationSecret(organizationId, secretName.value).asPresent()
            else -> OptionalValue.Absent
        }
}

/**
 * An exception class that is thrown if the reference to a secret cannot be resolved.
 */
class InvalidSecretReferenceException(reference: String) :
    Exception("Could not resolve secret reference '$reference'.")
