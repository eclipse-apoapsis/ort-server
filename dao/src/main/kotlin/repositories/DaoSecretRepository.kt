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
        description: String,
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

    override fun get(id: Long) = entityQuery { SecretDao[id].mapToModel() }

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

    override fun update(
        id: Long,
        path: OptionalValue<String>,
        name: OptionalValue<String>,
        description: OptionalValue<String?>
    ) = blockingQuery {
        val secret = SecretDao[id]

        path.ifPresent { secret.path = it }
        name.ifPresent { secret.name = it }
        description.ifPresent { secret.description = it }

        SecretDao[id].mapToModel()
    }.getOrThrow()

    override fun delete(id: Long) = blockingQuery { SecretDao[id].delete() }.getOrThrow()
}
