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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk

import org.eclipse.apoapsis.ortserver.dao.repositories.organization.DaoOrganizationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.product.DaoProductRepository
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.Organization

import org.jetbrains.exposed.sql.Database

class OrganizationServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var organizationRepository: DaoOrganizationRepository
    lateinit var productRepository: DaoProductRepository
    lateinit var fixtures: Fixtures

    beforeEach {
        db = dbExtension.db
        organizationRepository = dbExtension.fixtures.organizationRepository
        productRepository = dbExtension.fixtures.productRepository
        fixtures = dbExtension.fixtures
    }

    "createOrganization" should {
        "create Keycloak roles" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { createOrganizationPermissions(any()) } just runs
                coEvery { createOrganizationRoles(any()) } just runs
            }

            val service = OrganizationService(db, organizationRepository, productRepository, authorizationService)
            val organization = service.createOrganization("name", "description")

            coVerify(exactly = 1) {
                authorizationService.createOrganizationPermissions(organization.id)
                authorizationService.createOrganizationRoles(organization.id)
            }
        }
    }

    "createProduct" should {
        "create Keycloak roles" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { createProductPermissions(any()) } just runs
                coEvery { createProductRoles(any()) } just runs
            }

            val service = OrganizationService(db, organizationRepository, productRepository, authorizationService)
            val product = service.createProduct("name", "description", fixtures.organization.id)

            coVerify(exactly = 1) {
                authorizationService.createProductPermissions(product.id)
                authorizationService.createProductRoles(product.id)
            }
        }
    }

    "deleteOrganization" should {
        "delete Keycloak roles" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { deleteOrganizationPermissions(any()) } just runs
                coEvery { deleteOrganizationRoles(any()) } just runs
            }

            val service = OrganizationService(db, organizationRepository, productRepository, authorizationService)
            service.deleteOrganization(fixtures.organization.id)

            coVerify(exactly = 1) {
                authorizationService.deleteOrganizationPermissions(fixtures.organization.id)
                authorizationService.deleteOrganizationRoles(fixtures.organization.id)
            }
        }
    }

    "addUserToGroup" should {
        "throw an exception if the organization does not exist" {
            val service = OrganizationService(db, organizationRepository, productRepository, mockk())

            shouldThrow<ResourceNotFoundException> {
                service.addUserToGroup("username", 1, "readers")
            }
        }

        "throw an exception if the group does not exist" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                OrganizationService(
                    db,
                    organizationRepository,
                    productRepository,
                    authorizationService
                )
            ) {
                coEvery { getOrganization(any()) } returns Organization(1, "name", "description")
            }

            shouldThrow<ResourceNotFoundException> {
                service.addUserToGroup("username", 1, "viewers")
            }
        }

        "generate the Keycloak group name" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                OrganizationService(
                    db,
                    organizationRepository,
                    productRepository,
                    authorizationService
                )
            ) {
                coEvery { getOrganization(any()) } returns Organization(1, "name", "description")
            }

            service.addUserToGroup("username", 1, "readers")

            coVerify(exactly = 1) {
                authorizationService.addUserToGroup(
                    "username",
                    "ORGANIZATION_1_READERS"
                )
            }
        }
    }

    "removeUsersFromGroup" should {
        "throw an exception if the organization does not exist" {
            val service = OrganizationService(db, organizationRepository, productRepository, mockk())

            shouldThrow<ResourceNotFoundException> {
                service.removeUserFromGroup("username", 1, "readers")
            }
        }

        "throw an exception if the group does not exist" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                OrganizationService(
                    db,
                    organizationRepository,
                    productRepository,
                    authorizationService
                )
            ) {
                coEvery { getOrganization(any()) } returns Organization(1, "name", "description")
            }

            shouldThrow<ResourceNotFoundException> {
                service.removeUserFromGroup("username", 1, "viewers")
            }
        }

        "generate the Keycloak group name" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { removeUserFromGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                OrganizationService(
                    db,
                    organizationRepository,
                    productRepository,
                    authorizationService
                )
            ) {
                coEvery { getOrganization(any()) } returns Organization(1, "name", "description")
            }

            service.removeUserFromGroup("username", 1, "readers")

            coVerify(exactly = 1) {
                authorizationService.removeUserFromGroup(
                    "username",
                    "ORGANIZATION_1_READERS"
                )
            }
        }
    }
})
