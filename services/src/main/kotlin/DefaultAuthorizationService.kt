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

import org.ossreviewtoolkit.server.clients.keycloak.KeycloakClient
import org.ossreviewtoolkit.server.clients.keycloak.RoleName
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission

internal const val ROLE_DESCRIPTION = "This role is auto-generated, do not edit or remove."

/**
 * The default implementation of [AuthorizationService], using a [keycloakClient] to manage Keycloak roles.
 */
class DefaultAuthorizationService(
    private val keycloakClient: KeycloakClient
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
}
