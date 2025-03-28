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

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleName
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.KeycloakTestClient
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ProductRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository

@Suppress("LargeClass")
class DefaultAuthorizationServiceTest : WordSpec({
    val keycloakGroupPrefix = "PREFIX_"

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
            repositoryRepository,
            keycloakGroupPrefix
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
            OrganizationRole.entries.forEach { role ->
                val actualRoles =
                    keycloakClient.getCompositeRoles(RoleName(role.roleName(organizationId))).map { it.name.value }
                val expectedRoles = role.permissions.map { it.roleName(organizationId) }

                actualRoles should containExactlyInAnyOrder(expectedRoles)
            }
        }

        "create a group for each role" {
            keycloakClient.getGroups().map { it.name.value } should containAll(
                OrganizationRole.getGroupsForOrganization(organizationId).map { keycloakGroupPrefix + it }
            )
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
            ProductRole.entries.forEach { role ->
                val actualRoles =
                    keycloakClient.getCompositeRoles(RoleName(role.roleName(productId))).map { it.name.value }
                val expectedRoles = role.permissions.map { it.roleName(productId) }

                actualRoles should containExactlyInAnyOrder(expectedRoles)
            }
        }

        "add the roles as composites to the parent roles" {
            ProductRole.entries.forEach { role ->
                OrganizationRole.entries.find { it.includedProductRole == role }?.let { orgRole ->
                    keycloakClient.getCompositeRoles(RoleName(orgRole.roleName(organizationId)))
                        .map { it.name.value } should contain(role.roleName(productId))
                }
            }
        }

        "create a group for each role" {
            keycloakClient.getGroups().map { it.name.value } should
                    containAll(ProductRole.getGroupsForProduct(productId).map { keycloakGroupPrefix + it })
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
            RepositoryRole.entries.forEach { role ->
                val actualRoles =
                    keycloakClient.getCompositeRoles(RoleName(role.roleName(repositoryId))).map { it.name.value }
                val expectedRoles = role.permissions.map { it.roleName(repositoryId) }

                actualRoles should containExactlyInAnyOrder(expectedRoles)
            }
        }

        "add the roles as composites to the parent roles" {
            RepositoryRole.entries.forEach { role ->
                ProductRole.entries.find { it.includedRepositoryRole == role }?.let { orgRole ->
                    keycloakClient.getCompositeRoles(RoleName(orgRole.roleName(productId)))
                        .map { it.name.value } should contain(role.roleName(repositoryId))
                }
            }
        }

        "create a group for each role" {
            keycloakClient.getGroups().map { it.name.value } should
                    containAll(RepositoryRole.getGroupsForRepository(repositoryId).map { keycloakGroupPrefix + it })
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

            keycloakClient.getGroup(GroupName(keycloakGroupPrefix + Superuser.GROUP_NAME)) shouldNot beNull()
        }

        "assign the superuser role to the superuser group" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)

            mockkTransaction { runBlocking { service.ensureSuperuser() } }

            val group = keycloakClient.getGroup(GroupName(keycloakGroupPrefix + Superuser.GROUP_NAME))
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

        "remove roles for non-existing organizations" {
            val unneededRole = "${OrganizationPermission.rolePrefix(42)}reader"
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

        "remove roles for non-existing products" {
            val unneededRole = "${ProductPermission.rolePrefix(42)}writer"
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

        "remove roles for non-existing repositories" {
            val unneededRole = "${RepositoryPermission.rolePrefix(42)}admin"
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

        "remove roles for non-existing organizations" {
            val unneededRole = "${OrganizationRole.rolePrefix(42)}reader"
            val unaffectedRole = "superuser"
            val keycloakClient = KeycloakTestClient().apply {
                createRole(RoleName(unneededRole))
                createRole(RoleName(unaffectedRole))
            }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            val remainingRoles = keycloakClient.getRoles().map { it.name.value }
            remainingRoles shouldNot contain(unneededRole)
            remainingRoles shouldContain unaffectedRole
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

        "remove roles for non-existing products" {
            val unneededRole = "${ProductRole.rolePrefix(42)}writer"
            val unaffectedRole = "superuser"
            val keycloakClient = KeycloakTestClient().apply {
                createRole(RoleName(unneededRole))
                createRole(RoleName(unaffectedRole))
            }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            val remainingRoles = keycloakClient.getRoles().map { it.name.value }
            remainingRoles shouldNot contain(unneededRole)
            remainingRoles shouldContain unaffectedRole
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

        "remove roles for non-existing repositories" {
            val unneededRole = "${RepositoryRole.rolePrefix(42)}admin"
            val unaffectedRole = "superuser"
            val keycloakClient = KeycloakTestClient().apply {
                createRole(RoleName(unneededRole))
                createRole(RoleName(unaffectedRole))
            }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)
            service.createRepositoryRoles(repositoryId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            val remainingRoles = keycloakClient.getRoles().map { it.name.value }
            remainingRoles shouldNot contain(unneededRole)
            remainingRoles shouldContain unaffectedRole
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

            keycloakClient.getGroups().map { it.name.value } should containAll(
                OrganizationRole.getGroupsForOrganization(organizationId).map { keycloakGroupPrefix + it }
            )
        }

        "create missing product groups" {
            val existingGroup = ProductRole.READER.groupName(productId)
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(existingGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } should
                    containAll(ProductRole.getGroupsForProduct(productId).map { keycloakGroupPrefix + it })
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
                    containAll(RepositoryRole.getGroupsForRepository(repositoryId).map { keycloakGroupPrefix + it })
        }

        "remove unneeded organization groups" {
            val unneededGroup = "$keycloakGroupPrefix${OrganizationRole.groupPrefix(organizationId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(unneededGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } shouldNot contain(unneededGroup)
        }

        "remove groups for non-existing organizations" {
            val unneededGroup = "$keycloakGroupPrefix${OrganizationRole.groupPrefix(42)}readers"
            val unaffectedGroup1 = "$keycloakGroupPrefix${Superuser.GROUP_NAME}"
            val unaffectedGroup2 = "otherGroupPrefix${OrganizationRole.groupPrefix(organizationId)}readers"
            val keycloakClient = KeycloakTestClient().apply {
                createGroup(GroupName(unneededGroup))
                createGroup(GroupName(unaffectedGroup1))
                createGroup(GroupName(unaffectedGroup2))
            }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            val remainingGroups = keycloakClient.getGroups().map { it.name.value }
            remainingGroups shouldNot contain(unneededGroup)
            remainingGroups shouldContainAll listOf(unaffectedGroup1, unaffectedGroup2)
        }

        "remove unneeded product groups" {
            val unneededGroup = "$keycloakGroupPrefix${ProductRole.groupPrefix(productId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(unneededGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } shouldNot contain(unneededGroup)
        }

        "remove groups for non-existing products" {
            val unneededGroup = "$keycloakGroupPrefix${ProductRole.groupPrefix(42)}writers"
            val unaffectedGroup1 = "$keycloakGroupPrefix${Superuser.GROUP_NAME}"
            val unaffectedGroup2 = "otherGroupPrefix${ProductRole.groupPrefix(productId)}writers"
            val keycloakClient = KeycloakTestClient().apply {
                createGroup(GroupName(unneededGroup))
                createGroup(GroupName(unaffectedGroup1))
                createGroup(GroupName(unaffectedGroup2))
            }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            val remainingGroups = keycloakClient.getGroups().map { it.name.value }
            remainingGroups shouldNot contain(unneededGroup)
            remainingGroups shouldContainAll listOf(unaffectedGroup1, unaffectedGroup2)
        }

        "remove unneeded repository groups" {
            val unneededGroup = "$keycloakGroupPrefix${RepositoryRole.groupPrefix(repositoryId)}unneeded"
            val keycloakClient = KeycloakTestClient().apply { createGroup(GroupName(unneededGroup)) }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            keycloakClient.getGroups().map { it.name.value } shouldNot contain(unneededGroup)
        }

        "remove groups for non-existing repositories" {
            val unneededGroup = "$keycloakGroupPrefix${RepositoryRole.groupPrefix(42)}admins"
            val unaffectedGroup1 = "$keycloakGroupPrefix${Superuser.GROUP_NAME}"
            val unaffectedGroup2 = "otherGroupPrefix${RepositoryRole.groupPrefix(repositoryId)}admins"
            val keycloakClient = KeycloakTestClient().apply {
                createGroup(GroupName(unneededGroup))
                createGroup(GroupName(unaffectedGroup1))
                createGroup(GroupName(unaffectedGroup2))
            }
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }
            service.createOrganizationRoles(organizationId)
            service.createProductRoles(productId)

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            val remainingGroups = keycloakClient.getGroups().map { it.name.value }
            remainingGroups shouldNot contain(unneededGroup)
            remainingGroups shouldContainAll listOf(unaffectedGroup1, unaffectedGroup2)
        }

        "assign the correct roles to organization groups" {
            val keycloakClient = KeycloakTestClient()
            val service = createService(keycloakClient)
            mockkTransaction { runBlocking { service.synchronizePermissions() } }

            mockkTransaction { runBlocking { service.synchronizeRoles() } }

            OrganizationRole.entries.forEach { role ->
                val group = keycloakClient.getGroup(GroupName(keycloakGroupPrefix + role.groupName(organizationId)))
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

            ProductRole.entries.forEach { role ->
                val group = keycloakClient.getGroup(GroupName(keycloakGroupPrefix + role.groupName(productId)))
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

            RepositoryRole.entries.forEach { role ->
                val group = keycloakClient.getGroup(GroupName(keycloakGroupPrefix + role.groupName(repositoryId)))
                keycloakClient.getGroupClientRoles(group.id).map { it.name.value } should
                        contain(role.roleName(repositoryId))
            }
        }
    }
})
