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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.server.clients.keycloak.KeycloakClient
import org.ossreviewtoolkit.server.clients.keycloak.RoleName
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestClient
import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.OrganizationRole
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.ProductRole
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryRole
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository

class DefaultAuthorizationServiceTest : WordSpec({
    val organizationId = 1L
    val productId = 2L
    val repositoryId = 3L

    "createOrganizationPermissions" should {
        "create the correct Keycloak roles" {
            val keycloakClient = KeycloakTestClient()

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

            service.createOrganizationPermissions(organizationId)

            keycloakClient.getRoles().map { it.name.value } should
                    containExactlyInAnyOrder(OrganizationPermission.getRolesForOrganization(organizationId))
        }
    }

    "deleteOrganizationPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = KeycloakTestClient()

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())
            service.createOrganizationPermissions(organizationId)

            service.deleteOrganizationPermissions(organizationId)

            keycloakClient.getRoles() should beEmpty()
        }
    }

    "createOrganizationRoles" should {
        val keycloakClient = KeycloakTestClient()

        val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())
        service.createOrganizationPermissions(organizationId)

        service.createOrganizationRoles(organizationId)

        "create the correct Keycloak roles" {
            keycloakClient.getRoles().map { it.name.value } should
                    containAll(OrganizationRole.getRolesForOrganization(organizationId))
        }

        "add the correct permission roles as composites" {
            OrganizationRole.values().forEach { role ->
                val actualRoles =
                    keycloakClient.getCompositeRoles(RoleName(role.roleName(organizationId))).map { it.name.value }
                val expectedRoles = role.permissions.map { it.roleName(organizationId) }

                actualRoles should containExactlyInAnyOrder(expectedRoles)
            }
        }

        "create a group for each role" {
            keycloakClient.getGroups().map { it.name.value } should
                    containAll(OrganizationRole.getGroupsForOrganization(organizationId))
        }
    }

    "deleteOrganizationRoles" should {
        val keycloakClient = KeycloakTestClient()

        val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())
        service.createOrganizationPermissions(organizationId)
        service.createOrganizationRoles(organizationId)

        service.deleteOrganizationRoles(organizationId)

        "delete the correct Keycloak roles" {
            keycloakClient.getRoles().map { it.name.value } shouldNot
                    containAnyOf(OrganizationRole.getRolesForOrganization(organizationId))
        }

        "delete the Keycloak groups" {
            keycloakClient.getGroups().map { it.name.value } shouldNot
                    containAnyOf(OrganizationRole.getGroupsForOrganization(organizationId))
        }
    }

    "createProductPermissions" should {
        "create the correct Keycloak roles" {
            val keycloakClient = KeycloakTestClient()

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

            service.createProductPermissions(productId)

            keycloakClient.getRoles().map { it.name.value } should
                    containExactlyInAnyOrder(ProductPermission.getRolesForProduct(productId))
        }
    }

    "deleteProductPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = KeycloakTestClient()

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())
            service.createProductPermissions(productId)

            service.deleteProductPermissions(productId)

            keycloakClient.getRoles() should beEmpty()
        }
    }

    "createProductRoles" should {
        val keycloakClient = KeycloakTestClient()

        val organizationRepository = mockk<OrganizationRepository> {
            every { this@mockk.get(organizationId) } returns Organization(id = organizationId, name = "organization")
        }

        val productRepository = mockk<ProductRepository> {
            every { this@mockk.get(productId) } returns
                    Product(id = productId, organizationId = organizationId, name = "product")
        }

        val service =
            DefaultAuthorizationService(keycloakClient, mockk(), organizationRepository, productRepository, mockk())
        service.createOrganizationPermissions(organizationId)
        service.createOrganizationRoles(organizationId)
        service.createProductPermissions(productId)

        service.createProductRoles(productId)

        "create the correct Keycloak roles" {
            keycloakClient.getRoles().map { it.name.value } should
                    containAll(ProductRole.getRolesForProduct(productId))
        }

        "add the correct permission roles as composites" {
            ProductRole.values().forEach { role ->
                val actualRoles =
                    keycloakClient.getCompositeRoles(RoleName(role.roleName(productId))).map { it.name.value }
                val expectedRoles = role.permissions.map { it.roleName(productId) }

                actualRoles should containExactlyInAnyOrder(expectedRoles)
            }
        }

        "add the roles as composites to the parent roles" {
            ProductRole.values().forEach { role ->
                OrganizationRole.values().find { it.includedProductRole == role }?.let { orgRole ->
                    keycloakClient.getCompositeRoles(RoleName(orgRole.roleName(organizationId)))
                        .map { it.name.value } should contain(role.roleName(productId))
                }
            }
        }

        "create a group for each role" {
            keycloakClient.getGroups().map { it.name.value } should
                    containAll(ProductRole.getGroupsForProduct(productId))
        }
    }

    "deleteProductRoles" should {
        val keycloakClient = KeycloakTestClient()

        val organizationRepository = mockk<OrganizationRepository> {
            every { this@mockk.get(organizationId) } returns Organization(id = organizationId, name = "organization")
        }

        val productRepository = mockk<ProductRepository> {
            every { this@mockk.get(productId) } returns
                    Product(id = productId, organizationId = organizationId, name = "product")
        }

        val service =
            DefaultAuthorizationService(keycloakClient, mockk(), organizationRepository, productRepository, mockk())
        service.createOrganizationPermissions(organizationId)
        service.createOrganizationRoles(organizationId)
        service.createProductPermissions(productId)
        service.createProductRoles(productId)

        service.deleteProductRoles(productId)

        "delete the correct Keycloak roles" {
            keycloakClient.getRoles().map { it.name.value } shouldNot
                    containAnyOf(ProductRole.getRolesForProduct(productId))
        }

        "delete the Keycloak groups" {
            keycloakClient.getGroups().map { it.name.value } shouldNot
                    containAnyOf(ProductRole.getGroupsForProduct(productId))
        }
    }

    "createRepositoryPermissions" should {
        "create the correct Keycloak roles" {
            val keycloakClient = KeycloakTestClient()

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())

            service.createRepositoryPermissions(repositoryId)

            keycloakClient.getRoles().map { it.name.value } should
                    containExactlyInAnyOrder(RepositoryPermission.getRolesForRepository(repositoryId))
        }
    }

    "deleteRepositoryPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = KeycloakTestClient()

            val service = DefaultAuthorizationService(keycloakClient, mockk(), mockk(), mockk(), mockk())
            service.createRepositoryPermissions(repositoryId)

            service.deleteRepositoryPermissions(repositoryId)

            keycloakClient.getRoles() should beEmpty()
        }
    }

    "createRepositoryRoles" should {
        val keycloakClient = KeycloakTestClient()

        val organizationRepository = mockk<OrganizationRepository> {
            every { this@mockk.get(organizationId) } returns Organization(id = organizationId, name = "organization")
        }

        val productRepository = mockk<ProductRepository> {
            every { this@mockk.get(productId) } returns
                    Product(id = productId, organizationId = organizationId, name = "product")
        }

        val repositoryRepository = mockk<RepositoryRepository> {
            every { this@mockk.get(repositoryId) } returns
                    Repository(
                        id = repositoryId,
                        organizationId = organizationId,
                        productId = productId,
                        type = RepositoryType.GIT,
                        url = "https://example.com/repo.git"
                    )
        }

        val service = DefaultAuthorizationService(
            keycloakClient,
            mockk(),
            organizationRepository,
            productRepository,
            repositoryRepository
        )
        service.createOrganizationPermissions(organizationId)
        service.createOrganizationRoles(organizationId)
        service.createProductPermissions(productId)
        service.createProductRoles(productId)
        service.createRepositoryPermissions(repositoryId)

        service.createRepositoryRoles(repositoryId)

        "create the correct Keycloak roles" {
            keycloakClient.getRoles().map { it.name.value } should
                    containAll(RepositoryRole.getRolesForRepository(repositoryId))
        }

        "add the correct permission roles as composites" {
            RepositoryRole.values().forEach { role ->
                val actualRoles =
                    keycloakClient.getCompositeRoles(RoleName(role.roleName(repositoryId))).map { it.name.value }
                val expectedRoles = role.permissions.map { it.roleName(repositoryId) }

                actualRoles should containExactlyInAnyOrder(expectedRoles)
            }
        }

        "add the roles as composites to the parent roles" {
            RepositoryRole.values().forEach { role ->
                ProductRole.values().find { it.includedRepositoryRole == role }?.let { orgRole ->
                    keycloakClient.getCompositeRoles(RoleName(orgRole.roleName(productId)))
                        .map { it.name.value } should contain(role.roleName(repositoryId))
                }
            }
        }

        "create a group for each role" {
            keycloakClient.getGroups().map { it.name.value } should
                    containAll(RepositoryRole.getGroupsForRepository(repositoryId))
        }
    }

    "deleteRepositoryRoles" should {
        val keycloakClient = KeycloakTestClient()

        val organizationRepository = mockk<OrganizationRepository> {
            every { this@mockk.get(organizationId) } returns Organization(id = organizationId, name = "organization")
        }

        val productRepository = mockk<ProductRepository> {
            every { this@mockk.get(productId) } returns
                    Product(id = productId, organizationId = organizationId, name = "product")
        }

        val repositoryRepository = mockk<RepositoryRepository> {
            every { this@mockk.get(repositoryId) } returns
                    Repository(
                        id = repositoryId,
                        organizationId = organizationId,
                        productId = productId,
                        type = RepositoryType.GIT,
                        url = "https://example.com/repo.git"
                    )
        }

        val service = DefaultAuthorizationService(
            keycloakClient,
            mockk(),
            organizationRepository,
            productRepository,
            repositoryRepository
        )
        service.createOrganizationPermissions(organizationId)
        service.createOrganizationRoles(organizationId)
        service.createProductPermissions(productId)
        service.createProductRoles(productId)
        service.createRepositoryPermissions(repositoryId)
        service.createRepositoryRoles(repositoryId)

        service.deleteRepositoryRoles(repositoryId)

        "delete the correct Keycloak roles" {
            keycloakClient.getRoles().map { it.name.value } shouldNot
                    containAnyOf(RepositoryRole.getRolesForRepository(repositoryId))
        }

        "delete the Keycloak groups" {
            keycloakClient.getGroups().map { it.name.value } shouldNot
                    containAnyOf(RepositoryRole.getGroupsForRepository(repositoryId))
        }
    }

    "synchronizePermissions" should {
        val org = Organization(id = 1L, name = "org")
        val prod = Product(id = 2L, organizationId = org.id, name = "prod")
        val repo = Repository(
            id = 3L,
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
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(OrganizationPermission.getRolesForOrganization(organizationId))
        }

        "create missing product roles" {
            val existingRole = ProductPermission.READ.roleName(prod.id)
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(ProductPermission.getRolesForProduct(productId))
        }

        "create missing repository roles" {
            val existingRole = RepositoryPermission.READ.roleName(repo.id)
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(RepositoryPermission.getRolesForRepository(repositoryId))
        }

        "remove unneeded organization roles" {
            val unneededRole = "${OrganizationPermission.rolePrefix(org.id)}_unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }

        "remove unneeded product roles" {
            val unneededRole = "${ProductPermission.rolePrefix(prod.id)}_unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }

        "remove unneeded repository roles" {
            val unneededRole = "${RepositoryPermission.rolePrefix(repo.id)}_unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }
    }
})
