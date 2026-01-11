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

package org.eclipse.apoapsis.ortserver.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class CompoundHierarchyIdTest : WordSpec({
    "forOrganization()" should {
        "create an instance with only an organizationId" {
            val orgId = OrganizationId(1)
            val compoundId = CompoundHierarchyId.forOrganization(orgId)

            compoundId.organizationId shouldBe orgId
            compoundId.productId shouldBe null
            compoundId.repositoryId shouldBe null
        }
    }

    "forProduct()" should {
        "create an instance with an organizationId and a productId" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val compoundId = CompoundHierarchyId.forProduct(orgId, prodId)

            compoundId.organizationId shouldBe orgId
            compoundId.productId shouldBe prodId
            compoundId.repositoryId shouldBe null
        }
    }

    "forRepository()" should {
        "create an instance with an organizationId, productId, and repositoryId" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val repoId = RepositoryId(3)
            val compoundId = CompoundHierarchyId.forRepository(orgId, prodId, repoId)

            compoundId.organizationId shouldBe orgId
            compoundId.productId shouldBe prodId
            compoundId.repositoryId shouldBe repoId
        }
    }

    "parent" should {
        "return null for an organization" {
            val compoundId = CompoundHierarchyId.forOrganization(OrganizationId(1))

            compoundId.parent shouldBe null
        }

        "return the organization ID for a product" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val compoundId = CompoundHierarchyId.forProduct(orgId, prodId)

            compoundId.parent shouldBe CompoundHierarchyId.forOrganization(orgId)
        }

        "return the product ID for a repository" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val repoId = RepositoryId(3)
            val compoundId = CompoundHierarchyId.forRepository(orgId, prodId, repoId)

            compoundId.parent shouldBe CompoundHierarchyId.forProduct(orgId, prodId)
        }

        "return null for the wildcard instance" {
            CompoundHierarchyId.WILDCARD.parent shouldBe null
        }
    }

    "parents" should {
        "return an empty list for an organization" {
            val compoundId = CompoundHierarchyId.forOrganization(OrganizationId(1))

            compoundId.parents shouldBe emptyList()
        }

        "return a list with the organization ID for a product" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val compoundId = CompoundHierarchyId.forProduct(orgId, prodId)

            compoundId.parents shouldBe listOf(CompoundHierarchyId.forOrganization(orgId))
        }

        "return a list with the product and organization IDs for a repository" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val repoId = RepositoryId(3)
            val compoundId = CompoundHierarchyId.forRepository(orgId, prodId, repoId)

            compoundId.parents shouldBe listOf(
                CompoundHierarchyId.forProduct(orgId, prodId),
                CompoundHierarchyId.forOrganization(orgId)
            )
        }

        "return an empty list for the wildcard instance" {
            CompoundHierarchyId.WILDCARD.parents shouldBe emptyList()
        }
    }

    "level" should {
        "return the correct level for an organization" {
            val compoundId = CompoundHierarchyId.forOrganization(OrganizationId(1))

            compoundId.level shouldBe HierarchyLevel.ORGANIZATION
        }

        "return the correct level for a product" {
            val compoundId = CompoundHierarchyId.forProduct(OrganizationId(1), ProductId(2))

            compoundId.level shouldBe HierarchyLevel.PRODUCT
        }

        "return the correct level for a repository" {
            val compoundId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))

            compoundId.level shouldBe HierarchyLevel.REPOSITORY
        }

        "return the wildcard level for the wildcard instance" {
            CompoundHierarchyId.WILDCARD.level shouldBe HierarchyLevel.WILDCARD
        }
    }

    "get()" should {
        "return the correct ID for an organization" {
            val orgId = OrganizationId(1)
            val compoundId = CompoundHierarchyId.forOrganization(orgId)

            compoundId[HierarchyLevel.ORGANIZATION] shouldBe orgId
            compoundId[HierarchyLevel.PRODUCT] shouldBe null
            compoundId[HierarchyLevel.REPOSITORY] shouldBe null
        }

        "return the correct ID for a product" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val compoundId = CompoundHierarchyId.forProduct(orgId, prodId)

            compoundId[HierarchyLevel.ORGANIZATION] shouldBe orgId
            compoundId[HierarchyLevel.PRODUCT] shouldBe prodId
            compoundId[HierarchyLevel.REPOSITORY] shouldBe null
        }

        "return the correct ID for a repository" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val repoId = RepositoryId(3)
            val compoundId = CompoundHierarchyId.forRepository(orgId, prodId, repoId)

            compoundId[HierarchyLevel.ORGANIZATION] shouldBe orgId
            compoundId[HierarchyLevel.PRODUCT] shouldBe prodId
            compoundId[HierarchyLevel.REPOSITORY] shouldBe repoId
        }

        "throw for the wildcard level" {
            val compoundId = CompoundHierarchyId.forProduct(OrganizationId(1), ProductId(2))

            shouldThrow<IllegalArgumentException> {
                compoundId[HierarchyLevel.WILDCARD]
            }
        }
    }

    "contains()" should {
        "return true for the same instance" {
            val compoundId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))

            (compoundId in compoundId) shouldBe true
        }

        "return true for a parent instance" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val repoId = RepositoryId(3)
            val compoundId = CompoundHierarchyId.forRepository(orgId, prodId, repoId)
            val parentId = CompoundHierarchyId.forProduct(orgId, prodId)

            (compoundId in parentId) shouldBe true
        }

        "return false for a child instance" {
            val orgId = OrganizationId(1)
            val prodId = ProductId(2)
            val repoId = RepositoryId(3)
            val compoundId = CompoundHierarchyId.forProduct(orgId, prodId)
            val childId = CompoundHierarchyId.forRepository(orgId, prodId, repoId)

            (compoundId in childId) shouldBe false
        }

        "return false for an unrelated instance" {
            val compoundId1 = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))
            val compoundId2 = CompoundHierarchyId.forRepository(OrganizationId(4), ProductId(5), RepositoryId(6))

            (compoundId2 in compoundId1) shouldBe false
        }

        "return true for the wildcard instance" {
            val compoundId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))

            (compoundId in CompoundHierarchyId.WILDCARD) shouldBe true
        }

        "return false for any instance in the wildcard instance" {
            val compoundId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))

            (CompoundHierarchyId.WILDCARD in compoundId) shouldBe false
        }
    }
})
