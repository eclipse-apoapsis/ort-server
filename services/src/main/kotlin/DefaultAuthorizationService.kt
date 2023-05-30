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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.clients.keycloak.KeycloakClient
import org.ossreviewtoolkit.server.clients.keycloak.RoleName
import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DefaultAuthorizationService::class.java)

internal const val ROLE_DESCRIPTION = "This role is auto-generated, do not edit or remove."

/**
 * The default implementation of [AuthorizationService], using a [keycloakClient] to manage Keycloak roles.
 */
class DefaultAuthorizationService(
    private val keycloakClient: KeycloakClient,
    private val db: Database,
    private val organizationRepository: OrganizationRepository,
    private val productRepository: ProductRepository,
    private val repositoryRepository: RepositoryRepository
) : AuthorizationService {
    override suspend fun createOrganizationPermissions(organizationId: Long) {
        OrganizationPermission.getRolesForOrganization(organizationId).forEach { roleName ->
            keycloakClient.createRole(name = RoleName(roleName), description = ROLE_DESCRIPTION)
        }
    }

    override suspend fun deleteOrganizationPermissions(organizationId: Long) {
        OrganizationPermission.getRolesForOrganization(organizationId).forEach { roleName ->
            keycloakClient.deleteRole(RoleName(roleName))
        }
    }

    override suspend fun createProductPermissions(productId: Long) {
        ProductPermission.getRolesForProduct(productId).forEach { roleName ->
            keycloakClient.createRole(name = RoleName(roleName), description = ROLE_DESCRIPTION)
        }
    }

    override suspend fun deleteProductPermissions(productId: Long) {
        ProductPermission.getRolesForProduct(productId).forEach { roleName ->
            keycloakClient.deleteRole(RoleName(roleName))
        }
    }

    override suspend fun createRepositoryPermissions(repositoryId: Long) {
        RepositoryPermission.getRolesForRepository(repositoryId).forEach { roleName ->
            keycloakClient.createRole(name = RoleName(roleName), description = ROLE_DESCRIPTION)
        }
    }

    override suspend fun deleteRepositoryPermissions(repositoryId: Long) {
        RepositoryPermission.getRolesForRepository(repositoryId).forEach { roleName ->
            keycloakClient.deleteRole(RoleName(roleName))
        }
    }

    override suspend fun synchronizePermissions() {
        withContext(Dispatchers.IO) {
            val roles = keycloakClient.getRoles().mapTo(mutableSetOf()) { it.name.value }

            synchronizeOrganizationPermissions(roles)
            synchronizeProductPermissions(roles)
            synchronizeRepositoryPermissions(roles)
        }
    }

    private suspend fun synchronizeOrganizationPermissions(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for organizations.")

        runCatching {
            db.dbQuery { organizationRepository.list() }.forEach { organization ->
                val requiredRoles = OrganizationPermission.getRolesForOrganization(organization.id)
                val rolePrefix = OrganizationPermission.rolePrefix(organization.id)
                synchronizeRoles(roles, requiredRoles, rolePrefix)
            }
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for organizations.", it)
        }.getOrThrow()
    }

    private suspend fun synchronizeProductPermissions(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for products.")

        runCatching {
            db.dbQuery { productRepository.list() }.forEach { product ->
                val requiredRoles = ProductPermission.getRolesForProduct(product.id)
                val rolePrefix = ProductPermission.rolePrefix(product.id)
                synchronizeRoles(roles, requiredRoles, rolePrefix)
            }
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for products.", it)
        }.getOrThrow()
    }

    private suspend fun synchronizeRepositoryPermissions(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for repositories.")

        runCatching {
            db.dbQuery { repositoryRepository.list() }.forEach { repository ->
                val requiredRoles = RepositoryPermission.getRolesForRepository(repository.id)
                val rolePrefix = RepositoryPermission.rolePrefix(repository.id)
                synchronizeRoles(roles, requiredRoles, rolePrefix)
            }
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for repositories.", it)
        }.getOrThrow()
    }

    private suspend fun synchronizeRoles(roles: Set<String>, requiredRoles: List<String>, rolePrefix: String) {
        val missingRoles = requiredRoles.filter { it !in roles }
        logger.info("Creating missing roles: ${missingRoles.joinToString()}")
        missingRoles.forEach { role ->
            keycloakClient.createRole(RoleName(role), ROLE_DESCRIPTION)
        }

        val unneededRoles = roles.filter { it.startsWith(rolePrefix) }.filter { it !in requiredRoles }
        logger.info("Removing unneeded roles: ${unneededRoles.joinToString()}")
        unneededRoles.forEach { role ->
            keycloakClient.deleteRole(RoleName(role))
        }
    }
}
