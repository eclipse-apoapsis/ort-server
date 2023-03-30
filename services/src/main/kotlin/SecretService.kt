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

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.ktor.server.config.ApplicationConfig

import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.secrets.SecretStorage

/**
 * A service providing functions for working with [secrets][Secret].
 */
class SecretService(
    private val secretRepository: SecretRepository,
    applicationConfig: ApplicationConfig
) {
    private val config: Config = ConfigFactory.parseMap(applicationConfig.toMap())
    private val secretStorage by lazy { SecretStorage.createStorage(config) }

    /**
     * Create a secret. As the secret can only belong to an organization, a product, or a repository, a respective
     * [check][requireUnambiguousSecret] validates the input data.
     */
    suspend fun createSecret(
        name: String,
        description: String?,
        organizationId: Long?,
        productId: Long?,
        repositoryId: Long?
    ): Secret = dbQuery {
        requireUnambiguousSecret(organizationId, productId, repositoryId)

        val path = secretStorage.createPath(organizationId, productId, repositoryId, name)

        secretRepository.create(path.path, name, description, organizationId, productId, repositoryId)
    }.getOrThrow()

    /**
     * Delete a secret by [organizationId] and [name].
     */
    suspend fun deleteSecretByOrganizationAndName(organizationId: Long, name: String): Unit = dbQuery {
        secretRepository.deleteForOrganizationAndName(organizationId, name)
    }.getOrThrow()

    /**
     * Delete a secret by [productId] and [name].
     */
    suspend fun deleteSecretByProductAndName(productId: Long, name: String): Unit = dbQuery {
        secretRepository.deleteForProductAndName(productId, name)
    }.getOrThrow()

    /**
     * Delete a secret by [repositoryId] and [name].
     */
    suspend fun deleteSecretByRepositoryAndName(repositoryId: Long, name: String): Unit = dbQuery {
        secretRepository.deleteForRepositoryAndName(repositoryId, name)
    }.getOrThrow()

    /**
     * Get a secret by [organizationId] and [name]. Returns null if the secret is not found.
     */
    suspend fun getSecretByOrganizationIdAndName(organizationId: Long, name: String): Secret? = dbQuery {
        secretRepository.getByOrganizationIdAndName(organizationId, name)
    }.getOrThrow()

    /**
     * Get a secret by [productId] and [name]. Returns null if the secret is not found.
     */
    suspend fun getSecretByProductIdAndName(productId: Long, name: String): Secret? = dbQuery {
        secretRepository.getByProductIdAndName(productId, name)
    }.getOrThrow()

    /**
     * Get a secret by [repositoryId] and [name]. Returns null if the secret is not found.
     */
    suspend fun getSecretByRepositoryIdAndName(repositoryId: Long, name: String): Secret? = dbQuery {
        secretRepository.getByRepositoryIdAndName(repositoryId, name)
    }.getOrThrow()

    /**
     * List all secrets for a specific [organization][organizationId] and according to the given [parameters].
     */
    suspend fun listForOrganization(organizationId: Long, parameters: ListQueryParameters): List<Secret> = dbQuery {
        secretRepository.listForOrganization(organizationId, parameters)
    }.getOrThrow()

    /**
     * List all secrets for a specific [product][productId] and according to the given [parameters].
     */
    suspend fun listForProduct(productId: Long, parameters: ListQueryParameters): List<Secret> = dbQuery {
        secretRepository.listForProduct(productId, parameters)
    }.getOrThrow()

    /**
     * List all secrets for a specific [repository][repositoryId] and according to the given [parameters].
     */
    suspend fun listForRepository(repositoryId: Long, parameters: ListQueryParameters): List<Secret> = dbQuery {
        secretRepository.listForRepository(repositoryId, parameters)
    }.getOrThrow()

    /**
     * Update a secret by [organizationId] and [name][oldName] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateSecretByOrganizationAndName(
        organizationId: Long,
        name: String,
        description: OptionalValue<String?>
    ): Secret = dbQuery {
        secretRepository.updateForOrganizationAndName(organizationId, name, description)
    }.getOrThrow()

    /**
     * Update a secret by [productId] and [name][oldName] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateSecretByProductAndName(
        productId: Long,
        name: String,
        description: OptionalValue<String?>
    ): Secret = dbQuery {
        secretRepository.updateForProductAndName(productId, name, description)
    }.getOrThrow()

    /**
     * Update a secret by [repositoryId] and [name][name] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateSecretByRepositoryAndName(
        repositoryId: Long,
        name: String,
        description: OptionalValue<String?>
    ): Secret = dbQuery {
        secretRepository.updateForRepositoryAndName(repositoryId, name, description)
    }.getOrThrow()

    private fun requireUnambiguousSecret(organizationId: Long?, productId: Long?, repositoryId: Long?) {
        require(listOfNotNull(organizationId, productId, repositoryId).size == 1) {
            "The secret should belong to one of the following: Organization, Product or Repository."
        }
    }
}
