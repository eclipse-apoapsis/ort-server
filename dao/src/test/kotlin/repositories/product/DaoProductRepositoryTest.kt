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

package org.eclipse.apoapsis.ortserver.dao.repositories.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoProductRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var productRepository: DaoProductRepository
    lateinit var fixtures: Fixtures

    var orgId = -1L

    beforeEach {
        productRepository = dbExtension.fixtures.productRepository
        fixtures = dbExtension.fixtures

        orgId = fixtures.organization.id
    }

    "create should create an entry in the database" {
        val name = "name"
        val description = "description"

        val createdProduct = productRepository.create(name, description, orgId)

        val dbEntry = productRepository.get(createdProduct.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe Product(createdProduct.id, orgId, name, description)
    }

    "create with the same product name and organization should throw" {
        val name = "name"
        val description = "description"

        productRepository.create(name, description, orgId)

        shouldThrow<UniqueConstraintException> {
            productRepository.create(name, description, orgId)
        }
    }

    "list should retrieve all entities from the database" {
        val org2 = fixtures.createOrganization(name = "org2")

        val prod1 = fixtures.createProduct("prod1")
        val prod2 = fixtures.createProduct("prod2", organizationId = org2.id)

        productRepository.list() shouldBe ListQueryResult(
            data = listOf(prod1, prod2),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply parameters" {
        val org2 = fixtures.createOrganization(name = "org2")

        fixtures.createProduct("prod1")
        val prod2 = fixtures.createProduct("prod2", organizationId = org2.id)

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("name", OrderDirection.DESCENDING)),
            limit = 1
        )

        productRepository.list(parameters) shouldBe
            ListQueryResult(
                data = listOf(prod2),
                params = parameters,
                totalCount = 2
            )
    }

    "list should filter with regex ending pattern" {
        val prod1 = fixtures.createProduct("auth-product")
        val prod2 = fixtures.createProduct("user-product")
        fixtures.createProduct("product-gateway")
        fixtures.createProduct("core-service")

        productRepository.list(nameFilter = FilterParameter("product$")) shouldBe ListQueryResult(
            data = listOf(
                Product(prod1.id, orgId, prod1.name, prod1.description),
                Product(prod2.id, orgId, prod2.name, prod2.description)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should filter with regex starting pattern" {
        val prod1 = fixtures.createProduct("product-auth")
        val prod2 = fixtures.createProduct("product-service")
        fixtures.createProduct("user-product")
        fixtures.createProduct("name")

        productRepository.list(nameFilter = FilterParameter("^product")) shouldBe ListQueryResult(
            data = listOf(
                Product(prod1.id, orgId, prod1.name, prod1.description),
                Product(prod2.id, orgId, prod2.name, prod2.description)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply a hierarchy filter on product level" {
        val prod1 = fixtures.createProduct("prod1")
        val prod1Id = CompoundHierarchyId.forProduct(
            OrganizationId(fixtures.organization.id),
            ProductId(prod1.id)
        )
        val prod2 = fixtures.createProduct("prod2")
        val prod2Id = CompoundHierarchyId.forProduct(
            OrganizationId(fixtures.organization.id),
            ProductId(prod2.id)
        )
        fixtures.createProduct("prod3")

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.PRODUCT to listOf(prod1Id, prod2Id)),
            nonTransitiveIncludes = emptyMap(),
        )
        val result = productRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(
                Product(prod1.id, orgId, prod1.name, prod1.description),
                Product(prod2.id, orgId, prod2.name, prod2.description)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply a hierarchy filter on organization level" {
        val org2 = fixtures.createOrganization(name = "org2")
        val org1Id = CompoundHierarchyId.forOrganization(OrganizationId(fixtures.organization.id))
        val org2Id = CompoundHierarchyId.forOrganization(OrganizationId(org2.id))

        val prod1 = fixtures.createProduct("prod1")
        val prod2 = fixtures.createProduct("prod2", organizationId = org2.id)

        val otherOrg = fixtures.createOrganization(name = "otherOrg")
        fixtures.createProduct("prod3", organizationId = otherOrg.id)

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.ORGANIZATION to listOf(org1Id, org2Id)),
            nonTransitiveIncludes = emptyMap(),
        )
        val result = productRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(
                Product(prod1.id, orgId, prod1.name, prod1.description),
                Product(prod2.id, org2.id, prod2.name, prod2.description)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply a filter with non-transitive includes" {
        val prod1 = fixtures.createProduct("prod1")
        val prod1Id = CompoundHierarchyId.forProduct(
            OrganizationId(fixtures.organization.id),
            ProductId(prod1.id)
        )
        val prod2 = fixtures.createProduct("prod2")
        val prod2Id = CompoundHierarchyId.forProduct(
            OrganizationId(fixtures.organization.id),
            ProductId(prod2.id)
        )
        fixtures.createProduct("prod3")

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.PRODUCT to listOf(prod1Id)),
            nonTransitiveIncludes = mapOf(HierarchyLevel.PRODUCT to listOf(prod2Id)),
        )
        val result = productRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(
                Product(prod1.id, orgId, prod1.name, prod1.description),
                Product(prod2.id, orgId, prod2.name, prod2.description)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "list should apply a filter with non-transitive includes caused by elements on lower levels" {
        val prod = fixtures.createProduct("prod1")
        val prodId = CompoundHierarchyId.forProduct(
            OrganizationId(fixtures.organization.id),
            ProductId(prod.id)
        )
        val repo = fixtures.createRepository(RepositoryType.GIT, "https://repo.example.org", productId = prod.id)
        val repoId = CompoundHierarchyId.forRepository(
            OrganizationId(fixtures.organization.id),
            ProductId(prod.id),
            RepositoryId(repo.id)
        )

        val hierarchyFilter = HierarchyFilter(
            transitiveIncludes = mapOf(HierarchyLevel.REPOSITORY to listOf(repoId)),
            nonTransitiveIncludes = mapOf(HierarchyLevel.PRODUCT to listOf(prodId)),
        )
        val result = productRepository.list(hierarchyFilter = hierarchyFilter)

        result shouldBe ListQueryResult(
            data = listOf(
                Product(prod.id, orgId, prod.name, prod.description)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 1
        )
    }

    "listForOrganization should return all products for an organization" {
        val otherOrgId = fixtures.createOrganization(name = "otherOrg").id

        val name1 = "name1"
        val description1 = "description1"

        val name2 = "name2"
        val description2 = "description2"

        val createdProduct1 = productRepository.create(name1, description1, orgId)
        val createdProduct2 = productRepository.create(name2, description2, orgId)
        productRepository.create(name1, description1, otherOrgId)

        productRepository.listForOrganization(orgId) shouldBe ListQueryResult(
            data = listOf(
                Product(createdProduct1.id, orgId, name1, description1),
                Product(createdProduct2.id, orgId, name2, description2)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "listForOrganization should apply query parameters" {
        val otherOrgId = fixtures.createOrganization(name = "otherOrg").id

        val name1 = "name1"
        val description1 = "description1"

        val name2 = "name2"
        val description2 = "description2"

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("name", OrderDirection.DESCENDING)),
            limit = 1
        )

        productRepository.create(name1, description1, orgId)
        val createdProduct2 = productRepository.create(name2, description2, orgId)
        productRepository.create(name1, description1, otherOrgId)

        productRepository.listForOrganization(orgId, parameters) shouldBe ListQueryResult(
            data = listOf(Product(createdProduct2.id, orgId, name2, description2)),
            params = parameters,
            totalCount = 2
        )
    }

    "listForOrganization should apply filter parameter" {
        val otherOrgId = fixtures.createOrganization(name = "otherOrg").id

        val name1 = "name1"
        val description1 = "description1"

        val name2 = "test"
        val description2 = "description2"

        productRepository.create(name1, description1, orgId)
        val createdProduct2 = productRepository.create(name2, description2, orgId)
        productRepository.create(name1, description1, otherOrgId)

        productRepository.listForOrganization(orgId, filter = FilterParameter("test")) shouldBe ListQueryResult(
            data = listOf(Product(createdProduct2.id, orgId, name2, description2)),
            params = ListQueryParameters.DEFAULT,
            totalCount = 1
        )
    }

    "listForOrganization should filter with regex ending pattern" {
        val org2 = fixtures.createOrganization(name = "org2")

        fixtures.createProduct("auth-product", organizationId = org2.id)
        val prod2 = fixtures.createProduct("user-product")
        val prod3 = fixtures.createProduct("service-product")
        fixtures.createProduct("product-gateway")
        fixtures.createProduct("core-service")

        productRepository.listForOrganization(orgId, filter = FilterParameter("product$")) shouldBe ListQueryResult(
            data = listOf(
                Product(prod2.id, orgId, prod2.name, prod2.description),
                Product(prod3.id, orgId, prod3.name, prod3.description)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "listForOrganization should filter with regex starting pattern" {
        val org2 = fixtures.createOrganization(name = "org2")

        val prod1 = fixtures.createProduct("product-auth")
        val prod2 = fixtures.createProduct("product-service")
        fixtures.createProduct("product-user", organizationId = org2.id)
        fixtures.createProduct("name")

        productRepository.listForOrganization(orgId, filter = FilterParameter("^product")) shouldBe ListQueryResult(
            data = listOf(
                Product(prod1.id, orgId, prod1.name, prod1.description),
                Product(prod2.id, orgId, prod2.name, prod2.description)
            ),
            params = ListQueryParameters.DEFAULT,
            totalCount = 2
        )
    }

    "update should update an entry in the database" {
        val createdProduct = productRepository.create("name", "description", orgId)

        val updateName = "updatedName".asPresent()
        val updateDescription = "updatedDescription".asPresent()

        val updateResult = productRepository.update(createdProduct.id, updateName, updateDescription)

        updateResult shouldBe Product(
            createdProduct.id,
            orgId,
            updateName.value,
            updateDescription.value,
        )

        productRepository.get(createdProduct.id) shouldBe Product(
            createdProduct.id,
            orgId,
            updateName.value,
            updateDescription.value,
        )
    }

    "delete should delete the database entry" {
        val createdProduct = productRepository.create("name", "description", orgId)

        productRepository.delete(createdProduct.id)

        productRepository.listForOrganization(orgId) shouldBe ListQueryResult(
            data = emptyList(),
            params = ListQueryParameters.DEFAULT,
            totalCount = 0
        )
    }

    "get should return null" {
        productRepository.get(1L).shouldBeNull()
    }

    "get should return the product" {
        val product = productRepository.create("name", "description", orgId)

        productRepository.get(product.id) shouldBe product
    }
})
