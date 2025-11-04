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

package ort.eclipse.apoapsis.ortserver.components.search.routes

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.model.runs.Identifier

import ort.eclipse.apoapsis.ortserver.components.search.SearchIntegrationTest
import ort.eclipse.apoapsis.ortserver.components.search.createRunWithPackage
import ort.eclipse.apoapsis.ortserver.components.search.toCoordinates

private const val SEARCH_ROUTE = "/search/package"

class GetRunsWithPackageIntegrationTest : SearchIntegrationTest({
    "GetRunsWithPackage" should {
        "return BadRequest if identifier is missing" {
            searchTestApplication { client ->
                val response = client.get(SEARCH_ROUTE)

                response shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "return BadRequest if productId without organizationId is provided" {
            val fixtures = dbExtension.fixtures
            val run = createRunWithPackage(fixtures = fixtures, repoId = fixtures.repository.id)

            searchTestApplication { client ->
                val response = client.get(SEARCH_ROUTE) {
                    parameter("identifier", run.packageId)
                    parameter("productId", run.productId.toString())
                }

                response shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "return BadRequest if repositoryId without productId and organizationId is provided" {
            val fixtures = dbExtension.fixtures
            val run = createRunWithPackage(fixtures = fixtures, repoId = fixtures.repository.id)

            searchTestApplication { client ->
                val withoutScopes = client.get(SEARCH_ROUTE) {
                    parameter("identifier", run.packageId)
                    parameter("repositoryId", run.repositoryId.toString())
                }

                withoutScopes shouldHaveStatus HttpStatusCode.BadRequest

                val missingProduct = client.get(SEARCH_ROUTE) {
                    parameter("identifier", run.packageId)
                    parameter("organizationId", run.organizationId.toString())
                    parameter("repositoryId", run.repositoryId.toString())
                }

                missingProduct shouldHaveStatus HttpStatusCode.BadRequest
            }
        }

        "return runs globally when package exists" {
            val fixtures = dbExtension.fixtures
            val packageIdentifier = identifierFor("global")
            val run = createRunWithPackage(
                fixtures = fixtures,
                repoId = fixtures.repository.id,
                pkgId = packageIdentifier
            )

            searchTestApplication { client ->
                val response = client.get(SEARCH_ROUTE) {
                    parameter("identifier", run.packageId)
                }

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<List<RunWithPackage>>()
                body shouldContainExactly listOf(run)
            }
        }

        "return runs scoped to organization" {
            val fixtures = dbExtension.fixtures
            val packageIdentifier = identifierFor("organization")
            val run = createRunWithPackage(
                fixtures = fixtures,
                repoId = fixtures.repository.id,
                pkgId = packageIdentifier
            )
            val otherOrg = fixtures.createOrganization(name = "other-org")
            val otherProd = fixtures.createProduct(name = "other-prod", organizationId = otherOrg.id)
            val otherRepo = fixtures.createRepository(
                productId = otherProd.id,
                url = "https://example.com/org-scope.git"
            )
            createRunWithPackage(
                fixtures = fixtures,
                repoId = otherRepo.id,
                pkgId = packageIdentifier
            )

            searchTestApplication { client ->
                val response = client.get(SEARCH_ROUTE) {
                    parameter("identifier", run.packageId)
                    parameter("organizationId", run.organizationId.toString())
                }

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<List<RunWithPackage>>()
                body shouldContainExactly listOf(run)
            }
        }

        "return runs scoped to product" {
            val fixtures = dbExtension.fixtures
            val packageIdentifier = identifierFor("product")
            val run = createRunWithPackage(
                fixtures = fixtures,
                repoId = fixtures.repository.id,
                pkgId = packageIdentifier
            )
            val otherProd = fixtures.createProduct(name = "other-prod", organizationId = run.organizationId)
            val otherRepo = fixtures.createRepository(
                productId = otherProd.id,
                url = "https://example.com/product-scope.git"
            )
            createRunWithPackage(
                fixtures = fixtures,
                repoId = otherRepo.id,
                pkgId = packageIdentifier
            )

            searchTestApplication { client ->
                val response = client.get(SEARCH_ROUTE) {
                    parameter("identifier", run.packageId)
                    parameter("organizationId", run.organizationId.toString())
                    parameter("productId", run.productId.toString())
                }

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<List<RunWithPackage>>()
                body shouldContainExactly listOf(run)
            }
        }

        "return runs scoped to repository" {
            val fixtures = dbExtension.fixtures
            val packageIdentifier = identifierFor("repository")
            val run = createRunWithPackage(
                fixtures = fixtures,
                repoId = fixtures.repository.id,
                pkgId = packageIdentifier
            )
            val otherRepo = fixtures.createRepository(
                productId = run.productId,
                url = "https://example.com/repository-scope.git"
            )
            createRunWithPackage(
                fixtures = fixtures,
                repoId = otherRepo.id,
                pkgId = packageIdentifier
            )

            searchTestApplication { client ->
                val response = client.get(SEARCH_ROUTE) {
                    parameter("identifier", run.packageId)
                    parameter("organizationId", run.organizationId.toString())
                    parameter("productId", run.productId.toString())
                    parameter("repositoryId", run.repositoryId.toString())
                }

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<List<RunWithPackage>>()
                body shouldContainExactly listOf(run)
            }
        }

        "return all runs for a package across scopes" {
            val fixtures = dbExtension.fixtures
            val packageIdentifier = identifierFor("aggregate")
            val baseRepoId = fixtures.repository.id
            val run1 = createRunWithPackage(
                fixtures = fixtures,
                repoId = baseRepoId,
                pkgId = packageIdentifier
            )
            val run2 = createRunWithPackage(
                fixtures = fixtures,
                repoId = baseRepoId,
                pkgId = packageIdentifier
            )

            val otherOrg = fixtures.createOrganization(name = "agg-org")
            val otherProd = fixtures.createProduct(name = "agg-prod", organizationId = otherOrg.id)
            val otherRepo = fixtures.createRepository(
                productId = otherProd.id,
                url = "https://example.com/aggregate-scope.git"
            )
            val run3 = createRunWithPackage(
                fixtures = fixtures,
                repoId = otherRepo.id,
                pkgId = packageIdentifier
            )

            searchTestApplication { client ->
                val response = client.get(SEARCH_ROUTE) {
                    parameter("identifier", run1.packageId)
                }

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<List<RunWithPackage>>()
                body shouldContainExactlyInAnyOrder(listOf(run1, run2, run3))
            }
        }

        "return empty list when package not present in scope" {
            val fixtures = dbExtension.fixtures
            val packageIdentifier = identifierFor("empty-run")
            val run = createRunWithPackage(
                fixtures = fixtures,
                repoId = fixtures.repository.id,
                pkgId = packageIdentifier
            )
            val missingIdentifier = identifierFor("missing").toCoordinates()

            searchTestApplication { client ->
                val response = client.get(SEARCH_ROUTE) {
                    parameter("identifier", missingIdentifier)
                    parameter("organizationId", run.organizationId.toString())
                    parameter("productId", run.productId.toString())
                    parameter("repositoryId", run.repositoryId.toString())
                }

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<List<RunWithPackage>>()
                body.shouldBeEmpty()
            }
        }
    }
})

private fun identifierFor(suffix: String) = Identifier(
    type = "maven",
    namespace = "ns-$suffix",
    name = "name-$suffix",
    version = "1.0.0"
)
