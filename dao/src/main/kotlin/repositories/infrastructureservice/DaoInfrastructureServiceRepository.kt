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
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.findSingle
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.SecretDao
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or

class DaoInfrastructureServiceRepository(private val db: Database) : InfrastructureServiceRepository {
    companion object {
        /**
         * Return an expression to select an [InfrastructureService] for a specific [organization][organizationId]
         * with a given [name].
         */
        private fun selectByOrganizationAndName(
            organizationId: Long,
            name: String
        ): ConditionBuilder = {
            InfrastructureServicesTable.organizationId eq organizationId and
                    (InfrastructureServicesTable.name eq name)
        }

        /**
         * Return an expression to select an [InfrastructureService] for a specific [product][productId] with a given
         * [name].
         */
        private fun selectByProductAndName(
            productId: Long,
            name: String
        ): ConditionBuilder = {
            InfrastructureServicesTable.productId eq productId and
                    (InfrastructureServicesTable.name eq name)
        }
    }

    override fun create(
        name: String,
        url: String,
        description: String?,
        usernameSecret: Secret,
        passwordSecret: Secret,
        credentialsTypes: Set<CredentialsType>,
        organizationId: Long?,
        productId: Long?
    ): InfrastructureService = db.blockingQuery {
        InfrastructureServicesDao.new {
            this.name = name
            this.url = url
            this.description = description
            this.usernameSecretId = usernameSecret.id
            this.passwordSecretId = passwordSecret.id
            this.credentialsTypes = credentialsTypes
            this.organizationId = organizationId
            this.productId = productId
        }.mapToModel()
    }

    override fun getOrCreateForRun(service: InfrastructureService, runId: Long): InfrastructureService {
        service.validate()
        return db.blockingQuery {
            val serviceDao = InfrastructureServicesDao.getOrPut(service)
            InfrastructureServicesRunsTable.insert {
                it[infrastructureServiceId] = serviceDao.id
                it[ortRunId] = runId
            }

            serviceDao.mapToModel()
        }
    }

    override fun listForOrganization(
        organizationId: Long,
        parameters: ListQueryParameters
    ): ListQueryResult<InfrastructureService> = db.blockingQuery {
        InfrastructureServicesDao.listQuery(parameters, InfrastructureServicesDao::mapToModel) {
            InfrastructureServicesTable.organizationId eq organizationId
        }
    }

    override fun getByOrganizationAndName(organizationId: Long, name: String): InfrastructureService? =
        listBlocking(ListQueryParameters.DEFAULT, selectByOrganizationAndName(organizationId, name)).singleOrNull()

    override fun updateForOrganizationAndName(
        organizationId: Long,
        name: String,
        url: OptionalValue<String>,
        description: OptionalValue<String?>,
        usernameSecret: OptionalValue<Secret>,
        passwordSecret: OptionalValue<Secret>,
        credentialsTypes: OptionalValue<Set<CredentialsType>>
    ): InfrastructureService =
        update(
            selectByOrganizationAndName(organizationId, name),
            url,
            description,
            usernameSecret,
            passwordSecret,
            credentialsTypes
        )

    override fun deleteForOrganizationAndName(organizationId: Long, name: String) {
        delete(selectByOrganizationAndName(organizationId, name))
    }

    override fun listForProduct(productId: Long, parameters: ListQueryParameters): List<InfrastructureService> =
        listBlocking(parameters) { InfrastructureServicesTable.productId eq productId }

    override fun getByProductAndName(productId: Long, name: String): InfrastructureService? =
        listBlocking(ListQueryParameters.DEFAULT, selectByProductAndName(productId, name)).singleOrNull()

    override fun updateForProductAndName(
        productId: Long,
        name: String,
        url: OptionalValue<String>,
        description: OptionalValue<String?>,
        usernameSecret: OptionalValue<Secret>,
        passwordSecret: OptionalValue<Secret>,
        credentialsTypes: OptionalValue<Set<CredentialsType>>
    ): InfrastructureService =
        update(
            selectByProductAndName(productId, name),
            url,
            description,
            usernameSecret,
            passwordSecret,
            credentialsTypes
        )

    override fun deleteForProductAndName(productId: Long, name: String) {
        delete(selectByProductAndName(productId, name))
    }

    override fun listForRun(runId: Long, parameters: ListQueryParameters): List<InfrastructureService> =
        db.blockingQuery {
            val subQuery = InfrastructureServicesRunsTable.select(
                InfrastructureServicesRunsTable.infrastructureServiceId
            ).where { InfrastructureServicesRunsTable.ortRunId eq runId }

            list(parameters) { InfrastructureServicesTable.id inSubQuery subQuery }
        }

    override fun listForHierarchy(
        organizationId: Long,
        productId: Long
    ): List<InfrastructureService> = db.blockingQuery {
        list(ListQueryParameters.DEFAULT) {
            InfrastructureServicesTable.productId eq productId or
                    (InfrastructureServicesTable.organizationId eq organizationId)
        }.groupBy(InfrastructureService::url)
            .flatMap { (_, services) ->
                // For duplicates, prefer services defined for products over those for organizations
                services.takeIf { it.size < 2 } ?: services.filter { it.product != null }
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
