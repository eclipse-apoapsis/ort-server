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

import org.eclipse.apoapsis.ortserver.dao.ConditionBuilder
import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.findSingle
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.SecretDao
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

class DaoInfrastructureServiceRepository(private val db: Database) : InfrastructureServiceRepository {
    companion object {
        /**
         * Return an expression to select an [InfrastructureService] for a specific hierarchy entity [id]
         * with a given [name].
         */
        private fun selectByIdAndName(
            id: HierarchyId,
            name: String
        ): ConditionBuilder = {
            when (id) {
                is OrganizationId -> InfrastructureServicesTable.organizationId eq id.value
                is ProductId -> InfrastructureServicesTable.productId eq id.value
                is RepositoryId -> InfrastructureServicesTable.repositoryId eq id.value
            }.and {
                InfrastructureServicesTable.name eq name
            }
        }
    }

    override fun create(
        name: String,
        url: String,
        description: String?,
        usernameSecret: Secret,
        passwordSecret: Secret,
        credentialsTypes: Set<CredentialsType>,
        id: HierarchyId
    ): InfrastructureService = db.blockingQuery {
        InfrastructureServicesDao.find(selectByIdAndName(id, name)).let {
            if (it.count() > 0) {
                throw UniqueConstraintException(
                    "An infrastructure service with name '$name' already exists for the given hierarchy entity."
                )
            }
        }

        InfrastructureServicesDao.new {
            this.name = name
            this.url = url
            this.description = description
            this.usernameSecretId = usernameSecret.id
            this.passwordSecretId = passwordSecret.id
            this.credentialsTypes = credentialsTypes
            this.organizationId = (id as? OrganizationId)?.value
            this.productId = (id as? ProductId)?.value
            this.repositoryId = (id as? RepositoryId)?.value
        }.mapToModel()
    }

    override fun listForId(
        id: HierarchyId,
        parameters: ListQueryParameters
    ): ListQueryResult<InfrastructureService> = db.blockingQuery {
        InfrastructureServicesDao.listQuery(parameters, InfrastructureServicesDao::mapToModel) {
            (InfrastructureServicesTable.organizationId eq (id as? OrganizationId)?.value) and
                    (InfrastructureServicesTable.productId eq (id as? ProductId)?.value) and
                    (InfrastructureServicesTable.repositoryId eq (id as? RepositoryId)?.value)
        }
    }

    override fun getByIdAndName(id: HierarchyId, name: String): InfrastructureService? =
        listBlocking(ListQueryParameters.DEFAULT, selectByIdAndName(id, name)).singleOrNull()

    override fun updateForIdAndName(
        id: HierarchyId,
        name: String,
        url: OptionalValue<String>,
        description: OptionalValue<String?>,
        usernameSecret: OptionalValue<Secret>,
        passwordSecret: OptionalValue<Secret>,
        credentialsTypes: OptionalValue<Set<CredentialsType>>
    ): InfrastructureService =
        update(
            selectByIdAndName(id, name),
            url,
            description,
            usernameSecret,
            passwordSecret,
            credentialsTypes
        )

    override fun deleteForIdAndName(id: HierarchyId, name: String) {
        delete(selectByIdAndName(id, name))
    }

    override fun listForHierarchy(
        hierarchy: Hierarchy
    ): List<InfrastructureService> = db.blockingQuery {
        list(ListQueryParameters.DEFAULT) {
            (InfrastructureServicesTable.repositoryId eq hierarchy.repository.id) or
                    (InfrastructureServicesTable.productId eq hierarchy.product.id) or
                    (InfrastructureServicesTable.organizationId eq hierarchy.organization.id)
        }.groupBy(InfrastructureService::url)
            .flatMap { (_, services) ->
                // For duplicates, prefer services defined for products over those for organizations
                listOfNotNull(
                    services.find { it.repository?.id == hierarchy.repository.id }
                        ?: services.find { it.product?.id == hierarchy.product.id }
                        ?: services.find { it.organization?.id == hierarchy.organization.id }
                )
            }
    }

    override fun listForSecret(secretId: Long): List<InfrastructureService> =
        listBlocking(ListQueryParameters.DEFAULT) {
            InfrastructureServicesTable.usernameSecretId eq secretId or
                    (InfrastructureServicesTable.passwordSecretId eq secretId)
        }

    /**
     * Helper function to list all services that match a specific [expression][op] according to the given [parameters]
     * in a blocking query.
     */
    private fun listBlocking(
        parameters: ListQueryParameters,
        op: ConditionBuilder
    ): List<InfrastructureService> = db.blockingQuery {
        list(parameters, op)
    }

    /**
     * Helper function to list all services that match a specific [expression][op] according to the given [parameters].
     */
    private fun list(
        parameters: ListQueryParameters,
        op: ConditionBuilder
    ) = InfrastructureServicesDao.find(op)
        .apply(InfrastructureServicesTable, parameters)
        .map(InfrastructureServicesDao::mapToModel)

    /**
     * Update an entity selected by [op] based on the provided properties. Throw an exception if the entity cannot be
     * found.
     */
    private fun update(
        op: ConditionBuilder,
        url: OptionalValue<String>,
        description: OptionalValue<String?>,
        usernameSecret: OptionalValue<Secret>,
        passwordSecret: OptionalValue<Secret>,
        credentialsTypes: OptionalValue<Set<CredentialsType>>
    ): InfrastructureService = db.blockingQuery {
        val service = InfrastructureServicesDao.findSingle(op)

        url.ifPresent { service.url = it }
        description.ifPresent { service.description = it }
        usernameSecret.ifPresent { service.usernameSecret = SecretDao[it.id] }
        passwordSecret.ifPresent { service.passwordSecret = SecretDao[it.id] }
        credentialsTypes.ifPresent { service.credentialsTypes = it }

        service.mapToModel()
    }

    /**
     * Delete an entity selected by [op] or throw an exception if this entity cannot be found.
     */
    private fun delete(op: ConditionBuilder) = db.blockingQuery {
        InfrastructureServicesDao.findSingle(op).delete()
    }
}
