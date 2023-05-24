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

import io.kotest.core.spec.style.WordSpec

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

import org.ossreviewtoolkit.server.clients.keycloak.KeycloakClient
import org.ossreviewtoolkit.server.clients.keycloak.RoleName
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission

class DefaultAuthorizationServiceTest : WordSpec({
    val organizationId = 1L
    val productId = 2L
    val repositoryId = 3L

    "createOrganizationPermissions" should {
        "create the correct Keycloak roles" {
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
            }

            val service = DefaultAuthorizationService(keycloakClient)

            service.createOrganizationPermissions(organizationId)

            coVerify(exactly = 1) {
                OrganizationPermission.getRolesForOrganization(organizationId).forEach {
                    keycloakClient.createRole(RoleName(it), ROLE_DESCRIPTION)
                }
            }
        }
    }

    "deleteOrganizationPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { deleteRole(any()) } returns mockk()
            }

            val service = DefaultAuthorizationService(keycloakClient)

            service.deleteOrganizationPermissions(organizationId)

            coVerify(exactly = 1) {
                OrganizationPermission.getRolesForOrganization(organizationId).forEach {
                    keycloakClient.deleteRole(RoleName(it))
                }
            }
        }
    }

    "createProductPermissions" should {
        "create the correct Keycloak roles" {
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
            }

            val service = DefaultAuthorizationService(keycloakClient)

            service.createProductPermissions(productId)

            coVerify(exactly = 1) {
                ProductPermission.getRolesForProduct(productId).forEach {
                    keycloakClient.createRole(RoleName(it), ROLE_DESCRIPTION)
                }
            }
        }
    }

    "deleteProductPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { deleteRole(any()) } returns mockk()
            }

            val service = DefaultAuthorizationService(keycloakClient)

            service.deleteProductPermissions(productId)

            coVerify(exactly = 1) {
                ProductPermission.getRolesForProduct(productId).forEach {
                    keycloakClient.deleteRole(RoleName(it))
                }
            }
        }
    }

    "createRepositoryPermissions" should {
        "create the correct Keycloak roles" {
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
            }

            val service = DefaultAuthorizationService(keycloakClient)

            service.createRepositoryPermissions(repositoryId)

            coVerify(exactly = 1) {
                RepositoryPermission.getRolesForRepository(repositoryId).forEach {
                    keycloakClient.createRole(RoleName(it), ROLE_DESCRIPTION)
                }
            }
        }
    }

    "deleteRepositoryPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { deleteRole(any()) } returns mockk()
            }

            val service = DefaultAuthorizationService(keycloakClient)

            service.deleteRepositoryPermissions(repositoryId)

            coVerify(exactly = 1) {
                RepositoryPermission.getRolesForRepository(repositoryId).forEach {
                    keycloakClient.deleteRole(RoleName(it))
                }
            }
        }
    }
})
