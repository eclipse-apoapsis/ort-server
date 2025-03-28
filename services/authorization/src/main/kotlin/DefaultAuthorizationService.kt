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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleName
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ProductRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.util.removePrefixOrNull

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DefaultAuthorizationService::class.java)

internal const val ROLE_DESCRIPTION = "This role is auto-generated, do not edit or remove."

/**
 * The default implementation of [AuthorizationService], using a [keycloakClient] to manage Keycloak roles.
 */
@Suppress("TooManyFunctions")
class DefaultAuthorizationService(
    private val keycloakClient: KeycloakClient,
    private val db: Database,
    private val organizationRepository: OrganizationRepository,
    private val productRepository: ProductRepository,
    private val repositoryRepository: RepositoryRepository,

    /**
     * A prefix for Keycloak group names, to be used when multiple instances of ORT Server share the same Keycloak
     * realm.
     */
    private val keycloakGroupPrefix: String
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

    override suspend fun createOrganizationRoles(organizationId: Long) {
        OrganizationRole.entries.forEach { role ->
            val roleName = RoleName(role.roleName(organizationId))
            keycloakClient.createRole(name = roleName, description = ROLE_DESCRIPTION)
            role.permissions.forEach { permission ->
                val compositeRole = keycloakClient.getRole(RoleName(permission.roleName(organizationId)))
                keycloakClient.addCompositeRole(roleName, compositeRole.id)
            }

            val groupName = GroupName(keycloakGroupPrefix + role.groupName(organizationId))
            keycloakClient.createGroup(groupName)
            keycloakClient.addGroupClientRole(keycloakClient.getGroup(groupName).id, keycloakClient.getRole(roleName))
        }
    }

    override suspend fun deleteOrganizationRoles(organizationId: Long) {
        OrganizationRole.entries.forEach { role ->
            keycloakClient.deleteRole(RoleName(role.roleName(organizationId)))
            keycloakClient.deleteGroup(
                keycloakClient.getGroup(GroupName(keycloakGroupPrefix + role.groupName(organizationId))).id
            )
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

    override suspend fun createProductRoles(productId: Long) {
        val product = checkNotNull(productRepository.get(productId))
        val organization = checkNotNull(organizationRepository.get(product.organizationId))

        ProductRole.entries.forEach { role ->
            val roleName = RoleName(role.roleName(productId))
            keycloakClient.createRole(name = roleName, description = ROLE_DESCRIPTION)
            role.permissions.forEach { permission ->
                val compositeRole = keycloakClient.getRole(RoleName(permission.roleName(productId)))
                keycloakClient.addCompositeRole(roleName, compositeRole.id)
            }

            OrganizationRole.entries.find { it.includedProductRole == role }?.let { orgRole ->
                val parentRole = keycloakClient.getRole(RoleName(orgRole.roleName(organization.id)))
                val childRole = keycloakClient.getRole(roleName)
                keycloakClient.addCompositeRole(parentRole.name, childRole.id)
            }

            val groupName = GroupName(keycloakGroupPrefix + role.groupName(productId))
            keycloakClient.createGroup(groupName)
            keycloakClient.addGroupClientRole(keycloakClient.getGroup(groupName).id, keycloakClient.getRole(roleName))
        }
    }

    override suspend fun deleteProductRoles(productId: Long) {
        ProductRole.entries.forEach { role ->
            keycloakClient.deleteRole(RoleName(role.roleName(productId)))
            keycloakClient.deleteGroup(
                keycloakClient.getGroup(GroupName(keycloakGroupPrefix + role.groupName(productId))).id
            )
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

    override suspend fun createRepositoryRoles(repositoryId: Long) {
        val repository = checkNotNull(repositoryRepository.get(repositoryId))
        val product = checkNotNull(productRepository.get(repository.productId))

        RepositoryRole.entries.forEach { role ->
            val roleName = RoleName(role.roleName(repositoryId))
            keycloakClient.createRole(name = roleName, description = ROLE_DESCRIPTION)
            role.permissions.forEach { permission ->
                val compositeRole = keycloakClient.getRole(RoleName(permission.roleName(repositoryId)))
                keycloakClient.addCompositeRole(roleName, compositeRole.id)
            }

            ProductRole.entries.find { it.includedRepositoryRole == role }?.let { productRole ->
                val parentRole = keycloakClient.getRole(RoleName(productRole.roleName(product.id)))
                val childRole = keycloakClient.getRole(roleName)
                keycloakClient.addCompositeRole(parentRole.name, childRole.id)
            }

            val groupName = GroupName(keycloakGroupPrefix + role.groupName(repositoryId))
            keycloakClient.createGroup(groupName)
            keycloakClient.addGroupClientRole(keycloakClient.getGroup(groupName).id, keycloakClient.getRole(roleName))
        }
    }

    override suspend fun deleteRepositoryRoles(repositoryId: Long) {
        RepositoryRole.entries.forEach { role ->
            keycloakClient.deleteRole(RoleName(role.roleName(repositoryId)))
            keycloakClient.deleteGroup(
                keycloakClient.getGroup(GroupName(keycloakGroupPrefix + role.groupName(repositoryId))).id
            )
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

    /**
     * Synchronize the [organization permissions][OrganizationPermission] with the Keycloak roles by adding missing
     * roles and removing obsolete roles that start with the
     * [organization permission prefix][OrganizationPermission.rolePrefix], based on the provided list of existing
     * Keycloak [roles].
     */
    private suspend fun synchronizeOrganizationPermissions(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for organization permissions.")

        runCatching {
            val organizationIds = db.dbQuery { organizationRepository.list() }.mapTo(mutableSetOf()) { it.id }

            organizationIds.forEach { organizationId ->
                val requiredRoles = OrganizationPermission.getRolesForOrganization(organizationId)
                val rolePrefix = OrganizationPermission.rolePrefix(organizationId)
                synchronizeKeycloakRoles(roles, requiredRoles, rolePrefix)
            }

            removeRoles(
                roles.filter {
                    val roleOrganizationId = OrganizationPermission.extractOrganizationIdFromRole(it)
                    roleOrganizationId != null && roleOrganizationId !in organizationIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for organization permissions.", it)
        }.getOrThrow()
    }

    /**
     * Synchronize the [product permissions][ProductPermission] with the Keycloak roles by adding missing roles and
     * removing obsolete roles that start with the
     * [product permission prefix][ProductPermission.rolePrefix], based on the provided list of existing
     * Keycloak [roles].
     */
    private suspend fun synchronizeProductPermissions(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for product permissions.")

        runCatching {
            val productIds = db.dbQuery { productRepository.list() }.mapTo(mutableSetOf()) { it.id }

            productIds.forEach { productId ->
                val requiredRoles = ProductPermission.getRolesForProduct(productId)
                val rolePrefix = ProductPermission.rolePrefix(productId)
                synchronizeKeycloakRoles(roles, requiredRoles, rolePrefix)
            }

            removeRoles(
                roles.filter {
                    val roleProductId = ProductPermission.extractProductIdFromRole(it)
                    roleProductId != null && roleProductId !in productIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for product permissions.", it)
        }.getOrThrow()
    }

    /**
     * Synchronize the [repository permissions][RepositoryPermission] with the Keycloak roles by adding missing roles
     * and removing obsolete roles that start with the
     * [repository permission prefix][RepositoryPermission.rolePrefix], based on the provided list of existing
     * Keycloak [roles].
     */
    private suspend fun synchronizeRepositoryPermissions(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for repository permissions.")

        runCatching {
            val repositoryIds = db.dbQuery { repositoryRepository.list() }.mapTo(mutableSetOf()) { it.id }

            repositoryIds.forEach { repository ->
                val requiredRoles = RepositoryPermission.getRolesForRepository(repository)
                val rolePrefix = RepositoryPermission.rolePrefix(repository)
                synchronizeKeycloakRoles(roles, requiredRoles, rolePrefix)
            }

            removeRoles(
                roles.filter {
                    val roleRepositoryId = RepositoryPermission.extractRepositoryIdFromRole(it)
                    roleRepositoryId != null && roleRepositoryId !in repositoryIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for repository permissions.", it)
        }.getOrThrow()
    }

    override suspend fun synchronizeRoles() {
        withContext(Dispatchers.IO) {
            val roles = keycloakClient.getRoles().mapTo(mutableSetOf()) { it.name.value }
            val groups = keycloakClient.getGroups().map { it.name.value }
                .filterTo(mutableSetOf()) { it.startsWith(keycloakGroupPrefix) }

            synchronizeOrganizationRoles(roles)
            synchronizeOrganizationGroups(groups)
            synchronizeProductRoles(roles)
            synchronizeProductGroups(groups)
            synchronizeRepositoryRoles(roles)
            synchronizeRepositoryGroups(groups)
        }
    }

    override suspend fun ensureSuperuserAndSynchronizeRolesAndPermissions() {
        runCatching {
            logger.info("Ensuring superuser role and group.")
            this.ensureSuperuser()
            logger.info("Synchronizing Keycloak permissions.")
            this.synchronizePermissions()
            logger.info("Synchronizing Keycloak roles.")
            this.synchronizeRoles()
        }.onSuccess {
            logger.info("Synchronized Keycloak permissions and roles.")
        }.onFailure {
            logger.error("Error while synchronizing Keycloak permissions and roles.", it)
        }
    }

    /**
     * Synchronize the [organization roles][OrganizationRole] with the Keycloak roles by adding missing roles, removing
     * obsolete roles that start with the [organization role prefix][OrganizationRole.rolePrefix], and adding the
     * [defined permission roles][OrganizationRole.permissions] as composite roles, based on the provided list of
     * existing Keycloak [roles].
     */
    private suspend fun synchronizeOrganizationRoles(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for organization roles.")

        runCatching {
            val organizationIds = db.dbQuery { organizationRepository.list().mapTo(mutableSetOf()) { it.id } }

            organizationIds.forEach { organizationId ->
                // Make sure that all roles exist.
                val requiredRoles = OrganizationRole.getRolesForOrganization(organizationId)
                val rolePrefix = OrganizationRole.rolePrefix(organizationId)
                synchronizeKeycloakRoles(roles, requiredRoles, rolePrefix)

                // Make sure that roles have the correct composite roles.
                OrganizationRole.entries.forEach { role ->
                    val roleName = RoleName(role.roleName(organizationId))
                    val requiredCompositeRoles = role.permissions.map { it.roleName(organizationId) }
                    val actualCompositeRoles = keycloakClient.getCompositeRoles(roleName).map { it.name.value }
                    val compositeRolePrefix = OrganizationPermission.rolePrefix(organizationId)
                    synchronizeKeycloakCompositeRoles(
                        roleName,
                        requiredCompositeRoles,
                        actualCompositeRoles,
                        compositeRolePrefix
                    )
                }
            }

            // Make sure that roles for non-existing organizations are removed.
            removeRoles(
                roles.filter {
                    val roleOrganizationId = OrganizationRole.extractOrganizationIdFromRole(it)
                    roleOrganizationId != null && roleOrganizationId !in organizationIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for organization roles.", it)
        }.getOrThrow()
    }

    /**
     * Synchronize the [product roles][ProductRole] with the Keycloak roles by adding missing roles, removing obsolete
     * roles that start with the [product role prefix][ProductRole.rolePrefix], adding the product roles as composites
     * to the related [organization roles][OrganizationRole.includedProductRole], and adding the
     * [defined permission roles][ProductRole.permissions] as composite roles, based on the provided list of
     * existing Keycloak [roles].
     */
    private suspend fun synchronizeProductRoles(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for product roles.")

        runCatching {
            val products = db.dbQuery { productRepository.list() }

            products.forEach { product ->
                // Make sure that all roles exist.
                val requiredRoles = ProductRole.getRolesForProduct(product.id)
                val rolePrefix = ProductRole.rolePrefix(product.id)
                synchronizeKeycloakRoles(roles, requiredRoles, rolePrefix)

                // Make sure that roles have the correct composite roles.
                ProductRole.entries.forEach { role ->
                    val roleName = RoleName(role.roleName(product.id))
                    val requiredCompositeRoles = role.permissions.map { it.roleName(product.id) }
                    val actualCompositeRoles = keycloakClient.getCompositeRoles(roleName).map { it.name.value }
                    val compositeRolePrefix = ProductPermission.rolePrefix(product.id)
                    synchronizeKeycloakCompositeRoles(
                        roleName,
                        requiredCompositeRoles,
                        actualCompositeRoles,
                        compositeRolePrefix
                    )
                }

                // Make sure that the roles are added as composites to the related organization roles.
                ProductRole.entries.forEach { role ->
                    val roleName = RoleName(role.roleName(product.id))
                    OrganizationRole.entries.find { it.includedProductRole == role }
                        ?.let { organizationRole ->
                            val organizationRoleName = RoleName(organizationRole.roleName(product.organizationId))
                            val compositeRoles =
                                keycloakClient.getCompositeRoles(organizationRoleName).map { it.name.value }
                            synchronizeKeycloakCompositeRoles(
                                organizationRoleName,
                                listOf(roleName.value),
                                compositeRoles,
                                rolePrefix
                            )
                        }
                }
            }

            // Make sure that roles for non-existing products are removed.
            val productIds = products.mapTo(mutableSetOf()) { it.id }
            removeRoles(
                roles.filter {
                    val roleProductId = ProductRole.extractProductIdFromRole(it)
                    roleProductId != null && roleProductId !in productIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for product roles.", it)
        }.getOrThrow()
    }

    /**
     * Synchronize the [repository roles][RepositoryRole] with the Keycloak roles by adding missing roles, removing
     * obsolete roles that start with the [repository role prefix][RepositoryRole.rolePrefix], adding the repository
     * roles as composites to the related [product roles][ProductRole.includedRepositoryRole], and adding the
     * [defined permission roles][RepositoryRole.permissions] as composite roles, based on the provided list of
     * existing Keycloak [roles].
     */
    private suspend fun synchronizeRepositoryRoles(roles: Set<String>) {
        logger.info("Synchronizing Keycloak roles for repository roles.")

        runCatching {
            val repositories = db.dbQuery { repositoryRepository.list() }

            repositories.forEach { repository ->
                // Make sure that all roles exist.
                val requiredRoles = RepositoryRole.getRolesForRepository(repository.id)
                val rolePrefix = RepositoryRole.rolePrefix(repository.id)
                synchronizeKeycloakRoles(roles, requiredRoles, rolePrefix)

                // Make sure that roles have the correct composite roles.
                RepositoryRole.entries.forEach { role ->
                    val roleName = RoleName(role.roleName(repository.id))
                    val requiredCompositeRoles = role.permissions.map { it.roleName(repository.id) }
                    val actualCompositeRoles = keycloakClient.getCompositeRoles(roleName).map { it.name.value }
                    val compositeRolePrefix = RepositoryPermission.rolePrefix(repository.id)
                    synchronizeKeycloakCompositeRoles(
                        roleName,
                        requiredCompositeRoles,
                        actualCompositeRoles,
                        compositeRolePrefix
                    )
                }

                // Make sure that the roles are added as composites to the related product roles.
                RepositoryRole.entries.forEach { role ->
                    val roleName = RoleName(role.roleName(repository.id))
                    ProductRole.entries.find { it.includedRepositoryRole == role }
                        ?.let { productRole ->
                            val productRoleName = RoleName(productRole.roleName(repository.productId))
                            val compositeRoles = keycloakClient.getCompositeRoles(productRoleName).map { it.name.value }
                            synchronizeKeycloakCompositeRoles(
                                productRoleName,
                                listOf(roleName.value),
                                compositeRoles,
                                rolePrefix
                            )
                        }
                }
            }

            // Make sure that roles for non-existing repositories are removed.
            val repositoryIds = repositories.mapTo(mutableSetOf()) { it.id }
            removeRoles(
                roles.filter {
                    val roleRepositoryId = RepositoryRole.extractRepositoryIdFromRole(it)
                    roleRepositoryId != null && roleRepositoryId !in repositoryIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak roles for repository roles.", it)
        }.getOrThrow()
    }

    /**
     * Synchronize the [organization roles][OrganizationRole] with the Keycloak groups by adding missing groups and
     * removing obsolete groups that start with the
     * [organization group prefix][OrganizationRole.groupPrefix], based on the provided list of existing Keycloak
     * [groups].
     */
    private suspend fun synchronizeOrganizationGroups(groups: Set<String>) {
        logger.info("Synchronizing Keycloak groups for organization roles.")

        runCatching {
            val organizationIds = db.dbQuery { organizationRepository.list().mapTo(mutableSetOf()) { it.id } }

            organizationIds.forEach { organizationId ->
                // Make sure that all groups exist.
                val requiredGroups =
                    OrganizationRole.getGroupsForOrganization(organizationId).map { keycloakGroupPrefix + it }
                val groupPrefix = keycloakGroupPrefix + OrganizationRole.groupPrefix(organizationId)
                synchronizeKeycloakGroups(groups, requiredGroups, groupPrefix)

                // Make sure that groups have the correct role assigned.
                OrganizationRole.entries.forEach { role ->
                    synchronizeKeycloakGroupRole(
                        keycloakGroupPrefix + role.groupName(organizationId),
                        role.roleName(organizationId),
                        OrganizationRole.groupPrefix(organizationId)
                    )
                }
            }

            // Make sure that groups for non-existing organizations are removed.
            removeGroups(
                groups.filter {
                    val groupId = it.removePrefixOrNull(keycloakGroupPrefix)
                        ?.let(OrganizationRole::extractOrganizationIdFromGroup)
                    groupId != null && groupId !in organizationIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak groups for organization roles.", it)
        }.getOrThrow()
    }

    /**
     * Synchronize the [product roles][ProductRole] with the Keycloak groups by adding missing groups and removing
     * obsolete groups that start with the
     * [product group prefix][ProductRole.groupPrefix], based on the provided list of existing Keycloak [groups].
     */
    private suspend fun synchronizeProductGroups(groups: Set<String>) {
        logger.info("Synchronizing Keycloak groups for product roles.")

        runCatching {
            val productIds = db.dbQuery { productRepository.list().mapTo(mutableSetOf()) { it.id } }

            productIds.forEach { productId ->
                // Make sure that all groups exist.
                val requiredGroups = ProductRole.getGroupsForProduct(productId).map { keycloakGroupPrefix + it }
                val groupPrefix = keycloakGroupPrefix + ProductRole.groupPrefix(productId)
                synchronizeKeycloakGroups(groups, requiredGroups, groupPrefix)

                // Make sure that groups have the correct role assigned.
                ProductRole.entries.forEach { role ->
                    synchronizeKeycloakGroupRole(
                        keycloakGroupPrefix + role.groupName(productId),
                        role.roleName(productId),
                        ProductRole.groupPrefix(productId)
                    )
                }
            }

            // Make sure that groups for non-existing products are removed.
            removeGroups(
                groups.filter {
                    val groupId = it.removePrefixOrNull(keycloakGroupPrefix)
                        ?.let(ProductRole::extractProductIdFromGroup)
                    groupId != null && groupId !in productIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak groups for product roles.", it)
        }.getOrThrow()
    }

    /**
     * Synchronize the [repository roles][RepositoryRole] with the Keycloak groups by adding missing groups and
     * removing obsolete groups that start with the
     * [repository group prefix][RepositoryRole.groupPrefix], based on the provided list of existing Keycloak
     * [groups].
     */
    private suspend fun synchronizeRepositoryGroups(groups: Set<String>) {
        logger.info("Synchronizing Keycloak groups for repository roles.")

        runCatching {
            val repositoryIds = db.dbQuery { repositoryRepository.list().mapTo(mutableSetOf()) { it.id } }

            repositoryIds.forEach { repositoryId ->
                // Make sure that all groups exist.
                val requiredGroups =
                    RepositoryRole.getGroupsForRepository(repositoryId).map { keycloakGroupPrefix + it }
                val groupPrefix = keycloakGroupPrefix + RepositoryRole.groupPrefix(repositoryId)
                synchronizeKeycloakGroups(groups, requiredGroups, groupPrefix)

                // Make sure that groups have the correct role assigned.
                RepositoryRole.entries.forEach { role ->
                    synchronizeKeycloakGroupRole(
                        keycloakGroupPrefix + role.groupName(repositoryId),
                        role.roleName(repositoryId),
                        RepositoryRole.groupPrefix(repositoryId)
                    )
                }
            }

            // Make sure that groups for non-existing repositories are removed.
            removeGroups(
                groups.filter {
                    val groupId = it.removePrefixOrNull(keycloakGroupPrefix)
                        ?.let(RepositoryRole::extractRepositoryIdFromGroup)
                    groupId != null && groupId !in repositoryIds
                }
            )
        }.onFailure {
            logger.error("Error while synchronizing Keycloak groups for repository roles.", it)
        }.getOrThrow()
    }

    /**
     * Create Keycloak roles for all roles in [requiredRoles] that are not contained in [roles], and delete all Keycloak
     * roles which are not contained in [roles] and start with [rolePrefix].
     */
    private suspend fun synchronizeKeycloakRoles(roles: Set<String>, requiredRoles: List<String>, rolePrefix: String) {
        val missingRoles = requiredRoles.filter { it !in roles }
        if (missingRoles.isNotEmpty()) {
            logger.info("Creating missing roles: ${missingRoles.joinToString()}")
        }
        missingRoles.forEach { role ->
            keycloakClient.createRole(RoleName(role), ROLE_DESCRIPTION)
        }

        removeRoles(roles.filter { it.startsWith(rolePrefix) }.filter { it !in requiredRoles })
    }

    /**
     * Remove the given [unneededRoles] from Keycloak.
     */
    private suspend fun removeRoles(unneededRoles: Collection<String>) {
        if (unneededRoles.isNotEmpty()) {
            logger.info("Removing unneeded roles: ${unneededRoles.joinToString()}")
        }
        unneededRoles.forEach { role ->
            keycloakClient.deleteRole(RoleName(role))
        }
    }

    /**
     * Add all roles in [requiredCompositeRoles] that are not contained in [actualCompositeRoles] as composite roles to
     * the provided [role][roleName], and remove all composite roles from the provided [role][roleName] which are not
     * contained in [requiredCompositeRoles] and start with [rolePrefix].
     */
    private suspend fun synchronizeKeycloakCompositeRoles(
        roleName: RoleName,
        requiredCompositeRoles: List<String>,
        actualCompositeRoles: List<String>,
        rolePrefix: String
    ) {
        val missingCompositeRoles = requiredCompositeRoles.filter { it !in actualCompositeRoles }
        if (missingCompositeRoles.isNotEmpty()) {
            logger.info("Adding missing composite roles to ${roleName.value}: ${missingCompositeRoles.joinToString()}")
        }
        missingCompositeRoles.forEach { role ->
            keycloakClient.addCompositeRole(roleName, keycloakClient.getRole(RoleName(role)).id)
        }

        val unneededCompositeRoles =
            actualCompositeRoles.filter { it.startsWith(rolePrefix) }.filter { it !in requiredCompositeRoles }
        if (unneededCompositeRoles.isNotEmpty()) {
            logger.info(
                "Removing unneeded composite roles from ${roleName.value}: ${unneededCompositeRoles.joinToString()}"
            )
        }
        unneededCompositeRoles.forEach { role ->
            keycloakClient.removeCompositeRole(roleName, keycloakClient.getRole(RoleName(role)).id)
        }
    }

    override suspend fun ensureSuperuser() {
        val roleName = RoleName(Superuser.ROLE_NAME)
        val groupName = GroupName(keycloakGroupPrefix + Superuser.GROUP_NAME)

        runCatching {
            keycloakClient.getRole(roleName)
        }.onFailure {
            logger.info("Creating '${roleName.value}' role.")
            keycloakClient.createRole(name = roleName, description = ROLE_DESCRIPTION)
        }

        runCatching {
            keycloakClient.getGroup(groupName)
        }.onFailure {
            logger.info("Creating '${groupName.value}' group.")
            keycloakClient.createGroup(groupName)
        }

        val group = keycloakClient.getGroup(groupName)
        val roles = keycloakClient.getGroupClientRoles(group.id).map { it.name }
        if (roleName !in roles) {
            logger.info("Adding role '${roleName.value}' to group '${groupName.value}'.")
            val role = keycloakClient.getRole(roleName)
            keycloakClient.addGroupClientRole(group.id, role)
        }
    }

    /**
     * Create Keycloak groups for all groups in [requiredGroups] that are not contained in [groups], and delete all
     * Keycloak groups which are not contained in [groups] and start with [groupPrefix].
     */
    private suspend fun synchronizeKeycloakGroups(
        groups: Set<String>,
        requiredGroups: List<String>,
        groupPrefix: String
    ) {
        val missingGroups = requiredGroups.filter { it !in groups }
        if (missingGroups.isNotEmpty()) {
            logger.info("Creating missing groups: ${missingGroups.joinToString()}")
        }
        missingGroups.forEach { group ->
            keycloakClient.createGroup(GroupName(group))
        }

        removeGroups(groups.filter { it.startsWith(groupPrefix) }.filter { it !in requiredGroups })
    }

    /**
     * Remove the given [unneededGroups] from Keycloak.
     */
    private suspend fun removeGroups(unneededGroups: Collection<String>) {
        if (unneededGroups.isNotEmpty()) {
            logger.info("Removing unneeded groups: ${unneededGroups.joinToString()}")
        }
        unneededGroups.forEach { group ->
            keycloakClient.deleteGroup(keycloakClient.getGroup(GroupName(group)).id)
        }
    }

    /**
     * Add the provided [role][roleName] to the provided [group][groupName] if the group does not already have that
     * role, and remove all roles from the provided [group][groupName] that start with [groupPrefix].
     */
    private suspend fun synchronizeKeycloakGroupRole(groupName: String, roleName: String, groupPrefix: String) {
        val group = keycloakClient.getGroup(GroupName(groupName))
        val actualGroupRoles = keycloakClient.getGroupClientRoles(group.id)

        if (roleName !in actualGroupRoles.map { it.name.value }) {
            keycloakClient.addGroupClientRole(group.id, keycloakClient.getRole(RoleName(roleName)))
        }

        actualGroupRoles.filter { it.name.value.startsWith(groupPrefix) }.filter { it.name.value != roleName }.forEach {
            keycloakClient.removeGroupClientRole(group.id, it)
        }
    }

    override suspend fun addUserToGroup(
        username: String,
        groupName: String
    ) {
        val group = keycloakGroupPrefix + groupName // Allow multiple ORT instances to share the same Keycloak realm
        keycloakClient.addUserToGroup(UserName(username), GroupName(group))
    }

    override suspend fun removeUserFromGroup(
        username: String,
        groupName: String
    ) {
        val group = keycloakGroupPrefix + groupName // Allow multiple ORT instances to share the same Keycloak realm
        keycloakClient.removeUserFromGroup(UserName(username), GroupName(group))
    }
}
