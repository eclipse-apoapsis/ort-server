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

package org.ossreviewtoolkit.server.services

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.Secret as SecretValue
import org.ossreviewtoolkit.server.secrets.SecretStorage

/**
 * A service providing functions for working with [secrets][Secret].
 */
class SecretService(
    private val db: Database,
    private val secretRepository: SecretRepository,
    private val secretStorage: SecretStorage
) {
    /**
     * Create a secret with the given metadata [name] and [description], and the provided [value]. As the secret can
     * only belong to an organization, a product, or a repository, a respective [check][requireUnambiguousSecret]
     * validates the input data.
     */
    suspend fun createSecret(
        name: String,
        value: String,
        description: String?,
        organizationId: Long?,
        productId: Long?,
        repositoryId: Long?
    ): Secret = db.dbQuery {
        requireUnambiguousSecret(organizationId, productId, repositoryId)

        val path = secretStorage.createPath(organizationId, productId, repositoryId, name)

        val secret = secretRepository.create(path.path, name, description, organizationId, productId, repositoryId)

        secretStorage.writeSecret(path, SecretValue(value))

        secret
    }

    /**
     * Delete a secret by [organizationId] and [name].
     */
    suspend fun deleteSecretByOrganizationAndName(organizationId: Long, name: String) = db.dbQuery {
        val secret = secretRepository.getByOrganizationIdAndName(organizationId, name)
        secretRepository.deleteForOrganizationAndName(organizationId, name)
        secret?.deleteValue()
    }

    /**
     * Delete a secret by [productId] and [name].
     */
    suspend fun deleteSecretByProductAndName(productId: Long, name: String) = db.dbQuery {
        val secret = secretRepository.getByProductIdAndName(productId, name)
        secretRepository.deleteForProductAndName(productId, name)
        secret?.deleteValue()
    }

    /**
     * Delete a secret by [repositoryId] and [name].
     */
    suspend fun deleteSecretByRepositoryAndName(repositoryId: Long, name: String) = db.dbQuery {
        val secret = secretRepository.getByRepositoryIdAndName(repositoryId, name)
        secretRepository.deleteForRepositoryAndName(repositoryId, name)
        secret?.deleteValue()
    }

    /**
     * Get a secret by [organizationId] and [name]. Returns null if the secret is not found.
     */
    suspend fun getSecretByOrganizationIdAndName(organizationId: Long, name: String): Secret? = db.dbQuery {
        secretRepository.getByOrganizationIdAndName(organizationId, name)
    }

    /**
     * Get a secret by [productId] and [name]. Returns null if the secret is not found.
     */
    suspend fun getSecretByProductIdAndName(productId: Long, name: String): Secret? = db.dbQuery {
        secretRepository.getByProductIdAndName(productId, name)
    }

    /**
     * Get a secret by [repositoryId] and [name]. Returns null if the secret is not found.
     */
    suspend fun getSecretByRepositoryIdAndName(repositoryId: Long, name: String): Secret? = db.dbQuery {
        secretRepository.getByRepositoryIdAndName(repositoryId, name)
    }

    /**
     * List all secrets for a specific [organization][organizationId] and according to the given [parameters].
     */
    suspend fun listForOrganization(organizationId: Long, parameters: ListQueryParameters): List<Secret> = db.dbQuery {
        secretRepository.listForOrganization(organizationId, parameters)
    }

    /**
     * List all secrets for a specific [product][productId] and according to the given [parameters].
     */
    suspend fun listForProduct(productId: Long, parameters: ListQueryParameters): List<Secret> = db.dbQuery {
        secretRepository.listForProduct(productId, parameters)
    }

    /**
     * List all secrets for a specific [repository][repositoryId] and according to the given [parameters].
     */
    suspend fun listForRepository(repositoryId: Long, parameters: ListQueryParameters): List<Secret> = db.dbQuery {
        secretRepository.listForRepository(repositoryId, parameters)
    }

    /**
     * Update a secret by [organizationId] and [name] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateSecretByOrganizationAndName(
        organizationId: Long,
        name: String,
        value: OptionalValue<String>,
        description: OptionalValue<String?>
    ): Secret = db.dbQuery {
        val secret = secretRepository.updateForOrganizationAndName(organizationId, name, description)

        value.ifPresent {
            secretRepository.getByOrganizationIdAndName(organizationId, name)?.updateValue(it)
        }

        secret
    }

    /**
     * Update a secret by [productId] and [name] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateSecretByProductAndName(
        productId: Long,
        name: String,
        value: OptionalValue<String>,
        description: OptionalValue<String?>
    ): Secret = db.dbQuery {
        val secret = secretRepository.updateForProductAndName(productId, name, description)

        value.ifPresent {
            secretRepository.getByProductIdAndName(productId, name)?.updateValue(it)
        }

        secret
    }

    /**
     * Update a secret by [repositoryId] and [name][name] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateSecretByRepositoryAndName(
        repositoryId: Long,
        name: String,
        value: OptionalValue<String>,
        description: OptionalValue<String?>
    ): Secret = db.dbQuery {
        val secret = secretRepository.updateForRepositoryAndName(repositoryId, name, description)

        value.ifPresent {
            secretRepository.getByRepositoryIdAndName(repositoryId, name)?.updateValue(it)
        }

        secret
    }

    private fun requireUnambiguousSecret(organizationId: Long?, productId: Long?, repositoryId: Long?) {
        require(listOfNotNull(organizationId, productId, repositoryId).size == 1) {
            "The secret should belong to one of the following: Organization, Product or Repository."
        }
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
