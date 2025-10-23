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

package org.eclipse.apoapsis.ortserver.components.packagesearch.backend

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.test.toCoordinates
import org.eclipse.apoapsis.ortserver.model.runs.Identifier

class PackageSearchServiceTest : WordSpec({
    val expectedIdentifier = Identifier(
        type = "maven",
        namespace = "foo",
        name = "bar",
        version = "1.0.0"
    ).toCoordinates()

    val expectedOrganizationId = 10L
    val expectedProductId = 20L
    val expectedRepositoryId = 30L

    val mockDataAccess = object : PackageSearchDataAccess {
        override fun findOrtRunsByPackage(
            identifier: String,
            organizationId: Long?,
            productId: Long?,
            repositoryId: Long?
        ): List<OrtRunResult> {
            val matchesExpected = identifier == expectedIdentifier &&
                organizationId == expectedOrganizationId &&
                productId == expectedProductId &&
                repositoryId == expectedRepositoryId
            return if (matchesExpected) {
                listOf(
                    OrtRunResult(
                        organizationId = organizationId,
                        productId = productId,
                        repositoryId = repositoryId,
                        id = 42L,
                        revision = "abc123",
                        createdAt = Instant.parse("2025-10-17T12:00:00Z"),
                        packageId = expectedIdentifier
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    val service = PackageSearchService(mockDataAccess)

    "search" should {
        "throw if identifier is null" {
            shouldThrow<IllegalArgumentException> {
                service.search(null)
            }
        }

        "throw if productId without organizationId" {
            shouldThrow<IllegalArgumentException> {
                service.search(
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
                service.search(
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
                service.search(
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

        "return mapped DTOs for valid input" {
            val result = service.search(
                expectedIdentifier,
                organizationId = expectedOrganizationId,
                productId = expectedProductId,
                repositoryId = expectedRepositoryId
            )
            result.size shouldBe 1
            val dto = result.first()
            dto.organizationId shouldBe expectedOrganizationId
            dto.productId shouldBe expectedProductId
            dto.repositoryId shouldBe expectedRepositoryId
            dto.ortRunId shouldBe 42L
            dto.revision shouldBe "abc123"
            dto.createdAt shouldBe Instant.parse("2025-10-17T12:00:00Z")
        }

        "return empty list for non-matching input" {
            val result = service.search("xyz", organizationId = 999L, productId = 888L, repositoryId = 777L)
            result.size shouldBe 0
        }

        "return all runs for the same package in multiple repositories/products/orgs" {
            val multiMock = object : PackageSearchDataAccess {
                override fun findOrtRunsByPackage(
                    identifier: String,
                    organizationId: Long?,
                    productId: Long?,
                    repositoryId: Long?
                ): List<OrtRunResult> {
                    return if (identifier == expectedIdentifier) {
                        listOf(
                            OrtRunResult(
                                organizationId ?: 0L,
                                productId ?: 0L,
                                repositoryId ?: 0L,
                                1L,
                                "rev1",
                                Instant.parse("2025-10-17T12:00:00Z"),
                                expectedIdentifier
                            ),
                            OrtRunResult(
                                organizationId ?: 0L,
                                productId ?: 0L,
                                repositoryId ?: 0L,
                                2L,
                                "rev2",
                                Instant.parse("2025-10-18T12:00:00Z"),
                                expectedIdentifier
                            )
                        )
                    } else {
                        emptyList()
                    }
                }
            }
            val multiService = PackageSearchService(multiMock)
            val result = multiService.search(
                expectedIdentifier,
                organizationId = expectedOrganizationId,
                productId = expectedProductId,
                repositoryId = expectedRepositoryId
            )
            result.size shouldBe 2
            result.map { it.ortRunId } shouldContainExactlyInAnyOrder listOf(1L, 2L)
        }

        "return empty when package is not present in the given scope" {
            val emptyMock = object : PackageSearchDataAccess {
                override fun findOrtRunsByPackage(
                    identifier: String,
                    organizationId: Long?,
                    productId: Long?,
                    repositoryId: Long?
                ): List<OrtRunResult> = emptyList()
            }
            val emptyService = PackageSearchService(emptyMock)
            val result = emptyService.search(expectedIdentifier, organizationId = 999L)
            result shouldBe emptyList()
        }

        "return multiple runs for the same package in one repository" {
            val repoMock = object : PackageSearchDataAccess {
                override fun findOrtRunsByPackage(
                    identifier: String,
                    organizationId: Long?,
                    productId: Long?,
                    repositoryId: Long?
                ): List<OrtRunResult> {
                    return if (identifier == expectedIdentifier && repositoryId == expectedRepositoryId) {
                        listOf(
                            OrtRunResult(
                                organizationId ?: 0L,
                                productId ?: 0L,
                                repositoryId,
                                10L,
                                "revA",
                                Instant.parse("2025-10-19T12:00:00Z"),
                                expectedIdentifier
                            ),
                            OrtRunResult(
                                organizationId ?: 0L,
                                productId ?: 0L,
                                repositoryId,
                                11L,
                                "revB",
                                Instant.parse("2025-10-20T12:00:00Z"),
                                expectedIdentifier
                            )
                        )
                    } else {
                        emptyList()
                    }
                }
            }
            val repoService = PackageSearchService(repoMock)
            val result = repoService.search(
                expectedIdentifier,
                organizationId = expectedOrganizationId,
                productId = expectedProductId,
                repositoryId = expectedRepositoryId
            )
            result.size shouldBe 2
            result.map { it.ortRunId } shouldContainExactlyInAnyOrder listOf(10L, 11L)
        }

        "support substring search for package coordinates" {
            val substrings = listOf("foo", "bar", "1.0.0", "maven:foo:bar", "bar:1.0.0", "maven:foo.bar")
            val substringMock = object : PackageSearchDataAccess {
                override fun findOrtRunsByPackage(
                    identifier: String,
                    organizationId: Long?,
                    productId: Long?,
                    repositoryId: Long?
                ): List<OrtRunResult> {
                    return if (substrings.contains(identifier)) {
                        listOf(
                            OrtRunResult(
                                organizationId ?: 0L,
                                productId ?: 0L,
                                repositoryId ?: 0L,
                                99L,
                                "revSub",
                                Instant.parse("2025-10-21T12:00:00Z"),
                                expectedIdentifier,
                            )
                        )
                    } else {
                        emptyList()
                    }
                }
            }
            val substringService = PackageSearchService(substringMock)
            substrings.forEach { substring ->
                val result = substringService.search(substring)
                result.size shouldBe 1
                result.first().ortRunId shouldBe 99L
            }
        }
    }
})
