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
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

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

        val createdRepository = repositoryRepository.create(type, url, productId)

        val dbEntry = repositoryRepository.get(createdRepository.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe Repository(createdRepository.id, orgId, productId, type, url)
    }

    "create should throw an exception if a repository with the same url exists" {
        val type = RepositoryType.GIT
        val url = "https://example.com/repo.git"

        repositoryRepository.create(type, url, productId)

        shouldThrow<UniqueConstraintException> {
            repositoryRepository.create(type, url, productId)
        }
    }

    "list should retrieve all entities from the database" {
        val prod2 = fixtures.createProduct(name = "prod2")

        val repo1 = fixtures.createRepository(url = "https://example.org/repo1.git")
        val repo2 = fixtures.createRepository(url = "https://example.org/repo2.git", productId = prod2.id)

        repositoryRepository.list() should containExactlyInAnyOrder(repo1, repo2)
    }

    "list should apply parameters" {
        val prod2 = fixtures.createProduct(name = "prod2")

        fixtures.createRepository(url = "https://example.org/repo1.git")
        val repo2 = fixtures.createRepository(url = "https://example.org/repo2.git", productId = prod2.id)

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("url", OrderDirection.DESCENDING)),
            limit = 1
        )

        repositoryRepository.list(parameters) should containExactly(repo2)
    }

    "listForProduct should return all repositories for a product" {
        val type = RepositoryType.GIT

        val url1 = "https://example.com/repo1.git"
        val url2 = "https://example.com/repo2.git"

        val createdRepository1 = repositoryRepository.create(type, url1, productId)
        val createdRepository2 = repositoryRepository.create(type, url2, productId)

        repositoryRepository.listForProduct(productId) shouldBe
                ListQueryResult(
                    data = listOf(
                        Repository(createdRepository1.id, orgId, productId, type, url1),
                        Repository(createdRepository2.id, orgId, productId, type, url2)
                    ),
                    params = ListQueryParameters.DEFAULT,
                    totalCount = 2
                )
    }

    "listForProduct should apply query parameters" {
        val type = RepositoryType.GIT

        val url1 = "https://example.com/repo1.git"
        val url2 = "https://example.com/repo2.git"

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("url", OrderDirection.DESCENDING)),
            limit = 1
        )

        repositoryRepository.create(type, url1, productId)
        val createdRepository2 = repositoryRepository.create(type, url2, productId)

        repositoryRepository.listForProduct(productId, parameters) shouldBe
                ListQueryResult(
                    data = listOf(Repository(createdRepository2.id, orgId, productId, type, url2)),
                    params = parameters,
                    totalCount = 2
                )
    }

    "update should update an entry in the database" {
        val createdRepository =
            repositoryRepository.create(RepositoryType.GIT, "https://example.com/repo.git", productId)

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
            url = updateUrl.value,
        )
    }

    "update should throw an exception if a repository with the same url exists" {
        val type = RepositoryType.GIT

        val url1 = "https://example.com/repo1.git"
        val url2 = "https://example.com/repo2.git"

        repositoryRepository.create(type, url1, productId)
        val createdRepository2 = repositoryRepository.create(type, url2, productId)

        val updateType = OptionalValue.Absent
        val updateUrl = url1.asPresent()

        shouldThrow<UniqueConstraintException> {
            repositoryRepository.update(createdRepository2.id, updateType, updateUrl)
        }
    }

    "delete should delete the database entry" {
        val createdRepository =
            repositoryRepository.create(RepositoryType.GIT, "https://example.com/repo.git", productId)

        repositoryRepository.delete(createdRepository.id)

        repositoryRepository.listForProduct(productId).data shouldBe emptyList()
    }

    "get should return null" {
        repositoryRepository.get(1L).shouldBeNull()
    }

    "get should return the repository" {
        val repository = repositoryRepository.create(RepositoryType.GIT, "https://example.com/repo.git", productId)

        repositoryRepository.get(repository.id) shouldBe repository
    }

    "getHierarchy should return the structure of the repository" {
        val repository = repositoryRepository.create(RepositoryType.GIT, "https://example.com/repo.git", productId)

        val expectedHierarchy = Hierarchy(repository, fixtures.product, fixtures.organization)

        repositoryRepository.getHierarchy(repository.id) shouldBe expectedHierarchy
    }

    "getHierarchy should throw an exception for a non-existing repository" {
        shouldThrow<EntityNotFoundException> {
            repositoryRepository.getHierarchy(1L)
        }
    }
})
