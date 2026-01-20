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

package org.eclipse.apoapsis.ortserver.dao.repositories.organization

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoOrganizationRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var organizationRepository: DaoOrganizationRepository

    beforeEach {
        organizationRepository = dbExtension.fixtures.organizationRepository
    }

    "create should create an entry in the database" {
        val name = "name"
        val description = "description"

        val createdOrg = organizationRepository.create(name, description)

        val dbEntry = organizationRepository.get(createdOrg.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe Organization(dbEntry.id, name, description)
    }

    "list should retrieve all entities from the database" {
        val name1 = "name1"
        val description1 = "description1"

        val name2 = "name2"
        val description2 = "description2"

        val createdOrg1 = organizationRepository.create(name1, description1)
        val createdOrg2 = organizationRepository.create(name2, description2)

        organizationRepository.list() shouldBe ListQueryResult(
            data = listOf(
                Organization(createdOrg1.id, name1, description1),
                Organization(createdOrg2.id, name2, description2)

            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply parameters" {
        val name1 = "name1"
        val description1 = "description1"

        val name2 = "name2"
        val description2 = "description2"

        organizationRepository.create(name1, description1)
        val createdOrg2 = organizationRepository.create(name2, description2)

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("name", OrderDirection.DESCENDING)),
            limit = 1
        )

        organizationRepository.list(parameters) shouldBe ListQueryResult(
            data = listOf(Organization(createdOrg2.id, name2, description2)),
            params = parameters,
            totalCount = 2
        )
    }

    "list should filter organizations using basic regex pattern" {
        val createdOrg1 = organizationRepository.create("apple-org", "description1")
        val createdOrg2 = organizationRepository.create("banana-org", "description2")
        organizationRepository.create("orange-company", "description3")
        val createdOrg3 = organizationRepository.create("org-pear", "description4")
        val createdOrg4 = organizationRepository.create("test-organization", "description5")

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("name", OrderDirection.ASCENDING)),
            limit = 10
        )

        organizationRepository.list(parameters, FilterParameter("org")) shouldBe ListQueryResult(
            data = listOf(createdOrg1, createdOrg2, createdOrg3, createdOrg4),
            params = parameters,
            totalCount = 4
        )
    }

    "list should filter with regex ending pattern" {
        val createdOrg1 = organizationRepository.create("user-api", "API service")
        organizationRepository.create("api-gateway", "Gateway service")
        val createdOrg2 = organizationRepository.create("auth-api", "Authentication API")
        organizationRepository.create("core-service", "Core service")

        val parameters = ListQueryParameters(limit = 10)

        organizationRepository.list(parameters, FilterParameter("api$")) shouldBe ListQueryResult(
            data = listOf(createdOrg1, createdOrg2),
            params = parameters,
            totalCount = 2
        )
    }

    "list should filter with regex starting pattern" {
        val createdOrg1 = organizationRepository.create("core-service", "Core service")
        val createdOrg2 = organizationRepository.create("core-auth", "Core authentication")
        organizationRepository.create("user-core", "User core")
        organizationRepository.create("api-service", "API service")

        val parameters = ListQueryParameters(limit = 10)

        organizationRepository.list(parameters, FilterParameter("^core")) shouldBe ListQueryResult(
            data = listOf(createdOrg1, createdOrg2),
            params = parameters,
            totalCount = 2
        )
    }

    "list should apply a hierarchy filter" {
        val createdOrg1 = organizationRepository.create("org1", "description1")
        val org1Id = CompoundHierarchyId.forOrganization(OrganizationId(createdOrg1.id))
        val createdOrg2 = organizationRepository.create("org2", "description2")
        val org2Id = CompoundHierarchyId.forOrganization(OrganizationId(createdOrg2.id))
        organizationRepository.create("org3", "description3")

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.ORGANIZATION to listOf(org1Id, org2Id)),
            nonTransitiveIncludes = emptyMap()
        )
        val result = organizationRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(createdOrg1, createdOrg2),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply a hierarchy filter with non-transitive includes" {
        val createdOrg1 = organizationRepository.create("org1", "description1")
        val org1Id = CompoundHierarchyId.forOrganization(OrganizationId(createdOrg1.id))
        val createdOrg2 = organizationRepository.create("org2", "description2")
        val org2Id = CompoundHierarchyId.forOrganization(OrganizationId(createdOrg2.id))
        organizationRepository.create("org3", "description3")

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.ORGANIZATION to listOf(org1Id)),
            nonTransitiveIncludes = mapOf(HierarchyLevel.ORGANIZATION to listOf(org2Id))
        )
        val result = organizationRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(createdOrg1, createdOrg2),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply a hierarchy filter with non-transitive includes caused by elements on lower levels" {
        val createdOrg = organizationRepository.create("org", "description1")
        val orgId = CompoundHierarchyId.forOrganization(OrganizationId(createdOrg.id))
        val productId = CompoundHierarchyId.forProduct(
            OrganizationId(createdOrg.id),
            ProductId(1)
        )

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.PRODUCT to listOf(productId)),
            nonTransitiveIncludes = mapOf(HierarchyLevel.ORGANIZATION to listOf(orgId))
        )
        val result = organizationRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(createdOrg),
            params = ListQueryParameters.DEFAULT,
            totalCount = 1
        )
    }

    "update should update an entity in the database" {
        val createdOrg = organizationRepository.create("name", "description")

        val updateName = "updatedName".asPresent()
        val updateDescription = "updatedDescription".asPresent()

        organizationRepository.update(createdOrg.id, updateName, updateDescription)

        organizationRepository.get(createdOrg.id) shouldBe Organization(
            id = createdOrg.id,
            name = updateName.value,
            description = updateDescription.value
        )
    }

    "update should update null values and ignore absent values" {
        val name = "name"

        val createdOrg = organizationRepository.create("name", "description")

        organizationRepository.update(
            id = createdOrg.id,
            name = OptionalValue.Absent,
            description = null.asPresent()
        )

        organizationRepository.get(createdOrg.id) shouldBe Organization(
            id = createdOrg.id,
            name = name,
            description = null
        )
    }

    "delete should delete an entity in the database" {
        val createdOrg = organizationRepository.create("name", "description")

        organizationRepository.delete(createdOrg.id)

        organizationRepository.list().data shouldBe emptyList()
    }

    "get should return null" {
        organizationRepository.get(1L).shouldBeNull()
    }

    "get should return the organization" {
        val organization = organizationRepository.create("name", "description")

        organizationRepository.get(organization.id) shouldBe organization
    }
})
