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
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.Secret as SecretValue
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage

import org.jetbrains.exposed.sql.Database

/**
 * A service providing functions for working with [secrets][Secret].
 */
class SecretService(
    private val db: Database,
    private val secretRepository: SecretRepository,
    private val infrastructureServiceRepository: InfrastructureServiceRepository,
    private val secretStorage: SecretStorage
) {
    /**
     * Create a secret with the given metadata [name] and [description], and the provided [value]. As the secret can
     * only belong to an organization, a product, or a repository, as specified by the [entity].
     */
    suspend fun createSecret(
        name: String,
        value: String,
        description: String?,
        entity: SecretRepository.Entity,
        id: Long
    ): Secret = db.dbQuery {
        val path = secretStorage.createPath(entity, id, name)

        val secret = secretRepository.create(path.path, name, description, entity, id)

        secretStorage.writeSecret(path, SecretValue(value))

        secret
    }

    /**
     * Delete a secret by [entity], [id] and [name].
     */
    suspend fun deleteSecret(entity: SecretRepository.Entity, id: Long, name: String) = db.dbQuery {
        val secret = secretRepository.get(entity, id, name)?.also {
            val services = infrastructureServiceRepository.listForSecret(it.id).map { service -> service.name }

            if (services.isNotEmpty()) {
                throw ReferencedEntityException("Could not delete secret '${it.name}' due to usage in $services.")
            }
        }

        secretRepository.delete(entity, id, name)
        secret?.deleteValue()
    }

    /**
     * Get a secret by [entity], [id] and [name]. Return null if the secret is not found.
     */
    suspend fun getSecret(entity: SecretRepository.Entity, id: Long, name: String): Secret? = db.dbQuery {
        secretRepository.get(entity, id, name)
    }

    /**
     * List all secrets for a specific [entity] with [id] and according to the given [parameters].
     */
    suspend fun listSecrets(
        entity: SecretRepository.Entity,
        id: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): List<Secret> = db.dbQuery {
        secretRepository.list(entity, id, parameters)
    }

    /**
     * Update the [named][name] secret in [entity] with [id] to the optional [value] and [description].
     */
    suspend fun updateSecret(
        entity: SecretRepository.Entity,
        id: Long,
        name: String,
        value: OptionalValue<String>,
        description: OptionalValue<String?>
    ): Secret = db.dbQuery {
        val secret = secretRepository.update(entity, id, name, description)

        value.ifPresent {
            secretRepository.get(entity, id, name)?.updateValue(it)
        }

        secret
    }

    /**
     * Update the [value] of this [Secret] in the [SecretStorage].
     */
    private fun Secret.updateValue(value: String) {
        secretStorage.writeSecret(Path(path), SecretValue(value))
    }

    /**
     * Remove the value of this [Secret] from the [SecretStorage].
     */
    private fun Secret.deleteValue() {
        secretStorage.removeSecret(Path(path))
    }
}

class ReferencedEntityException(message: String) : Exception(message)
