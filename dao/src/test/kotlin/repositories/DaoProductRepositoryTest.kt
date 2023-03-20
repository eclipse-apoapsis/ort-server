/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.test.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.dao.UniqueConstraintException
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OrderDirection
import org.ossreviewtoolkit.server.model.util.OrderField
import org.ossreviewtoolkit.server.model.util.asPresent

class DaoProductRepositoryTest : StringSpec() {
    private val productRepository = DaoProductRepository()

    private lateinit var fixtures: Fixtures
    private var orgId = -1L

    init {
        extension(
            DatabaseTestExtension {
                fixtures = Fixtures()
                orgId = fixtures.organization.id
            }
        )

        "create should create an entry in the database" {
            val name = "name"
            val description = "description"

            val createdProduct = productRepository.create(name, description, orgId)

            val dbEntry = productRepository.get(createdProduct.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe Product(createdProduct.id, name, description)
        }

        "create with the same product name and organization should throw" {
            val name = "name"
            val description = "description"

            productRepository.create(name, description, orgId)

            shouldThrow<UniqueConstraintException> {
                productRepository.create(name, description, orgId)
            }
        }

        "listForOrganization should return all products for an organization" {
            val otherOrgId = fixtures.createOrganization(name = "ortherOrg").id

            val name1 = "name1"
            val description1 = "description1"

            val name2 = "name2"
            val description2 = "description2"

            val createdProduct1 = productRepository.create(name1, description1, orgId)
            val createdProduct2 = productRepository.create(name2, description2, orgId)
            productRepository.create(name1, description1, otherOrgId)

            productRepository.listForOrganization(orgId) shouldBe listOf(
                Product(createdProduct1.id, name1, description1),
                Product(createdProduct2.id, name2, description2)
            )
        }

        "listForOrganization should apply query parameters" {
            val otherOrgId = fixtures.createOrganization(name = "ortherOrg").id

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

            productRepository.listForOrganization(orgId, parameters) shouldBe listOf(
                Product(createdProduct2.id, name2, description2)
            )
        }

        "update should update an entry in the database" {
            val createdProduct = productRepository.create("name", "description", orgId)

            val updateName = "updatedName".asPresent()
            val updateDescription = "updatedDescription".asPresent()

            val updateResult = productRepository.update(createdProduct.id, updateName, updateDescription)

            updateResult shouldBe Product(
                createdProduct.id,
                updateName.value,
                updateDescription.value,
            )

            productRepository.get(createdProduct.id) shouldBe Product(
                createdProduct.id,
                updateName.value,
                updateDescription.value,
            )
        }

        "delete should delete the database entry" {
            val createdProduct = productRepository.create("name", "description", orgId)

            productRepository.delete(createdProduct.id)

            productRepository.listForOrganization(orgId) shouldBe emptyList()
        }

        "get should return null" {
            productRepository.get(1L).shouldBeNull()
        }

        "get should return the product" {
            val product = productRepository.create("name", "description", orgId)

            productRepository.get(product.id) shouldBe product
        }
    }
}
