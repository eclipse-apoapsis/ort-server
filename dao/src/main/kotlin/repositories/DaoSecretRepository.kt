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

package org.ossreviewtoolkit.server.dao.repositories

import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.OrganizationDao
import org.ossreviewtoolkit.server.dao.tables.ProductDao
import org.ossreviewtoolkit.server.dao.tables.RepositoryDao
import org.ossreviewtoolkit.server.dao.tables.SecretDao
import org.ossreviewtoolkit.server.dao.tables.SecretsTable
import org.ossreviewtoolkit.server.dao.utils.apply
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OptionalValue

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DaoSecretRepository::class.java)

class DaoSecretRepository : SecretRepository {
    override fun create(
        path: String,
        name: String,
        description: String?,
        organizationId: Long?,
        productId: Long?,
        repositoryId: Long?
    ) = blockingQuery {
        SecretDao.new {
            this.path = path
            this.name = name
            this.description = description
            this.organization = organizationId?.let { OrganizationDao[it] }
            this.product = productId?.let { ProductDao[it] }
            this.repository = repositoryId?.let { RepositoryDao[it] }
        }.mapToModel()
    }.getOrThrow()

    override fun getByOrganizationIdAndName(organizationId: Long, name: String) = entityQuery {
        findSecretByParentEntityId(organizationId, null, null, name)?.mapToModel()
    }

    override fun getByProductIdAndName(productId: Long, name: String): Secret? = entityQuery {
        findSecretByParentEntityId(null, productId, null, name)?.mapToModel()
    }

    override fun getByRepositoryIdAndName(repositoryId: Long, name: String): Secret? = entityQuery {
        findSecretByParentEntityId(null, null, repositoryId, name)?.mapToModel()
    }

    override fun listForOrganization(organizationId: Long, parameters: ListQueryParameters) = blockingQuery {
        SecretDao.find { SecretsTable.organizationId eq organizationId }
            .apply(SecretsTable, parameters)
            .map { it.mapToModel() }
    }.getOrElse {
        logger.error("Cannot list secrets for organization id $organizationId.", it)
        throw it
    }

    override fun listForProduct(productId: Long, parameters: ListQueryParameters): List<Secret> = blockingQuery {
        SecretDao.find { SecretsTable.productId eq productId }
            .apply(SecretsTable, parameters)
            .map { it.mapToModel() }
    }.getOrElse {
        logger.error("Cannot list secrets for product id $productId.", it)
        throw it
    }

    override fun listForRepository(repositoryId: Long, parameters: ListQueryParameters): List<Secret> = blockingQuery {
        SecretDao.find { SecretsTable.repositoryId eq repositoryId }
            .apply(SecretsTable, parameters)
            .map { it.mapToModel() }
    }.getOrElse {
        logger.error("Cannot list secrets for repository id $repositoryId.", it)
        throw it
    }

    override fun updateForOrganizationAndName(
        organizationId: Long,
        name: String,
        description: OptionalValue<String?>
    ): Secret = blockingQuery {
        val secret = findSecretByParentEntityId(organizationId, null, null, name)
            ?: throw IllegalArgumentException("No secrets with name $name found for organization $organizationId")

        description.ifPresent { secret.description = it }

        secret.mapToModel()
    }.getOrThrow()

    override fun updateForProductAndName(
        productId: Long,
        name: String,
        description: OptionalValue<String?>
    ): Secret = blockingQuery {
        val secret = findSecretByParentEntityId(null, productId, null, name)
            ?: throw IllegalArgumentException("No secrets with name $name found for product $productId")

        description.ifPresent { secret.description = it }

        secret.mapToModel()
    }.getOrThrow()

    override fun updateForRepositoryAndName(
        repositoryId: Long,
        name: String,
        description: OptionalValue<String?>
    ): Secret = blockingQuery {
        val secret = findSecretByParentEntityId(null, null, repositoryId, name)
            ?: throw IllegalArgumentException("No secrets with name $name found for repository $repositoryId")

        description.ifPresent { secret.description = it }

        secret.mapToModel()
    }.getOrThrow()

    override fun deleteForOrganizationAndName(organizationId: Long, name: String) = blockingQuery {
        val secret =
            findSecretByParentEntityId(organizationId, null, null, name)
                ?: throw IllegalArgumentException("No secrets with name $name found for organization $organizationId")

        secret.delete()
    }.getOrThrow()

    override fun deleteForProductAndName(productId: Long, name: String) = blockingQuery {
        val secret =
            findSecretByParentEntityId(null, productId, null, name)
                ?: throw IllegalArgumentException("No secrets with name $name found for product $productId")

        secret.delete()
    }.getOrThrow()

    override fun deleteForRepositoryAndName(repositoryId: Long, name: String) = blockingQuery {
        val secret =
            findSecretByParentEntityId(null, null, repositoryId, name)
                ?: throw IllegalArgumentException("No secrets with name $name found for repository $repositoryId")

        secret.delete()
    }.getOrThrow()

    private fun findSecretByParentEntityId(
        organizationId: Long?,
        productId: Long?,
        repositoryId: Long?,
        name: String
    ) =
        SecretDao.find {
            SecretsTable.organizationId eq organizationId and
                    (SecretsTable.productId eq productId) and
                    (SecretsTable.repositoryId eq repositoryId) and
                    (SecretsTable.name eq name)
        }.firstOrNull()
}
