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

package org.ossreviewtoolkit.server.dao.repositories

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.dao.ConditionBuilder
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.blockingQueryCatching
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.findSingle
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

class DaoSecretRepository(private val db: Database) : SecretRepository {
    override fun create(
        path: String,
        name: String,
        description: String?,
        organizationId: Long?,
        productId: Long?,
        repositoryId: Long?
    ) = db.blockingQuery {
        SecretDao.new {
            this.path = path
            this.name = name
            this.description = description
            this.organization = organizationId?.let { OrganizationDao[it] }
            this.product = productId?.let { ProductDao[it] }
            this.repository = repositoryId?.let { RepositoryDao[it] }
        }.mapToModel()
    }

    override fun getByOrganizationIdAndName(organizationId: Long, name: String) = db.entityQuery {
        findSecretByParentEntityId(organizationId, null, null, name)?.mapToModel()
    }

    override fun getByProductIdAndName(productId: Long, name: String): Secret? = db.entityQuery {
        findSecretByParentEntityId(null, productId, null, name)?.mapToModel()
    }

    override fun getByRepositoryIdAndName(repositoryId: Long, name: String): Secret? = db.entityQuery {
        findSecretByParentEntityId(null, null, repositoryId, name)?.mapToModel()
    }

    override fun listForOrganization(organizationId: Long, parameters: ListQueryParameters) = db.blockingQueryCatching {
        SecretDao.find { SecretsTable.organizationId eq organizationId }
            .apply(SecretsTable, parameters)
            .map { it.mapToModel() }
    }.getOrElse {
        logger.error("Cannot list secrets for organization id $organizationId.", it)
        throw it
    }

    override fun listForProduct(productId: Long, parameters: ListQueryParameters): List<Secret> =
        db.blockingQueryCatching {
            SecretDao.find { SecretsTable.productId eq productId }
                .apply(SecretsTable, parameters)
                .map { it.mapToModel() }
        }.getOrElse {
            logger.error("Cannot list secrets for product id $productId.", it)
            throw it
        }

    override fun listForRepository(repositoryId: Long, parameters: ListQueryParameters): List<Secret> =
        db.blockingQueryCatching {
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
    ): Secret = db.blockingQuery {
        val secret = SecretDao.findSingle(byNameCondition(organizationId, null, null, name))

        description.ifPresent { secret.description = it }

        secret.mapToModel()
    }

    override fun updateForProductAndName(
        productId: Long,
        name: String,
        description: OptionalValue<String?>
    ): Secret = db.blockingQuery {
        val secret = SecretDao.findSingle(byNameCondition(null, productId, null, name))

        description.ifPresent { secret.description = it }

        secret.mapToModel()
    }

    override fun updateForRepositoryAndName(
        repositoryId: Long,
        name: String,
        description: OptionalValue<String?>
    ): Secret = db.blockingQuery {
        val secret = SecretDao.findSingle(byNameCondition(null, null, repositoryId, name))

        description.ifPresent { secret.description = it }

        secret.mapToModel()
    }

    override fun deleteForOrganizationAndName(organizationId: Long, name: String) = db.blockingQuery {
        val secret = SecretDao.findSingle(byNameCondition(organizationId, null, null, name))

        secret.delete()
    }

    override fun deleteForProductAndName(productId: Long, name: String) = db.blockingQuery {
        val secret = SecretDao.findSingle(byNameCondition(null, productId, null, name))

        secret.delete()
    }

    override fun deleteForRepositoryAndName(repositoryId: Long, name: String) = db.blockingQuery {
        val secret = SecretDao.findSingle(byNameCondition(null, null, repositoryId, name))

        secret.delete()
    }
}

/**
 * Generate a WHERE condition to find a [Secret] entity with a specific [name] that is associated with one of the
 * given [organizationId], [productId], or [repositoryId].
 */
private fun byNameCondition(
    organizationId: Long?,
    productId: Long?,
    repositoryId: Long?,
    name: String
): ConditionBuilder = {
    SecretsTable.organizationId eq organizationId and
            (SecretsTable.productId eq productId) and
            (SecretsTable.repositoryId eq repositoryId) and
            (SecretsTable.name eq name)
}

/**
 * Find a [Secret] entity with a specific [name] that is associated with one of the given [organizationId],
 * [productId], or [repositoryId].
 */
private fun findSecretByParentEntityId(
    organizationId: Long?,
    productId: Long?,
    repositoryId: Long?,
    name: String
): SecretDao? =
    SecretDao.find(byNameCondition(organizationId, productId, repositoryId, name)).firstOrNull()
