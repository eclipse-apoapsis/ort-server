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

package org.eclipse.apoapsis.ortserver.dao.repositories.secret

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.ConditionBuilder
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.blockingQueryCatching
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.findSingle
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DaoSecretRepository::class.java)

class DaoSecretRepository(private val db: Database) : SecretRepository {
    override fun create(path: String, name: String, description: String?, id: HierarchyId) = db.blockingQuery {
        SecretDao.new {
            this.path = path
            this.name = name
            this.description = description
            this.organizationId = (id as? OrganizationId)?.value
            this.productId = (id as? ProductId)?.value
            this.repositoryId = (id as? RepositoryId)?.value
        }.mapToModel()
    }

    override fun getByIdAndName(id: HierarchyId, name: String) = db.entityQuery {
        SecretDao.find(byNameCondition(id, name)).firstOrNull()?.mapToModel()
    }

    override fun listForId(id: HierarchyId, parameters: ListQueryParameters, includeDeleted: Boolean) =
        db.blockingQueryCatching {
            val column = when (id) {
                is OrganizationId -> SecretsTable.organizationId
                is ProductId -> SecretsTable.productId
                is RepositoryId -> SecretsTable.repositoryId
            }

            val query: ConditionBuilder = {
                (column eq id.value) and (SecretsTable.isDeleted eq includeDeleted)
            }

            SecretDao.listQuery(parameters, SecretDao::mapToModel, query)
        }.getOrElse {
            logger.error("Cannot list secrets for $id.", it)
            throw it
        }

    override fun updateForIdAndName(id: HierarchyId, name: String, description: OptionalValue<String?>): Secret =
        db.blockingQuery {
            val secret = SecretDao.findSingle(byNameCondition(id, name))
            description.ifPresent { secret.description = it }
            secret.mapToModel()
        }

    /**
     * Mark the entry as deleted instead of physically removing it from the database table. This approach preserves
     * referential integrity in the database.
     */
    override fun markAsDeletedForIdAndName(id: HierarchyId, name: String): Secret? = db.blockingQuery {
        SecretDao.findSingle(byNameCondition(id, name))
            .takeIf { !it.isDeleted }
            ?.let {
                it.isDeleted = true
                it.deletedAt = Clock.System.now()
                it.mapToModel()
            }
    }
}

/**
 * Generate a WHERE condition to find a [Secret] entity within the hierarchy [id] and the given [name].
 */
private fun byNameCondition(id: HierarchyId, name: String): ConditionBuilder = {
    SecretsTable.organizationId eq (id as? OrganizationId)?.value and
            (SecretsTable.productId eq (id as? ProductId)?.value) and
            (SecretsTable.repositoryId eq (id as? RepositoryId)?.value) and
            (SecretsTable.name eq name)
}
