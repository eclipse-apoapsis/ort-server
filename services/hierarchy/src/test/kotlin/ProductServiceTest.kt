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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.product.DaoProductRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.DaoRepositoryRepository
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter

import org.jetbrains.exposed.v1.jdbc.Database

class ProductServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var productRepository: DaoProductRepository
    lateinit var repositoryRepository: DaoRepositoryRepository
    lateinit var ortRunRepository: DaoOrtRunRepository
    lateinit var fixtures: Fixtures

    beforeEach {
        db = dbExtension.db
        productRepository = dbExtension.fixtures.productRepository
        repositoryRepository = dbExtension.fixtures.repositoryRepository
        ortRunRepository = dbExtension.fixtures.ortRunRepository
        fixtures = dbExtension.fixtures
    }

    "deleteProduct" should {
        "delete all repositories associated to this product" {
            val service = ProductService(db, productRepository, repositoryRepository, ortRunRepository, mockk())

            val product = fixtures.createProduct()

            val repo1 = fixtures.createRepository(url = "https://example.com/git/first.git", productId = product.id)
            val repo2 = fixtures.createRepository(url = "https://example.com/git/second.git", productId = product.id)

            fixtures.createOrtRun(repo1.id)
            fixtures.createOrtRun(repo1.id)
            fixtures.createOrtRun(repo2.id)
            fixtures.createOrtRun(repo2.id)

            fixtures.repositoryRepository.listForProduct(product.id).totalCount shouldBe 2
            fixtures.ortRunRepository.listForRepository(repo1.id).totalCount shouldBe 2
            fixtures.ortRunRepository.listForRepository(repo2.id).totalCount shouldBe 2

            service.deleteProduct(product.id)

            fixtures.repositoryRepository.listForProduct(product.id).totalCount shouldBe 0
            fixtures.ortRunRepository.listForRepository(repo1.id).totalCount shouldBe 0
            fixtures.ortRunRepository.listForRepository(repo2.id).totalCount shouldBe 0
        }
    }

    "getRepositoryIdsForProduct" should {
        "return IDs for all repositories of a product" {
            val service = ProductService(db, productRepository, repositoryRepository, ortRunRepository, mockk())

            val prodId = fixtures.createProduct().id

            val repo1Id = fixtures.createRepository(productId = prodId).id
            val repo2Id = fixtures.createRepository(url = "https://example.com/repo2.git", productId = prodId).id
            val repo3Id = fixtures.createRepository(url = "https://example.com/repo3.git", productId = prodId).id

            service.getRepositoryIdsForProduct(prodId).shouldContainExactlyInAnyOrder(repo1Id, repo2Id, repo3Id)
        }
    }

    "listRepositoriesForProductAndUser" should {
        "apply a hierarchy filter obtained from the authorization service" {
            val userId = "the-test-user"
            val repo1 = fixtures.repository
            val repo1Id = CompoundHierarchyId.forRepository(
                OrganizationId(fixtures.organization.id),
                ProductId(fixtures.product.id),
                RepositoryId(repo1.id)
            )
            val repo2 = fixtures.createRepository(url = "https://example.com/another-repo.git")
            val repo2Id = CompoundHierarchyId.forRepository(
                OrganizationId(fixtures.organization.id),
                ProductId(fixtures.product.id),
                RepositoryId(repo2.id)
            )

            val filter = HierarchyFilter(
                transitiveIncludes = mapOf(HierarchyLevel.REPOSITORY to listOf(repo1Id, repo2Id)),
                nonTransitiveIncludes = emptyMap()
            )
            val authService = mockk<AuthorizationService> {
                coEvery {
                    filterHierarchyIds(userId, RepositoryRole.READER, ProductId(fixtures.product.id))
                } returns filter
            }

            val service = ProductService(db, productRepository, repositoryRepository, ortRunRepository, authService)
            val result = service.listRepositoriesForProductAndUser(
                productId = fixtures.product.id,
                userId = userId
            )

            result.data shouldContainExactlyInAnyOrder listOf(repo1, repo2)
        }

        "return an empty list for a non-existing product ID" {
            val service = ProductService(db, productRepository, repositoryRepository, ortRunRepository, mockk())

            service.listRepositoriesForProductAndUser(-1L, "some-user").data should beEmpty()
        }
    }
})
