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

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.WordSpec

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.server.clients.keycloak.KeycloakClient
import org.ossreviewtoolkit.server.clients.keycloak.Role
import org.ossreviewtoolkit.server.clients.keycloak.RoleId
import org.ossreviewtoolkit.server.clients.keycloak.RoleName
import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository

class DefaultAuthorizationServiceTest : WordSpec({
    val organizationId = 1L
    val productId = 2L
    val repositoryId = 3L

    "createOrganizationPermissions" should {
        "create the correct Keycloak roles" {
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
            }

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

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

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

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

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

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

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

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

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

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

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

            service.deleteRepositoryPermissions(repositoryId)

            coVerify(exactly = 1) {
                RepositoryPermission.getRolesForRepository(repositoryId).forEach {
                    keycloakClient.deleteRole(RoleName(it))
                }
            }
        }
    }

    "synchronizePermissions" should {
        val org = Organization(id = 1L, name = "org")
        val prod = Product(id = 1L, organizationId = org.id, name = "prod")
        val repo = Repository(
            id = 1L,
            organizationId = org.id,
            productId = prod.id,
            type = RepositoryType.GIT,
            url = "https://example.org/repo.git"
        )

        val organizationRepository = mockk<OrganizationRepository> {
            every { list(any()) } returns listOf(org)
        }

        val productRepository = mockk<ProductRepository> {
            every { list(any()) } returns listOf(prod)
        }

        val repositoryRepository = mockk<RepositoryRepository> {
            every { list(any()) } returns listOf(repo)
        }

        fun createService(keycloakClient: KeycloakClient) =
            DefaultAuthorizationService(
                keycloakClient,
                mockk(),
                organizationRepository,
                productRepository,
                repositoryRepository
            )

        "create missing organization roles" {
            val existingRole = OrganizationPermission.READ.roleName(org.id)

            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
                coEvery { getRoles() } returns setOf(Role(id = RoleId("id"), RoleName(existingRole)))
            }

            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            coVerify(exactly = 0) {
                keycloakClient.createRole(RoleName(existingRole), any())
            }

            coVerify(exactly = 1) {
                (OrganizationPermission.getRolesForOrganization(org.id) - existingRole).forEach {
                    keycloakClient.createRole(RoleName(it), ROLE_DESCRIPTION)
                }
            }
        }

        "create missing product roles" {
            val existingRole = ProductPermission.READ.roleName(prod.id)

            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
                coEvery { getRoles() } returns setOf(Role(id = RoleId("id"), RoleName(existingRole)))
            }

            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            coVerify(exactly = 0) {
                keycloakClient.createRole(RoleName(existingRole), any())
            }

            coVerify(exactly = 1) {
                (ProductPermission.getRolesForProduct(prod.id) - existingRole).forEach {
                    keycloakClient.createRole(RoleName(it), ROLE_DESCRIPTION)
                }
            }
        }

        "create missing repository roles" {
            val existingRole = RepositoryPermission.READ.roleName(repo.id)

            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
                coEvery { getRoles() } returns setOf(Role(id = RoleId("id"), RoleName(existingRole)))
            }

            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            coVerify(exactly = 0) {
                keycloakClient.createRole(RoleName(existingRole), any())
            }

            coVerify(exactly = 1) {
                (RepositoryPermission.getRolesForRepository(repo.id) - existingRole).forEach {
                    keycloakClient.createRole(RoleName(it), ROLE_DESCRIPTION)
                }
            }
        }

        "remove unneeded organization roles" {
            val unneededRole = "${OrganizationPermission.rolePrefix(org.id)}_unneeded"

            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
                coEvery { deleteRole(any()) } returns mockk()
                coEvery { getRoles() } returns setOf(Role(id = RoleId("id"), RoleName(unneededRole)))
            }

            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            coVerify(exactly = 1) {
                keycloakClient.deleteRole(RoleName(unneededRole))
            }
        }

        "remove unneeded product roles" {
            val unneededRole = "${ProductPermission.rolePrefix(prod.id)}_unneeded"

            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
                coEvery { deleteRole(any()) } returns mockk()
                coEvery { getRoles() } returns setOf(Role(id = RoleId("id"), RoleName(unneededRole)))
            }

            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            coVerify(exactly = 1) {
                keycloakClient.deleteRole(RoleName(unneededRole))
            }
        }

        "remove unneeded repository roles" {
            val unneededRole = "${RepositoryPermission.rolePrefix(repo.id)}_unneeded"

            val keycloakClient = mockk<KeycloakClient> {
                coEvery { createRole(any(), any()) } returns mockk()
                coEvery { deleteRole(any()) } returns mockk()
                coEvery { getRoles() } returns setOf(Role(id = RoleId("id"), RoleName(unneededRole)))
            }

            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            coVerify(exactly = 1) {
                keycloakClient.deleteRole(RoleName(unneededRole))
            }
        }
    }
})
