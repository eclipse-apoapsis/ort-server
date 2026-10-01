/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.secrets

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.blockingQueryCatching
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.findSingle
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.SecretDao
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.SecretsTable
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SecretRepository::class.java)

/**
 * A repository of [secrets][Secret].
 */
internal class SecretRepository(private val db: Database) {
    /**
     * Create a secret for the given hierarchy [id].
     */
    fun create(path: String, name: String, description: String?, id: HierarchyId): Secret = db.blockingQuery {
        SecretDao.new {
            this.path = path
            this.name = name
            this.description = description
            this.organizationId = (id as? OrganizationId)?.value
            this.productId = (id as? ProductId)?.value
            this.repositoryId = (id as? RepositoryId)?.value
        }.mapToModel()
    }

    /**
     * Get a secret by [id] and [name]. Returns null if the secret is not found.
     */
    fun getByIdAndName(id: HierarchyId, name: String): Secret? = db.entityQuery {
        SecretDao.find(byNameCondition(id, name)).firstOrNull()?.mapToModel()
    }

    /**
     * List all secrets for an [id] according to the given [parameters].
     */
    fun listForId(
        id: HierarchyId,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<Secret> = db.blockingQueryCatching {
        val query = when (id) {
            is OrganizationId -> SecretsTable.organizationId eq id.value
            is ProductId -> SecretsTable.productId eq id.value
            is RepositoryId -> SecretsTable.repositoryId eq id.value
        }

        SecretDao.listQuery(parameters, SecretDao::mapToModel, query)
    }.getOrElse {
        logger.error("Cannot list secrets for $id.", it)
        throw it
    }

    /**
     * Update a secret by [id] and [name] with the [present][OptionalValue.Present] values.
     */
    fun updateForIdAndName(id: HierarchyId, name: String, description: OptionalValue<String?>): Secret =
        db.blockingQuery {
            val secret = SecretDao.findSingle(byNameCondition(id, name))
            description.ifPresent { secret.description = it }
            secret.mapToModel()
        }

    /**
     * Delete a secret by [id] and [name].
     */
    fun deleteForIdAndName(id: HierarchyId, name: String) = db.blockingQuery {
        SecretDao.findSingle(byNameCondition(id, name)).delete()
    }
}

/**
 * Generate a WHERE condition to find a [Secret] entity within the hierarchy [id] and the given [name].
 */
private fun byNameCondition(id: HierarchyId, name: String): Op<Boolean> =
    SecretsTable.organizationId eq (id as? OrganizationId)?.value and
            (SecretsTable.productId eq (id as? ProductId)?.value) and
            (SecretsTable.repositoryId eq (id as? RepositoryId)?.value) and
            (SecretsTable.name eq name)
