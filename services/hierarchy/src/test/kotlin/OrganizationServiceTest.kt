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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.DaoOrganizationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.product.DaoProductRepository
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter

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

    "getRepositoryIdsForOrganization" should {
        "return IDs for all repositories found in the products of the organization" {
            val service = OrganizationService(db, organizationRepository, productRepository, mockk())

            val orgId = fixtures.createOrganization().id

            val prod1Id = fixtures.createProduct(organizationId = orgId).id
            val prod2Id = fixtures.createProduct("Prod2", organizationId = orgId).id

            val repo1Id = fixtures.createRepository(productId = prod1Id).id
            val repo2Id = fixtures.createRepository(url = "https://example.com/repo2.git", productId = prod2Id).id
            val repo3Id = fixtures.createRepository(url = "https://example.com/repo3.git", productId = prod2Id).id

            service.getRepositoryIdsForOrganization(orgId).shouldContainExactlyInAnyOrder(repo1Id, repo2Id, repo3Id)
        }
    }

    "listOrganizationsForUser" should {
        "filter for organizations visible to a specific user" {
            val userId = "test-user"
            val org1Id = fixtures.organization.id
            val org2 = fixtures.createOrganization(name = "Org2")
            val org2Id = org2.id
            val orgHierarchyIds = listOf(org1Id, org2Id).map { id ->
                CompoundHierarchyId.forOrganization(OrganizationId(id))
            }
            fixtures.createOrganization(name = "HiddenOrg").id

            val authService = mockk<AuthorizationService> {
                coEvery {
                    filterHierarchyIds(userId, OrganizationRole.READER)
                } returns HierarchyFilter(
                    transitiveIncludes = mapOf(CompoundHierarchyId.ORGANIZATION_LEVEL to orgHierarchyIds),
                    nonTransitiveIncludes = emptyMap()
                )
            }

            val service = OrganizationService(db, organizationRepository, productRepository, authService)
            val organizations = service.listOrganizationsForUser(userId)

            organizations.totalCount shouldBe 2
            organizations.data shouldContainExactlyInAnyOrder listOf(fixtures.organization, org2)
        }
    }
})
