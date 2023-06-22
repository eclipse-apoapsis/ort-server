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
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.server.clients.keycloak.GroupName
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
import org.ossreviewtoolkit.server.model.authorization.Superuser
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository

class DefaultAuthorizationServiceTest : WordSpec({
    val organizationId = 1L
    val productId = 2L
    val repositoryId = 3L

    val organization = Organization(id = organizationId, name = "org")
    val product = Product(id = productId, organizationId = organization.id, name = "prod")
    val repository = Repository(
        id = repositoryId,
        organizationId = organization.id,
        productId = product.id,
        type = RepositoryType.GIT,
        url = "https://example.org/repo.git"
    )

    val organizationRepository = mockk<OrganizationRepository> {
        every { this@mockk.get(organizationId) } returns Organization(id = organizationId, name = "organization")
        every { list(any()) } returns listOf(organization)
    }

    val productRepository = mockk<ProductRepository> {
        every { this@mockk.get(productId) } returns
                Product(id = productId, organizationId = organizationId, name = "product")
        every { list(any()) } returns listOf(product)
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
        every { list(any()) } returns listOf(repository)
    }

    fun createService(keycloakClient: KeycloakClient) =
        DefaultAuthorizationService(
            keycloakClient,
            mockk(),
            organizationRepository,
            productRepository,
            repositoryRepository
        )

    "createOrganizationPermissions" should {
        "create the correct Keycloak roles" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)

            service.createOrganizationPermissions(organizationId)

            keycloakClient.getRoles().map { it.name.value } should
                    containExactlyInAnyOrder(OrganizationPermission.getRolesForOrganization(organizationId))
        }
    }

    "deleteOrganizationPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            service.createOrganizationPermissions(organizationId)

            service.deleteOrganizationPermissions(organizationId)

            keycloakClient.getRoles() should beEmpty()
        }
    }

    "createOrganizationRoles" should {
        val keycloakClient = KeycloakTestClient()
        val service = createService(keycloakClient)
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
        val service = createService(keycloakClient)
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
            val service = createService(keycloakClient)

            service.createProductPermissions(productId)

            keycloakClient.getRoles().map { it.name.value } should
                    containExactlyInAnyOrder(ProductPermission.getRolesForProduct(productId))
        }
    }

    "deleteProductPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            service.createProductPermissions(productId)

            service.deleteProductPermissions(productId)

            keycloakClient.getRoles() should beEmpty()
        }
    }

    "createProductRoles" should {
        val keycloakClient = KeycloakTestClient()
        val service = createService(keycloakClient)
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
        val service = createService(keycloakClient)
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
            val service = createService(keycloakClient)

            service.createRepositoryPermissions(repositoryId)

            keycloakClient.getRoles().map { it.name.value } should
                    containExactlyInAnyOrder(RepositoryPermission.getRolesForRepository(repositoryId))
        }
    }

    "deleteRepositoryPermissions" should {
        "delete the correct Keycloak permissions" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            service.createRepositoryPermissions(repositoryId)

            service.deleteRepositoryPermissions(repositoryId)

            keycloakClient.getRoles() should beEmpty()
        }
    }

    "createRepositoryRoles" should {
        val keycloakClient = KeycloakTestClient()
        val service = createService(keycloakClient)
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
        val service = createService(keycloakClient)
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

    "ensureSuperuser" should {
        "create the missing superuser role" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.ensureSuperuser() } }

            keycloakClient.getRole(RoleName(Superuser.ROLE_NAME)) shouldNot beNull()
        }

        "create the missing superuser group" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.ensureSuperuser() } }

            keycloakClient.getGroup(GroupName(Superuser.GROUP_NAME)) shouldNot beNull()
        }

        "assign the superuser role to the superuser group" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.ensureSuperuser() } }

            val group = keycloakClient.getGroup(GroupName(Superuser.GROUP_NAME))
            keycloakClient.getGroupClientRoles(group.id).map { it.name.value } should
                    containExactly(Superuser.ROLE_NAME)
        }
    }

    "synchronizePermissions" should {
        "create missing organization roles" {
            val existingRole = OrganizationPermission.READ.roleName(organizationId)
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(OrganizationPermission.getRolesForOrganization(organizationId))
        }

        "create missing product roles" {
            val existingRole = ProductPermission.READ.roleName(productId)
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(ProductPermission.getRolesForProduct(productId))
        }

        "create missing repository roles" {
            val existingRole = RepositoryPermission.READ.roleName(repositoryId)
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(RepositoryPermission.getRolesForRepository(repositoryId))
        }

        "remove unneeded organization roles" {
            val unneededRole = "${OrganizationPermission.rolePrefix(organizationId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }

        "remove unneeded product roles" {
            val unneededRole = "${ProductPermission.rolePrefix(productId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }

        "remove unneeded repository roles" {
            val unneededRole = "${RepositoryPermission.rolePrefix(repositoryId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }
    }

    "synchronizeRoles" should {
        "create missing organization roles" {
            val existingRole = OrganizationRole.READER.roleName(organizationId)
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(OrganizationRole.getRolesForOrganization(organizationId))
        }

        "create missing product roles" {
            val existingRole = ProductRole.READER.roleName(productId)
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(ProductRole.getRolesForProduct(productId))
        }

        "create missing repository roles" {
            val existingRole = RepositoryRole.READER.roleName(repositoryId)
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(existingRole)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getRoles().map { it.name.value } should
                    containAll(RepositoryRole.getRolesForRepository(repositoryId))
        }

        "remove unneeded organization roles" {
            val unneededRole = "${OrganizationRole.rolePrefix(organizationId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }

        "remove unneeded product roles" {
            val unneededRole = "${ProductRole.rolePrefix(productId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }

        "remove unneeded repository roles" {
            val unneededRole = "${RepositoryRole.rolePrefix(repositoryId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createRole(RoleName(unneededRole)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)
            service.createRepositoryRoles(repositoryId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getRoles().map { it.name.value } shouldNot contain(unneededRole)
        }

        "add missing organization composite roles" {
            val missingRole = OrganizationPermission.READ.roleName(organizationId)
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            val roleName = RoleName(OrganizationRole.READER.roleName(organizationId))
            keycloakClient.removeCompositeRole(roleName, keycloakClient.getRole(RoleName(missingRole)).id)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getCompositeRoles(roleName).map { it.name.value } should contain(missingRole)
        }

        "add missing product composite roles" {
            val missingRole = ProductPermission.READ.roleName(productId)
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)
            val roleName = RoleName(ProductRole.READER.roleName(productId))
            keycloakClient.removeCompositeRole(roleName, keycloakClient.getRole(RoleName(missingRole)).id)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getCompositeRoles(roleName).map { it.name.value } should contain(missingRole)
        }

        "add missing repository composite roles" {
            val missingRole = RepositoryPermission.READ.roleName(repositoryId)
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)
            service.createRepositoryRoles(repositoryId)
            val roleName = RoleName(RepositoryRole.READER.roleName(repositoryId))
            keycloakClient.removeCompositeRole(roleName, keycloakClient.getRole(RoleName(missingRole)).id)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getCompositeRoles(roleName).map { it.name.value } should contain(missingRole)
        }

        "add the product roles as composites to the organization roles" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)
            val organizationRole = OrganizationRole.READER.roleName(organizationId)
            val missingCompositeRole = ProductRole.READER.roleName(productId)
            keycloakClient.removeCompositeRole(
                RoleName(organizationRole),
                keycloakClient.getRole(RoleName(missingCompositeRole)).id
            )

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getCompositeRoles(RoleName(organizationRole)).map { it.name.value } should
                    contain(missingCompositeRole)
        }

        "add the repository roles as composites to the product roles" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)
            service.createRepositoryRoles(repositoryId)
            val productRole = ProductRole.READER.roleName(productId)
            val missingCompositeRole = RepositoryRole.READER.roleName(repositoryId)
            keycloakClient.removeCompositeRole(
                RoleName(productRole),
                keycloakClient.getRole(RoleName(missingCompositeRole)).id
            )

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getCompositeRoles(RoleName(productRole)).map { it.name.value } should
                    contain(missingCompositeRole)
        }

        "remove unneeded organization composite roles" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            val unneededRole = "${OrganizationPermission.rolePrefix(organizationId)}unneeded"
            keycloakClient.createRole(RoleName(unneededRole))
            val roleName = RoleName(OrganizationRole.READER.roleName(organizationId))
            keycloakClient.addCompositeRole(roleName, keycloakClient.getRole(RoleName(unneededRole)).id)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getCompositeRoles(roleName).map { it.name.value } shouldNot contain(unneededRole)
        }

        "remove unneeded product composite roles" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)
            val unneededRole = "${ProductPermission.rolePrefix(productId)}unneeded"
            keycloakClient.createRole(RoleName(unneededRole))
            val roleName = RoleName(ProductRole.READER.roleName(productId))
            keycloakClient.addCompositeRole(roleName, keycloakClient.getRole(RoleName(unneededRole)).id)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getCompositeRoles(roleName).map { it.name.value } shouldNot contain(unneededRole)
        }

        "remove unneeded repository composite roles" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)
            service.createRepositoryRoles(repositoryId)
            val unneededRole = "${RepositoryPermission.rolePrefix(repositoryId)}unneeded"
            keycloakClient.createRole(RoleName(unneededRole))
            val roleName = RoleName(RepositoryRole.READER.roleName(repositoryId))
            keycloakClient.addCompositeRole(roleName, keycloakClient.getRole(RoleName(unneededRole)).id)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getCompositeRoles(roleName).map { it.name.value } shouldNot contain(unneededRole)
        }

        "create missing organization groups" {
            val existingGroup = OrganizationRole.READER.groupName(organizationId)
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(existingGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } should
                    containAll(OrganizationRole.getGroupsForOrganization(organizationId))
        }

        "create missing product groups" {
            val existingGroup = ProductRole.READER.groupName(productId)
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(existingGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } should
                    containAll(ProductRole.getGroupsForProduct(productId))
        }

        "create missing repository groups" {
            val existingGroup = RepositoryRole.READER.groupName(repositoryId)
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(existingGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } should
                    containAll(RepositoryRole.getGroupsForRepository(repositoryId))
        }

        "remove unneeded organization groups" {
            val unneededGroup = "${OrganizationRole.groupPrefix(organizationId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(unneededGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } shouldNot contain(unneededGroup)
        }

        "remove unneeded product groups" {
            val unneededGroup = "${ProductRole.groupPrefix(productId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(unneededGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } shouldNot contain(unneededGroup)
        }

        "remove unneeded repository groups" {
            val unneededGroup = "${RepositoryRole.groupPrefix(repositoryId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(unneededGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } shouldNot contain(unneededGroup)
        }

        "assign the correct roles to organization groups" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            OrganizationRole.values().forEach { role ->
                val group = keycloakClient.getGroup(GroupName(role.groupName(organizationId)))
                keycloakClient.getGroupClientRoles(group.id).map { it.name.value } should
                        contain(role.roleName(organizationId))
            }
        }

        "assign the correct roles to product groups" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            ProductRole.values().forEach { role ->
                val group = keycloakClient.getGroup(GroupName(role.groupName(productId)))
                keycloakClient.getGroupClientRoles(group.id).map { it.name.value } should
                        contain(role.roleName(productId))
            }
        }

        "assign the correct roles to repository groups" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            RepositoryRole.values().forEach { role ->
                val group = keycloakClient.getGroup(GroupName(role.groupName(repositoryId)))
                keycloakClient.getGroupClientRoles(group.id).map { it.name.value } should
                        contain(role.roleName(repositoryId))
            }
        }
    }
})
