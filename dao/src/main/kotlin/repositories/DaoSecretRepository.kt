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

package org.eclipse.apoapsis.ortserver.dao.repositories

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.blockingQueryCatching
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.findSingle
import org.eclipse.apoapsis.ortserver.dao.tables.OrganizationDao
import org.eclipse.apoapsis.ortserver.dao.tables.ProductDao
import org.eclipse.apoapsis.ortserver.dao.tables.RepositoryDao
import org.eclipse.apoapsis.ortserver.dao.tables.SecretDao
import org.eclipse.apoapsis.ortserver.dao.tables.SecretsTable
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository.Entity
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DaoSecretRepository::class.java)

class DaoSecretRepository(private val db: Database) : SecretRepository {
    override fun create(path: String, name: String, description: String?, entity: Entity, id: Long) =
        db.blockingQuery {
            SecretDao.new {
                this.path = path
                this.name = name
                this.description = description
                this.organization = id.takeIf { entity == Entity.ORGANIZATION }?.let { OrganizationDao[it] }
                this.product = id.takeIf { entity == Entity.PRODUCT }?.let { ProductDao[it] }
                this.repository = id.takeIf { entity == Entity.REPOSITORY }?.let { RepositoryDao[it] }
            }.mapToModel()
        }

    override fun get(entity: Entity, id: Long, name: String) = db.entityQuery {
        SecretDao.find(entity.condition(id) and (SecretsTable.name eq name)).firstOrNull()?.mapToModel()
    }

    override fun list(entity: Entity, id: Long, parameters: ListQueryParameters) = db.blockingQueryCatching {
        SecretDao.find(entity.condition(id)).apply(SecretsTable, parameters).map { it.mapToModel() }
    }.getOrElse {
        logger.error("Cannot list secrets for organization id $id.", it)
        throw it
    }

    override fun update(entity: Entity, id: Long, name: String, description: OptionalValue<String?>): Secret =
        db.blockingQuery {
            val secret = SecretDao.findSingle { entity.condition(id) and (SecretsTable.name eq name) }

            description.ifPresent { secret.description = it }

            secret.mapToModel()
        }

    override fun delete(entity: Entity, id: Long, name: String) = db.blockingQuery {
        val secret = SecretDao.findSingle { entity.condition(id) and (SecretsTable.name eq name) }

        secret.delete()
    }
}

private fun Entity.condition(id: Long) = when (this) {
    Entity.ORGANIZATION -> SecretsTable.organizationId eq id
    Entity.PRODUCT -> SecretsTable.productId eq id
    Entity.REPOSITORY -> SecretsTable.repositoryId eq id
}
