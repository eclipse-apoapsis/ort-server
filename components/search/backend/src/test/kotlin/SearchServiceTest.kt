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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.search.backend.SearchService
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter

import org.jetbrains.exposed.sql.Database

class SearchServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures
    lateinit var authorizationService: AuthorizationService
    lateinit var searchService: SearchService
    var repositoryId = -1L

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures
        authorizationService = mockk {
            coEvery {
                filterHierarchyIds(any(), any(), any(), any(), any())
            } returns HierarchyFilter.WILDCARD
        }
        searchService = SearchService(db, authorizationService)
        repositoryId = fixtures.repository.id
    }
    val userId = "user-id"

    "findOrtRunsByPackage" should {
        "support global search" {
            val run = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val expectedId = run.packageId

            val result = searchService.findOrtRunsByPackage(identifier = expectedId, purl = null, userId = userId)

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

            val filter = HierarchyFilter(
                transitiveIncludes = mapOf(
                    CompoundHierarchyId.ORGANIZATION_LEVEL to listOf(
                        CompoundHierarchyId.forOrganization(OrganizationId(run.organizationId))
                    )
                ),
                nonTransitiveIncludes = emptyMap()
            )
            coEvery {
                authorizationService.filterHierarchyIds(any(), any(), any(), any(), OrganizationId(run.organizationId))
            } returns filter

            val result = searchService.findOrtRunsByPackage(
                identifier = expectedId,
                purl = null,
                userId = userId,
                scope = OrganizationId(run.organizationId)
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

            val filter = HierarchyFilter(
                transitiveIncludes = mapOf(
                    CompoundHierarchyId.PRODUCT_LEVEL to listOf(
                        CompoundHierarchyId.forProduct(
                            OrganizationId(run.organizationId),
                            ProductId(run.productId)
                        )
                    )
                ),
                nonTransitiveIncludes = emptyMap()
            )
            coEvery {
                authorizationService.filterHierarchyIds(any(), any(), any(), any(), ProductId(run.productId))
            } returns filter

            val result = searchService.findOrtRunsByPackage(
                identifier = expectedId,
                purl = null,
                userId = userId,
                scope = ProductId(run.productId)
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

            val filter = HierarchyFilter(
                transitiveIncludes = mapOf(
                    CompoundHierarchyId.REPOSITORY_LEVEL to listOf(
                        CompoundHierarchyId.forRepository(
                            OrganizationId(run.organizationId),
                            ProductId(run.productId),
                            RepositoryId(run.repositoryId)
                        )
                    )
                ),
                nonTransitiveIncludes = emptyMap()
            )
            coEvery {
                authorizationService.filterHierarchyIds(any(), any(), any(), any(), RepositoryId(run.repositoryId))
            } returns filter

            val result = searchService.findOrtRunsByPackage(
                identifier = expectedId,
                purl = null,
                userId = userId,
                scope = RepositoryId(run.repositoryId)
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

            val result = searchService.findOrtRunsByPackage(identifier = expectedId, purl = null, userId = userId)

            result shouldContainExactlyInAnyOrder(listOf(run1, run2, run3))
        }

        "return empty when package is not present in the given scope" {
            val run = createRunWithPackage(fixtures = fixtures, repoId = repositoryId)
            val expectedId = "maven:nonexistent:package:1.0.0"

            val result = searchService.findOrtRunsByPackage(
                identifier = expectedId,
                purl = null,
                userId = userId,
                scope = RepositoryId(run.repositoryId)
            )

            result should beEmpty()
        }

        "return only runs from repositories included in the hierarchy filter" {
            val packageIdentifier = Identifier(
                type = "maven",
                namespace = "filtered",
                name = "package",
                version = "1.0.0"
            )
            val run1 = createRunWithPackage(fixtures = fixtures, repoId = repositoryId, pkgId = packageIdentifier)
            val otherRepo = dbExtension.fixtures.createRepository(
                productId = run1.productId,
                url = "https://example.com/filtered.git"
            )
            createRunWithPackage(fixtures = fixtures, repoId = otherRepo.id, pkgId = packageIdentifier)

            val filter = HierarchyFilter(
                transitiveIncludes = mapOf(
                    CompoundHierarchyId.REPOSITORY_LEVEL to listOf(
                        CompoundHierarchyId.forRepository(
                            OrganizationId(run1.organizationId),
                            ProductId(run1.productId),
                            RepositoryId(run1.repositoryId)
                        )
                    )
                ),
                nonTransitiveIncludes = emptyMap()
            )
            coEvery {
                authorizationService.filterHierarchyIds(any(), any(), any(), any(), any())
            } returns filter

            val result = searchService.findOrtRunsByPackage(identifier = run1.packageId, purl = null, userId = userId)

            result shouldContainExactly listOf(run1)
        }

        "support PURL-based search without curation" {
            val pkgId = Identifier("maven", "org.example", "library", "1.0.0")
            val run = createRunWithPackageForPurlSearch(fixtures = fixtures, repoId = repositoryId, pkgId = pkgId)
            val expectedPurl = pkgId.toPurl()

            val result = searchService.findOrtRunsByPackage(identifier = null, purl = expectedPurl, userId = userId)

            result shouldContainExactly listOf(run)
        }

        "support PURL-based search with curation" {
            val pkgId = Identifier("maven", "org.example", "library", "1.0.0")
            val curatedPurl = "pkg:maven/org.example/library-corrected@1.0.0"
            val run = createRunWithCuratedPurl(
                db = db,
                fixtures = fixtures,
                repoId = repositoryId,
                pkgId = pkgId,
                curatedPurl = curatedPurl
            )

            val result = searchService.findOrtRunsByPackage(identifier = null, purl = curatedPurl, userId = userId)

            result shouldContainExactly listOf(run)
        }

        "not find original PURL when curated PURL exists" {
            val pkgId = Identifier("maven", "org.example", "library", "1.0.0")
            val originalPurl = pkgId.toPurl()
            val curatedPurl = "pkg:maven/org.example/library-corrected@1.0.0"
            createRunWithCuratedPurl(
                db = db,
                fixtures = fixtures,
                repoId = repositoryId,
                pkgId = pkgId,
                curatedPurl = curatedPurl
            )

            val result = searchService.findOrtRunsByPackage(identifier = null, purl = originalPurl, userId = userId)

            result should beEmpty()
        }

        "return purl field and null packageId for PURL search" {
            val pkgId = Identifier("maven", "org.example", "library", "1.0.0")
            createRunWithPackageForPurlSearch(fixtures = fixtures, repoId = repositoryId, pkgId = pkgId)
            val expectedPurl = pkgId.toPurl()

            val result = searchService.findOrtRunsByPackage(identifier = null, purl = expectedPurl, userId = userId)

            result.shouldBeSingleton {
                it.purl shouldBe expectedPurl
                it.packageId shouldBe null
            }
        }

        "return packageId field and null purl for identifier search" {
            val pkgId = Identifier("maven", "org.example", "library", "1.0.0")
            createRunWithPackage(fixtures = fixtures, repoId = repositoryId, pkgId = pkgId)
            val expectedId = pkgId.toCoordinates()

            val result = searchService.findOrtRunsByPackage(identifier = expectedId, purl = null, userId = userId)

            result.shouldBeSingleton {
                it.packageId shouldBe expectedId
                it.purl shouldBe null
            }
        }
    }
})
