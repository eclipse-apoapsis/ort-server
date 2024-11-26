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
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.Product
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

        productRepository.list() should containExactlyInAnyOrder(prod1, prod2)
    }

    "list should apply parameters" {
        val org2 = fixtures.createOrganization(name = "org2")

        fixtures.createProduct("prod1")
        val prod2 = fixtures.createProduct("prod2", organizationId = org2.id)

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("name", OrderDirection.DESCENDING)),
            limit = 1
        )

        productRepository.list(parameters) should containExactly(prod2)
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
