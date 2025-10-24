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

package org.eclipse.apoapsis.ortserver.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter

class HierarchyFilterTest : WordSpec({
    "create()" should {
        "create a new instance" {
            val orgId = CompoundHierarchyId.forOrganization(OrganizationId(1))
            val productId = CompoundHierarchyId.forProduct(OrganizationId(1), ProductId(2))
            val repositoryId1 = CompoundHierarchyId.forRepository(
                OrganizationId(1),
                ProductId(2),
                RepositoryId(3)
            )
            val repositoryId2 = CompoundHierarchyId.forRepository(
                OrganizationId(11),
                ProductId(12),
                RepositoryId(13)
            )

            val filter = HierarchyFilter.create(
                listOf(orgId, productId, repositoryId1, repositoryId2)
            )

            filter.elementsByLevel shouldHaveSize 3
            filter.elementsByLevel[CompoundHierarchyId.ORGANIZATION_LEVEL] shouldContainExactly listOf(orgId)
            filter.elementsByLevel[CompoundHierarchyId.PRODUCT_LEVEL] shouldContainExactly listOf(productId)
            filter.elementsByLevel[CompoundHierarchyId.REPOSITORY_LEVEL] shouldContainExactlyInAnyOrder listOf(
                repositoryId1, repositoryId2
            )
        }

        "support restricting the level" {
            val orgId = CompoundHierarchyId.forOrganization(OrganizationId(1))
            val productId = CompoundHierarchyId.forProduct(OrganizationId(1), ProductId(2))
            val repositoryId = CompoundHierarchyId.forRepository(
                OrganizationId(1),
                ProductId(2),
                RepositoryId(3)
            )

            val filter = HierarchyFilter.create(
                listOf(orgId, productId, repositoryId),
                maxLevel = CompoundHierarchyId.PRODUCT_LEVEL
            )

            filter.elementsByLevel shouldHaveSize 2
            filter.elementsByLevel[CompoundHierarchyId.ORGANIZATION_LEVEL] shouldContainExactly listOf(orgId)
            filter.elementsByLevel[CompoundHierarchyId.PRODUCT_LEVEL] shouldContainExactly listOf(productId)
        }
    }

    "isWildcard" should {
        "return false if the wildcard ID is not contained" {
            val orgId = CompoundHierarchyId.forOrganization(OrganizationId(1))
            val productId = CompoundHierarchyId.forProduct(OrganizationId(1), ProductId(2))

            val filter = HierarchyFilter.create(listOf(orgId, productId))

            filter.isWildcard shouldBe false
        }

        "return false for an empty filter" {
            val filter = HierarchyFilter.create(emptyList())

            filter.isWildcard shouldBe false
        }

        "return true if the wildcard ID is contained" {
            val repoId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))

            val filter = HierarchyFilter.create(listOf(repoId, CompoundHierarchyId.WILDCARD))

            filter.isWildcard shouldBe true
        }

        "return true for the WILDCARD filter instance" {
            HierarchyFilter.WILDCARD.isWildcard shouldBe true
        }
    }
})
