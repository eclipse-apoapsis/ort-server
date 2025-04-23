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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.components.authorization.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.dbQueryCatching
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ProductRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.services.utils.toJoinedString

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OrganizationService::class.java)

/**
 * A service providing functions for working with [organizations][Organization].
 */
class OrganizationService(
    private val db: Database,
    private val organizationRepository: OrganizationRepository,
    private val productRepository: ProductRepository,
    private val authorizationService: AuthorizationService
) {
    /**
     * Create an organization.
     */
    suspend fun createOrganization(name: String, description: String?): Organization = db.dbQueryCatching {
        organizationRepository.create(name, description)
    }.onSuccess { organization ->
        runCatching {
            authorizationService.createOrganizationPermissions(organization.id)
            authorizationService.createOrganizationRoles(organization.id)
        }.onFailure { e ->
            logger.error("Error while creating Keycloak roles for organization '${organization.id}'.", e)
        }
    }.getOrThrow()

    /**
     * Create a product inside an [organization][organizationId].
     */
    suspend fun createProduct(name: String, description: String?, organizationId: Long) = db.dbQueryCatching {
        productRepository.create(name, description, organizationId)
    }.onSuccess { product ->
        runCatching {
            authorizationService.createProductPermissions(product.id)
            authorizationService.createProductRoles(product.id)
        }.onFailure { e ->
            logger.error("Error while creating Keycloak roles for product '${product.id}'.", e)
        }
    }.getOrThrow()

    /**
     * Delete an organization by [organizationId].
     */
    suspend fun deleteOrganization(organizationId: Long): Unit = db.dbQueryCatching {
        if (productRepository.countForOrganization(organizationId) != 0L) {
            throw OrganizationNotEmptyException(
                "Cannot delete organization '$organizationId', as it still contains products."
            )
        }

        organizationRepository.delete(organizationId)
    }.onSuccess {
        runCatching {
            authorizationService.deleteOrganizationPermissions(organizationId)
            authorizationService.deleteOrganizationRoles(organizationId)
        }.onFailure { e ->
            logger.error("Error while deleting Keycloak roles for organization '$organizationId'.", e)
        }
    }.getOrThrow()

    /**
     * Get an organization by [organizationId]. Returns null if the organization is not found.
     */
    suspend fun getOrganization(organizationId: Long): Organization? = db.dbQuery {
        organizationRepository.get(organizationId)
    }

    /**
     * List all organizations according to the given [parameters].
     */
    suspend fun listOrganizations(
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): List<Organization> = db.dbQuery {
        organizationRepository.list(parameters)
    }

    /**
     * List all products for an [organization][organizationId].
     */
    suspend fun listProductsForOrganization(
        organizationId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ) = db.dbQuery {
        productRepository.listForOrganization(organizationId, parameters)
    }

    /**
     * Update an organization by [organizationId] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateOrganization(
        organizationId: Long,
        name: OptionalValue<String> = OptionalValue.Absent,
        description: OptionalValue<String?> = OptionalValue.Absent
    ): Organization = db.dbQuery {
        organizationRepository.update(organizationId, name, description)
    }

    /**
     * Add a user to one of the three groups that grant roles and permissions on organization level.
     */
    suspend fun addUserToGroup(
        username: String,
        organizationId: Long,
        groupId: String
    ) {
        getOrganization(organizationId)
            ?: throw ResourceNotFoundException("Organization with organizationId '$organizationId' not found.")

        val organizationRole = try {
            OrganizationRole.valueOf(groupId.uppercase().removeSuffix("S"))
        } catch (e: IllegalArgumentException) {
            throw ResourceNotFoundException(
                "Group with groupId '$groupId' not found. Must be one of ${OrganizationRole.entries.toJoinedString()}",
                e
            )
        }

        val groupName = organizationRole.groupName(organizationId)

        // As the AuthorizationService does not distinguish between technical exceptions (e.g., cannot connect to
        // Keycloak) and business exceptions (e.g., user not found), we can't do special exception handling here
        // and just let the exception propagate.
        authorizationService.addUserToGroup(username, groupName)
    }

    /**
     * Remove a user from a group that grant roles and permissions on organization level.
     */
    suspend fun removeUserFromGroup(
        username: String,
        organizationId: Long,
        groupId: String
    ) {
        getOrganization(organizationId)
            ?: throw ResourceNotFoundException("Organization with organizationId '$organizationId' not found.")

        val organizationRole = try {
            OrganizationRole.valueOf(groupId.uppercase().removeSuffix("S"))
        } catch (e: IllegalArgumentException) {
            throw ResourceNotFoundException(
                "Group with groupId '$groupId' not found. Must be one of ${OrganizationRole.entries.toJoinedString()}",
                e
            )
        }

        val groupName = organizationRole.groupName(organizationId)

        // As the AuthorizationService does not distinguish between technical exceptions (e.g., cannot connect to
        // Keycloak) and business exceptions (e.g., user not found), we can't do special exception handling here
        // and just let the exception propagate.
        authorizationService.removeUserFromGroup(username, groupName)
    }

    /** Get IDs for all repositories found in the products of the organization. */
    suspend fun getRepositoryIdsForOrganization(organizationId: Long): List<Long> = db.dbQuery {
        RepositoriesTable
            .innerJoin(ProductsTable)
            .select(RepositoriesTable.id)
            .where { ProductsTable.organizationId eq organizationId }
            .map { it[RepositoriesTable.id].value }
    }
}

class OrganizationNotEmptyException(message: String) : Exception(message)
