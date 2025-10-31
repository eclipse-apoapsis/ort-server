/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package ort.eclipse.apoapsis.ortserver.components.search

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should

import org.eclipse.apoapsis.ortserver.components.search.backend.SearchService
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.runs.Identifier

import org.jetbrains.exposed.sql.Database

class SearchServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures
    lateinit var searchService: SearchService
    var repositoryId = -1L

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures
        searchService = SearchService(db)
        repositoryId = fixtures.repository.id
    }

    "findOrtRunsByPackage" should {
        "throw if productId without organizationId" {
            shouldThrow<IllegalArgumentException> {
                searchService.findOrtRunsByPackage(
                    Identifier(
                        type = "maven",
                        namespace = "foo",
                        name = "bar",
                        version = "1.0.0"
                    ).toCoordinates(),
                    productId = 2L
                )
            }
        }

        "throw if repositoryId without productId and organizationId" {
            shouldThrow<IllegalArgumentException> {
                searchService.findOrtRunsByPackage(
                    Identifier(
                        type = "maven",
                        namespace = "foo",
                        name = "bar",
                        version = "1.0.0"
                    ).toCoordinates(),
                    repositoryId = 3L
                )
            }
            shouldThrow<IllegalArgumentException> {
                searchService.findOrtRunsByPackage(
                    Identifier(
                        type = "maven",
                        namespace = "foo",
                        name = "bar",
                        version = "1.0.0"
                    ).toCoordinates(),
                    organizationId = 1L,
                    repositoryId = 3L
                )
            }
        }

        "support global search" {
            val run = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val expectedId = run.packageId

            val result = searchService.findOrtRunsByPackage(expectedId)

            result shouldContainExactly listOf(run)
        }

        "support search inside an organization" {
            val run = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val expectedId = run.packageId
            val otherOrg = dbExtension.fixtures.createOrganization(name = "other-org")
            val otherProd = dbExtension.fixtures.createProduct(name = "other-prod", organizationId = otherOrg.id)
            val otherRepo = dbExtension.fixtures.createRepository(
                productId = otherProd.id,
                url = "https://example.com/other-repo.git"
            )
            createRunWithPackage(fixtures = fixtures, repoId = otherRepo.id)

            val result = searchService.findOrtRunsByPackage(
                identifier = expectedId,
                organizationId = run.organizationId
            )

            result shouldContainExactly listOf(run)
        }

        "support search inside a product" {
            val run = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val expectedId = run.packageId
            val otherProd = dbExtension.fixtures.createProduct(name = "other-prod", organizationId = run.organizationId)
            val otherRepo = dbExtension.fixtures.createRepository(
                productId = otherProd.id,
                url = "https://example.com/other-repo.git"
            )
            createRunWithPackage(fixtures = fixtures, repoId = otherRepo.id)

            val result = searchService.findOrtRunsByPackage(
                expectedId,
                organizationId = run.organizationId,
                productId = run.productId
            )
            result shouldContainExactly listOf(run)
        }

        "support search inside a repository" {
            val run = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val expectedId = run.packageId
            val otherRepo = dbExtension.fixtures.createRepository(
                productId = run.productId,
                url = "https://example.com/other-repo.git"
            )
            createRunWithPackage(fixtures = fixtures, repoId = otherRepo.id)

            val result = searchService.findOrtRunsByPackage(
                expectedId,
                organizationId = run.organizationId,
                productId = run.productId,
                repositoryId = run.repositoryId
            )
            result shouldContainExactly listOf(run)
        }

        "find all runs for a package from multiple repositories/products/organizations" {
            val run1 = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val run2 = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val expectedId = run1.packageId

            val otherOrg = dbExtension.fixtures.createOrganization(name = "other-org")
            val otherProd = dbExtension.fixtures.createProduct(name = "other-prod", organizationId = otherOrg.id)
            val otherRepo = dbExtension.fixtures.createRepository(
                productId = otherProd.id,
                url = "https://example.com/other-repo.git"
            )
            val run3 = createRunWithPackage(
                fixtures = fixtures, repoId = otherRepo.id,
                pkgId = Identifier(
                    type = "test",
                    namespace = "ns",
                    name = "name",
                    version = "ver"
                )
            )

            val result = searchService.findOrtRunsByPackage(expectedId)

            result shouldContainExactlyInAnyOrder(listOf(run1, run2, run3))
        }

        "return empty when package is not present in the given scope" {
            val run = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val expectedId = "maven:nonexistent:package:1.0.0"

            val result = searchService.findOrtRunsByPackage(
                expectedId,
                organizationId = run.organizationId,
                productId = run.productId,
                repositoryId = run.repositoryId
            )

            result should beEmpty()
        }
    }
})
