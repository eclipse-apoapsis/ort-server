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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceDeclarationsRunsTable.infrastructureServiceDeclarationId
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceDeclarationsRunsTable.ortRunId
import org.eclipse.apoapsis.ortserver.dao.ConditionBuilder
import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.findSingle
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

/**
 * A service providing functionality for managing [infrastructure services][InfrastructureService].
 *
 * Infrastructure services can be manually assigned to organizations and products. On the repository level, they are
 * defined using a configuration file that is part of the repository and read during the analysis phase.
 */
class InfrastructureServiceService(
    /** Reference to the database. */
    private val db: Database
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
        return db.dbQuery {
            if (getDaoForId(id, name) != null) {
                throw UniqueConstraintException(
                    "An infrastructure service with name '$name' already exists for the given hierarchy entity."
                )
            }

            InfrastructureServicesDao.new {
                this.name = name
                this.url = url
                this.description = description
                this.usernameSecret = usernameSecretRef
                this.passwordSecret = passwordSecretRef
                this.credentialsTypes = credentialsTypes
                this.organizationId = (id as? OrganizationId)?.value
                this.productId = (id as? ProductId)?.value
                this.repositoryId = (id as? RepositoryId)?.value
            }.mapToModel()
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
        return db.dbQuery {
            val service = InfrastructureServicesDao.findSingle(selectByIdAndName(id, name))

            url.ifPresent { service.url = it }
            description.ifPresent { service.description = it }
            usernameSecretRef.ifPresent { service.usernameSecret = it }
            passwordSecretRef.ifPresent { service.passwordSecret = it }
            credentialsTypes.ifPresent { service.credentialsTypes = it }

            service.mapToModel()
        }
    }

    /**
     * Delete the [InfrastructureService] with the given [name] and hierarchy entity [id].
     */
    suspend fun deleteForId(id: HierarchyId, name: String) {
        db.dbQuery {
            InfrastructureServicesDao.findSingle(selectByIdAndName(id, name)).delete()
        }
    }

    /**
     * Return the [InfrastructureService] for the given [name] and hierarchy entity [id].
     */
    suspend fun getForId(id: HierarchyId, name: String): InfrastructureService? =
        db.dbQuery { getDaoForId(id, name)?.mapToModel() }

    /**
     * Return an [InfrastructureServiceDeclaration] with properties matching the ones
     * of the given [service] that is associated with the given [ORT Run][runId]. Try to find an already existing
     * [service] with the given properties first and return this. If not found, create a new
     * [InfrastructureServiceDeclaration]. In both cases, associate the [service] with the given [ORT Run][runId].
     */
    suspend fun getOrCreateDeclarationForRun(
        service: InfrastructureServiceDeclaration, runId: Long
    ): InfrastructureServiceDeclaration = db.dbQuery {
        service.validate()

        db.blockingQuery {
            val serviceDao = InfrastructureServiceDeclarationDao.getOrPut(service)
            InfrastructureServiceDeclarationsRunsTable.insert {
                it[infrastructureServiceDeclarationId] = serviceDao.id
                it[ortRunId] = runId
            }

            serviceDao.mapToModel()
        }
    }

    /**
     * Return the [InfrastructureServiceDeclaration]s associated to the given [ORT Run][runId].
     */
    suspend fun listDeclarationsForRun(runId: Long): List<InfrastructureServiceDeclaration> = db.dbQuery {
        val subQuery = InfrastructureServiceDeclarationsRunsTable
            .select(infrastructureServiceDeclarationId)
            .where { ortRunId eq runId }

        InfrastructureServiceDeclarationsTable
            .selectAll()
            .where {
                InfrastructureServiceDeclarationsTable.id inSubQuery subQuery
            }
            .map { row ->
                InfrastructureServiceDeclaration(
                    name = row[InfrastructureServiceDeclarationsTable.name],
                    url = row[InfrastructureServiceDeclarationsTable.url],
                    description = row[InfrastructureServiceDeclarationsTable.description],
                    usernameSecret = row[InfrastructureServiceDeclarationsTable.usernameSecret],
                    passwordSecret = row[InfrastructureServiceDeclarationsTable.passwordSecret],
                    credentialsTypes = InfrastructureServiceDeclarationDao.fromCredentialsTypeString(
                        row[InfrastructureServiceDeclarationsTable.credentialsType]
                    )
                )
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
        InfrastructureServicesDao.listQuery(parameters, InfrastructureServicesDao::mapToModel) {
            (InfrastructureServicesTable.organizationId eq (id as? OrganizationId)?.value) and
                    (InfrastructureServicesTable.productId eq (id as? ProductId)?.value) and
                    (InfrastructureServicesTable.repositoryId eq (id as? RepositoryId)?.value)
        }
    }

    /**
     * Return a list with [InfrastructureService]s that are associated with the given [Hierarchy].
     * If there are multiple services with the same URL, instances on a lower level of the hierarchy are preferred,
     * and others are dropped.
     */
    suspend fun listForHierarchy(hierarchy: Hierarchy): List<InfrastructureService> = db.dbQuery {
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

    /**
     * Return a list with the [InfrastructureService]s that are associated with the name of the given
     * [Secret][secretName].
     */
    suspend fun listForSecret(secretName: String): List<InfrastructureService> = db.dbQuery {
        list(ListQueryParameters.DEFAULT) {
            InfrastructureServicesTable.usernameSecret eq secretName or
                    (InfrastructureServicesTable.passwordSecret eq secretName)
        }
    }

    private fun getDaoForId(id: HierarchyId, name: String): InfrastructureServicesDao? =
        InfrastructureServicesDao.find(selectByIdAndName(id, name)).singleOrNull()

    private fun list(parameters: ListQueryParameters, op: ConditionBuilder) =
        InfrastructureServicesDao.find(op)
            .apply(InfrastructureServicesTable, parameters)
            .map(InfrastructureServicesDao::mapToModel)
}

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

/**
 * An exception class that is thrown if the reference to a secret cannot be resolved.
 */
class InvalidSecretReferenceException(reference: String) :
    Exception("Could not resolve secret reference '$reference'.")
