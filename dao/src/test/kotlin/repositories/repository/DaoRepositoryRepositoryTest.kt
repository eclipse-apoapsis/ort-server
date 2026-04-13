/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.v1.dao.exceptions.EntityNotFoundException

class DaoRepositoryRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var repositoryRepository: DaoRepositoryRepository
    lateinit var fixtures: Fixtures

    var orgId = -1L
    var productId = -1L

    beforeEach {
        repositoryRepository = dbExtension.fixtures.repositoryRepository
        fixtures = dbExtension.fixtures

        orgId = fixtures.organization.id
        productId = fixtures.product.id
    }

    "create should create an entry in the database" {
        val type = RepositoryType.GIT
        val url = "https://example.com/repo.git"
        val description = "description"

        val createdRepository = repositoryRepository.create(type, url, productId, description = description)

        val dbEntry = repositoryRepository.get(createdRepository.id)

        dbEntry shouldBe Repository(
            id = createdRepository.id,
            organizationId = orgId,
            productId = productId,
            type = type,
            url = url,
            description = description
        )
    }

    "create should throw an exception if a repository with the same url exists" {
        val type = RepositoryType.GIT
        val url = "https://example.com/repo.git"
        val description = "description"

        repositoryRepository.create(type, url, productId, description = description)

        shouldThrow<UniqueConstraintException> {
            repositoryRepository.create(type, url, productId, description = description)
        }
    }

    "create should persist a non-null name" {
        val repository = repositoryRepository.create(
            type = RepositoryType.GIT,
            url = "https://example.com/named-repo.git",
            productId = productId,
            name = "Named repository",
            description = "description"
        )

        repository.name shouldBe "Named repository"
        repositoryRepository.get(repository.id)?.name shouldBe "Named repository"
    }

    "create should persist a null name" {
        val repository = repositoryRepository.create(
            type = RepositoryType.GIT,
            url = "https://example.com/unnamed-repo.git",
            productId = productId,
            name = null,
            description = "description"
        )

        repository.name.shouldBeNull()
        repositoryRepository.get(repository.id)?.name.shouldBeNull()
    }

    "list should retrieve all entities from the database" {
        val prod2 = fixtures.createProduct(name = "prod2")

        val repo1 = fixtures.createRepository(url = "https://example.org/repo1.git")
        val repo2 = fixtures.createRepository(url = "https://example.org/repo2.git", productId = prod2.id)

        repositoryRepository.list() shouldBe ListQueryResult(
            data = listOf(repo1, repo2),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply parameters" {
        val prod2 = fixtures.createProduct(name = "prod2")

        fixtures.createRepository(url = "https://example.org/repo1.git")
        val repo2 = fixtures.createRepository(url = "https://example.org/repo2.git", productId = prod2.id)

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("url", OrderDirection.DESCENDING)),
            limit = 1
        )

        repositoryRepository.list(parameters) shouldBe ListQueryResult(
            data = listOf(repo2),
            params = parameters,
            totalCount = 2
        )
    }
    "list should filter with regex ending pattern" {
        fixtures.createProduct(name = "product1")

        val repo1 = fixtures.createRepository(url = "https://example.com/auth-repository.git")
        val repo2 = fixtures.createRepository(url = "https://example.com/user-repository.git")
        fixtures.createRepository(url = "https://example.com/repo3.git")
        fixtures.createRepository(url = "https://example.com/repo4.git")

        repositoryRepository.list(urlFilter = FilterParameter("repository.git$")) shouldBe ListQueryResult(
            data = listOf(
                Repository(
                    id = repo1.id,
                    organizationId = orgId,
                    productId = repo1.productId,
                    type = repo1.type,
                    url = repo1.url,
                    description = repo1.description
                ),
                Repository(
                    id = repo2.id,
                    organizationId = orgId,
                    productId = repo2.productId,
                    type = repo2.type,
                    url = repo2.url,
                    description = repo2.description
                )
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should filter with example com domain pattern" {
        fixtures.createProduct(name = "product")

        val repo1 = fixtures.createRepository(url = "https://example.com/auth-repository.git")
        val repo2 = fixtures.createRepository(url = "https://example.com/core-service.git")
        fixtures.createRepository(url = "https://github.com/user/repo.git")

        val repo4 = fixtures.createRepository(
            url = "https://subdomain.example.com/repo.git"
        )

        val result = repositoryRepository.list(urlFilter = FilterParameter("example\\.com"))

        result shouldBe ListQueryResult(
            data = listOf(
                Repository(
                    id = repo1.id,
                    organizationId = orgId,
                    productId = repo1.productId,
                    type = repo1.type,
                    url = repo1.url,
                    description = repo1.description
                ),
                Repository(
                    id = repo2.id,
                    organizationId = orgId,
                    productId = repo2.productId,
                    type = repo2.type,
                    url = repo2.url,
                    description = repo2.description
                ),
                Repository(
                    id = repo4.id,
                    organizationId = orgId,
                    productId = repo4.productId,
                    type = repo4.type,
                    url = repo4.url,
                    description = repo4.description
                )
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 3
        )
    }

    "list should filter by repository name as well as url" {
        val namedRepository = fixtures.createRepository(
            url = "https://example.com/repo1.git",
            name = "Internal tools"
        )
        fixtures.createRepository(
            url = "https://example.com/repo2.git",
            name = "Customer portal"
        )

        repositoryRepository.list(urlFilter = FilterParameter("^Internal")) shouldBe ListQueryResult(
            data = listOf(namedRepository),
            params = ListQueryParameters.DEFAULT,
            totalCount = 1
        )
    }

    "list should apply a hierarchy filter on repository level" {
        val repo1 = fixtures.createRepository(url = "https://example.com/repo1.git")
        val repo1Id = CompoundHierarchyId.forRepository(
            OrganizationId(fixtures.organization.id),
            ProductId(fixtures.product.id),
            RepositoryId(repo1.id)
        )
        val repo2 = fixtures.createRepository(url = "https://example.com/repo2.git")
        val repo2Id = CompoundHierarchyId.forRepository(
            OrganizationId(fixtures.organization.id),
            ProductId(fixtures.product.id),
            RepositoryId(repo2.id)
        )
        fixtures.createRepository(url = "https://example.com/repo3.git")

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.REPOSITORY to listOf(repo1Id, repo2Id)),
            nonTransitiveIncludes = emptyMap()
        )
        val result = repositoryRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(
                Repository(
                    id = repo1.id,
                    organizationId = orgId,
                    productId = repo1.productId,
                    type = repo1.type,
                    url = repo1.url,
                    description = repo1.description
                ),
                Repository(
                    id = repo2.id,
                    organizationId = orgId,
                    productId = repo2.productId,
                    type = repo2.type,
                    url = repo2.url,
                    description = repo2.description
                )
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply a hierarchy filter on product level" {
        val product1 = fixtures.createProduct(name = "product1")
        val product1Id = CompoundHierarchyId.forProduct(
            OrganizationId(fixtures.organization.id),
            ProductId(product1.id)
        )
        val repo1 = fixtures.createRepository(url = "https://example.com/repo1.git", productId = product1.id)
        val product2 = fixtures.createProduct(name = "product2")
        val product2Id = CompoundHierarchyId.forProduct(
            OrganizationId(fixtures.organization.id),
            ProductId(product2.id)
        )
        val repo2 = fixtures.createRepository(url = "https://example.com/repo2.git", productId = product2.id)
        fixtures.createRepository(url = "https://example.com/repo3.git")

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.PRODUCT to listOf(product1Id, product2Id)),
            nonTransitiveIncludes = emptyMap()
        )
        val result = repositoryRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(
                Repository(
                    id = repo1.id,
                    organizationId = orgId,
                    productId = repo1.productId,
                    type = repo1.type,
                    url = repo1.url,
                    description = repo1.description
                ),
                Repository(
                    id = repo2.id,
                    organizationId = orgId,
                    productId = repo2.productId,
                    type = repo2.type,
                    url = repo2.url,
                    description = repo2.description
                )
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply a hierarchy filter on organization level" {
        val organization1 = fixtures.createOrganization(name = "testOrganization")
        val organization1Id = CompoundHierarchyId.forOrganization(OrganizationId(organization1.id))
        val product1 = fixtures.createProduct(name = "product1", organizationId = organization1.id)
        val repo1 = fixtures.createRepository(url = "https://example.com/repo1.git", productId = product1.id)

        val organization2 = fixtures.createOrganization(name = "organization2")
        val organization2Id = CompoundHierarchyId.forOrganization(OrganizationId(organization2.id))
        val product2 = fixtures.createProduct(name = "product2", organizationId = organization2.id)
        val repo2 = fixtures.createRepository(url = "https://example.com/repo2.git", productId = product2.id)

        fixtures.createRepository(url = "https://example.com/repo3.git")

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(
                HierarchyLevel.ORGANIZATION to listOf(
                    organization1Id,
                    organization2Id
                )
            ),
            nonTransitiveIncludes = emptyMap()
        )
        val result = repositoryRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(
                Repository(
                    id = repo1.id,
                    organizationId = organization1.id,
                    productId = repo1.productId,
                    type = repo1.type,
                    url = repo1.url,
                    description = repo1.description
                ),
                Repository(
                    id = repo2.id,
                    organizationId = organization2.id,
                    productId = repo2.productId,
                    type = repo2.type,
                    url = repo2.url,
                    description = repo2.description
                )
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "listForProduct should return all repositories for a product" {
        val type = RepositoryType.GIT

        val url1 = "https://example.com/repo1.git"
        val url2 = "https://example.com/repo2.git"
        val description = "description"

        val createdRepository1 = repositoryRepository.create(type, url1, productId, description = description)
        val createdRepository2 = repositoryRepository.create(type, url2, productId, description = description)

        repositoryRepository.listForProduct(productId) shouldBe
                ListQueryResult(
                    data = listOf(
                        Repository(
                            id = createdRepository1.id,
                            organizationId = orgId,
                            productId = productId,
                            type = type,
                            url = url1,
                            description = description
                        ),
                        Repository(
                            id = createdRepository2.id,
                            organizationId = orgId,
                            productId = productId,
                            type = type,
                            url = url2,
                            description = description
                        )
                    ),
                    params = ListQueryParameters.DEFAULT,
                    totalCount = 2
                )
    }

    "listForProduct should apply query parameters" {
        val type = RepositoryType.GIT

        val url1 = "https://example.com/repo1.git"
        val url2 = "https://example.com/repo2.git"
        val description = "description"

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("url", OrderDirection.DESCENDING)),
            limit = 1
        )

        repositoryRepository.create(type, url1, productId, description = description)
        val createdRepository2 = repositoryRepository.create(type, url2, productId, description = description)

        repositoryRepository.listForProduct(productId, parameters) shouldBe
                ListQueryResult(
                    data = listOf(
                        Repository(
                            id = createdRepository2.id,
                            organizationId = orgId,
                            productId = productId,
                            type = type,
                            url = url2,
                            description = description
                        )
                    ),
                    params = parameters,
                    totalCount = 2
                )
    }

    "listForProduct should apply query parameters and filter parameter" {
        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("url", OrderDirection.DESCENDING)),
            limit = 4
        )

        val otherProd = fixtures.createProduct(name = "otherProduct")

        val repo1 = fixtures.createRepository(url = "https://example.com/auth-repository.git", productId = productId)
        fixtures.createRepository(url = "https://example.com/user-repository.git", productId = otherProd.id)
        val repo3 = fixtures.createRepository(url = "https://example.com/service-repository.git", productId = productId)
        fixtures.createRepository(url = "https://example.com/repo4.git")

        repositoryRepository.listForProduct(productId, parameters, FilterParameter("repository.git$")) shouldBe
                ListQueryResult(
                    data = listOf(
                        repo3,
                        repo1
                    ),
                    params = parameters,
                    totalCount = 2
                )
    }

    "listForProduct should filter by repository name as well as url" {
        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("url", OrderDirection.DESCENDING)),
            limit = 10
        )

        val otherProd = fixtures.createProduct(name = "otherProduct")

        val namedRepository = fixtures.createRepository(
            url = "https://example.com/repo1.git",
            productId = productId,
            name = "Repository display name"
        )
        fixtures.createRepository(
            url = "https://example.com/repo2.git",
            productId = productId,
            name = "Another repository"
        )
        fixtures.createRepository(
            url = "https://example.com/repo3.git",
            productId = otherProd.id,
            name = "Repository display name"
        )

        repositoryRepository.listForProduct(productId, parameters, FilterParameter("^Repository display")) shouldBe
                ListQueryResult(
                    data = listOf(namedRepository),
                    params = parameters,
                    totalCount = 1
                )
    }

    "update should update an entry in the database" {
        val createdRepository = repositoryRepository.create(
            RepositoryType.GIT,
            "https://example.com/repo.git",
            productId,
            description = null
        )

        val updateType = RepositoryType.SUBVERSION.asPresent()
        val updateUrl = "https://svn.example.com/repos/org/repo/trunk".asPresent()

        val updateRepositoryResult = repositoryRepository.update(createdRepository.id, updateType, updateUrl)

        updateRepositoryResult shouldBe Repository(
            id = createdRepository.id,
            organizationId = orgId,
            productId = productId,
            type = updateType.value,
            url = updateUrl.value
        )

        repositoryRepository.get(createdRepository.id) shouldBe Repository(
            id = createdRepository.id,
            organizationId = orgId,
            productId = productId,
            type = updateType.value,
            url = updateUrl.value
        )
    }

    "update should move a repository to another product" {
        val createdRepository = repositoryRepository.create(
            RepositoryType.GIT,
            "https://example.com/repo.git",
            productId,
            description = null
        )
        val newProduct = fixtures.createProduct(name = "newProduct")

        val updateRepositoryResult = repositoryRepository.update(
            createdRepository.id,
            productId = newProduct.id.asPresent()
        )

        updateRepositoryResult shouldBe Repository(
            id = createdRepository.id,
            organizationId = newProduct.organizationId,
            productId = newProduct.id,
            type = createdRepository.type,
            url = createdRepository.url
        )

        repositoryRepository.get(createdRepository.id) shouldBe Repository(
            id = createdRepository.id,
            organizationId = newProduct.organizationId,
            productId = newProduct.id,
            type = createdRepository.type,
            url = createdRepository.url
        )
    }

    "update should set a repository name" {
        val repository = fixtures.createRepository(url = "https://example.com/name-update.git", name = null)

        val updatedRepository = repositoryRepository.update(
            id = repository.id,
            name = "Updated name".asPresent()
        )

        updatedRepository.name shouldBe "Updated name"
        repositoryRepository.get(repository.id)?.name shouldBe "Updated name"
    }

    "update should clear a repository name" {
        val repository = fixtures.createRepository(url = "https://example.com/name-clear.git", name = "Initial name")

        val updatedRepository = repositoryRepository.update(
            id = repository.id,
            name = null.asPresent()
        )

        updatedRepository.name.shouldBeNull()
        repositoryRepository.get(repository.id)?.name.shouldBeNull()
    }

    "update should throw an exception if a repository with the same url exists" {
        val type = RepositoryType.GIT

        val url1 = "https://example.com/repo1.git"
        val url2 = "https://example.com/repo2.git"
        val description = "description"

        repositoryRepository.create(type, url1, productId, description = description)
        val createdRepository2 = repositoryRepository.create(type, url2, productId, description = description)

        val updateType = OptionalValue.Absent
        val updateUrl = url1.asPresent()

        shouldThrow<UniqueConstraintException> {
            repositoryRepository.update(createdRepository2.id, updateType, updateUrl)
        }
    }

    "update should throw an exception when moving a repository and there is a conflict with the url" {
        val url = "https://example.com/repo.git"
        val createdRepository =
            repositoryRepository.create(RepositoryType.GIT, url, productId, description = null)

        val newProduct = fixtures.createProduct(name = "newProduct")
        repositoryRepository.create(RepositoryType.GIT, url, newProduct.id, description = null)

        shouldThrow<UniqueConstraintException> {
            repositoryRepository.update(
                createdRepository.id,
                productId = newProduct.id.asPresent()
            )
        }
    }

    "delete should delete the database entry" {
        val createdRepository =
            repositoryRepository.create(
                RepositoryType.GIT,
                "https://example.com/repo.git",
                productId,
                description = "description"
            )

        repositoryRepository.delete(createdRepository.id)

        repositoryRepository.listForProduct(productId).data should beEmpty()
    }

    "get should return null" {
        repositoryRepository.get(1L).shouldBeNull()
    }

    "get should return the repository" {
        val repository = repositoryRepository.create(
            RepositoryType.GIT,
            "https://example.com/repo.git",
            productId,
            description = "description"
        )

        repositoryRepository.get(repository.id) shouldBe repository
    }

    "getHierarchy should return the structure of the repository" {
        val repository = repositoryRepository.create(
            RepositoryType.GIT,
            "https://example.com/repo.git",
            productId,
            description = "description"
        )

        val expectedHierarchy = Hierarchy(repository, fixtures.product, fixtures.organization)

        repositoryRepository.getHierarchy(repository.id) shouldBe expectedHierarchy
    }

    "getHierarchy should throw an exception for a non-existing repository" {
        shouldThrow<EntityNotFoundException> {
            repositoryRepository.getHierarchy(1L)
        }
    }
})
