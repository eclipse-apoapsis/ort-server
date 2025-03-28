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

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser

/**
 * A service to manage roles and permissions in Keycloak.
 */
@Suppress("TooManyFunctions")
interface AuthorizationService {
    /**
     * Create the [permissions][OrganizationPermission.getRolesForOrganization] for the provided [organizationId].
     */
    suspend fun createOrganizationPermissions(organizationId: Long)

    /**
     * Delete the [permissions][OrganizationPermission.getRolesForOrganization] for the provided [organizationId].
     */
    suspend fun deleteOrganizationPermissions(organizationId: Long)

    /** Create the [roles][OrganizationRole.getRolesForOrganization] for the provided [organizationId]. */
    suspend fun createOrganizationRoles(organizationId: Long)

    /** Delete the [roles][OrganizationRole.getRolesForOrganization] for the provided [organizationId]. */
    suspend fun deleteOrganizationRoles(organizationId: Long)

    /**
     * Create the [permissions][ProductPermission.getRolesForProduct] for the provided [productId].
     */
    suspend fun createProductPermissions(productId: Long)

    /**
     * Delete the [permissions][ProductPermission.getRolesForProduct] for the provided [productId].
     */
    suspend fun deleteProductPermissions(productId: Long)

    /** Create the [roles][ProductRole.getRolesForProduct] for the provided [productId]. */
    suspend fun createProductRoles(productId: Long)

    /** Delete the [roles][ProductRole.getRolesForProduct] for the provided [productId]. */
    suspend fun deleteProductRoles(productId: Long)

    /**
     * Create the [permissions][RepositoryPermission.getRolesForRepository] for the provided [repositoryId].
     */
    suspend fun createRepositoryPermissions(repositoryId: Long)

    /**
     * Delete the [permissions][RepositoryPermission.getRolesForRepository] for the provided [repositoryId].
     */
    suspend fun deleteRepositoryPermissions(repositoryId: Long)

    /** Create the [roles][RepositoryRole.getRolesForRepository] for the provided [repositoryId]. */
    suspend fun createRepositoryRoles(repositoryId: Long)

    /** Delete the [roles][RepositoryRole.getRolesForRepository] for the provided [repositoryId]. */
    suspend fun deleteRepositoryRoles(repositoryId: Long)

    /**
     * Ensure that the [Superuser.ROLE_NAME] and [Superuser.GROUP_NAME] exist and that the group grants the role.
     */
    suspend fun ensureSuperuser()

    /**
     * Synchronize the permissions in Keycloak with the database entities to ensure that the correct Keycloak roles
     * exist. This is required for the following scenarios:
     * * The roles in Keycloak were manually changed.
     * * The permission definitions have changed and therefore the Keycloak roles created when creating the database
     *   entities are not correct anymore.
     */
    suspend fun synchronizePermissions()

    /**
     * Synchronize the roles and groups in Keycloak with the database entities to ensure that the correct Keycloak roles
     * and groups exist. This is required for the following scenarios:
     * * The roles or groups in Keycloak were manually changed.
     * * The role definitions have changed and therefore the Keycloak roles and groups created when creating the
     *   database entities are not correct anymore.
     */
    suspend fun synchronizeRoles()

    /**
     * Combines [ensureSuperuser], [synchronizeRoles] and [synchronizePermissions] with some logging
     */
    suspend fun ensureSuperuserAndSynchronizeRolesAndPermissions()

    /**
     * Add a user [username] to the group with the given [groupName].
     */
    suspend fun addUserToGroup(username: String, groupName: String)

    /**
     * Remove a user [username] from a group with the given [groupName].
     */
    suspend fun removeUserFromGroup(username: String, groupName: String)
}
