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
import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.dao.UniqueConstraintException
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.dao.repositories.ProductsRepository
import org.ossreviewtoolkit.server.shared.models.api.CreateOrganization
import org.ossreviewtoolkit.server.shared.models.api.CreateProduct
import org.ossreviewtoolkit.server.shared.models.api.Product
import org.ossreviewtoolkit.server.shared.models.api.UpdateProduct
import org.ossreviewtoolkit.server.shared.models.api.common.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class ProductsRepositoryTest : DatabaseTest() {
    private var orgId = -1L

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()

        orgId = OrganizationsRepository.createOrganization(
            CreateOrganization(name = "org", description = "org description")
        ).id
    }

    init {
        test("createProduct should create an entry in the database") {
            val product = CreateProduct("product", "description")

            val createdProduct = ProductsRepository.createProduct(orgId, product)

            val dbEntry = ProductsRepository.getProduct(createdProduct.id)
            dbEntry.shouldNotBeNull()

            dbEntry.mapToApiModel() shouldBe Product(createdProduct.id, product.name, product.description)
        }

        test("createProduct with the same product name and organization should throw") {
            val product = CreateProduct("product", "description")

            ProductsRepository.createProduct(orgId, product)

            shouldThrow<UniqueConstraintException> {
                ProductsRepository.createProduct(orgId, product)
            }
        }

        test("listProductsForOrg should return all products for an organization") {
            val otherOrgId = OrganizationsRepository.createOrganization(
                CreateOrganization(name = "otherOrg", description = "org description")
            ).id

            val product1 = CreateProduct("product1", "description1")
            val product2 = CreateProduct("product2", "description2")
            val otherOrgProduct = CreateProduct("product1", "description1")

            val createdProduct1 = ProductsRepository.createProduct(orgId, product1)
            val createdProduct2 = ProductsRepository.createProduct(orgId, product2)
            ProductsRepository.createProduct(otherOrgId, otherOrgProduct)

            ProductsRepository.listProductsForOrg(orgId).map { it.mapToApiModel() } shouldBe listOf(
                Product(createdProduct1.id, product1.name, product1.description),
                Product(createdProduct2.id, product2.name, product2.description)
            )
        }

        test("updateProduct should update an entry in the database") {
            val product = CreateProduct("product", "description")

            val createdProduct = ProductsRepository.createProduct(orgId, product)

            val updatedOrg = UpdateProduct(
                OptionalValue.Present("updatedProduct"),
                OptionalValue.Present("updatedDescription")
            )
            val updateResult = ProductsRepository.updateProduct(createdProduct.id, updatedOrg)

            updateResult.mapToApiModel() shouldBe Product(
                createdProduct.id,
                (updatedOrg.name as OptionalValue.Present).value,
                (updatedOrg.description as OptionalValue.Present).value,
            )

            ProductsRepository.getProduct(createdProduct.id)?.mapToApiModel() shouldBe Product(
                createdProduct.id,
                (updatedOrg.name as OptionalValue.Present).value,
                (updatedOrg.description as OptionalValue.Present).value,
            )
        }

        test("deleteProduct should delete the database entry") {
            val product = CreateProduct("product", "description")

            val createdProduct = ProductsRepository.createProduct(orgId, product)

            ProductsRepository.deleteProduct(createdProduct.id)

            ProductsRepository.listProductsForOrg(orgId) shouldBe emptyList()
        }
    }
}
