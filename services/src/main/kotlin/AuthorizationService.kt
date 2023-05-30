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

import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission

/**
 * A service to manage roles and permissions in Keycloak.
 */
interface AuthorizationService {
    /**
     * Create the [permissions][OrganizationPermission.getRolesForOrganization] for the provided [organizationId].
     */
    suspend fun createOrganizationPermissions(organizationId: Long)

    /**
     * Delete the [permissions][OrganizationPermission.getRolesForOrganization] for the provided [organizationId].
     */
    suspend fun deleteOrganizationPermissions(organizationId: Long)

    /**
     * Create the [permissions][ProductPermission.getRolesForProduct] for the provided [productId].
     */
    suspend fun createProductPermissions(productId: Long)

    /**
     * Delete the [permissions][ProductPermission.getRolesForProduct] for the provided [productId].
     */
    suspend fun deleteProductPermissions(productId: Long)

    /**
     * Create the [permissions][RepositoryPermission.getRolesForRepository] for the provided [repositoryId].
     */
    suspend fun createRepositoryPermissions(repositoryId: Long)

    /**
     * Delete the [permissions][RepositoryPermission.getRolesForRepository] for the provided [repositoryId].
     */
    suspend fun deleteRepositoryPermissions(repositoryId: Long)

    /**
     * Synchronize the permissions in Keycloak with the database entities to ensure that the correct Keycloak roles
     * exist. This is required for the following scenarios:
     * * The roles in Keycloak were manually changed.
     * * The permission definitions have changed and therefore the Keycloak roles created when creating the database
     *   entities are not correct anymore.
     */
    suspend fun synchronizePermissions()
}
