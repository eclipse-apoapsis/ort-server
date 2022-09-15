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

package org.ossreviewtoolkit.server.core.api

import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication

import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.dao.repositories.ProductsRepository
import org.ossreviewtoolkit.server.dao.repositories.RepositoriesRepository
import org.ossreviewtoolkit.server.shared.models.api.CreateOrganization
import org.ossreviewtoolkit.server.shared.models.api.CreateProduct
import org.ossreviewtoolkit.server.shared.models.api.CreateRepository
import org.ossreviewtoolkit.server.shared.models.api.Product
import org.ossreviewtoolkit.server.shared.models.api.Repository
import org.ossreviewtoolkit.server.shared.models.api.RepositoryType
import org.ossreviewtoolkit.server.shared.models.api.UpdateProduct
import org.ossreviewtoolkit.server.shared.models.api.common.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class ProductsRouteIntegrationTest : DatabaseTest() {
    var orgId = -1L

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()

        orgId = OrganizationsRepository.createOrganization(
            CreateOrganization("org", "org description")
        ).id
    }

    init {
        test("GET /products/{productId} should return a single product") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                val client = createJsonClient()

                val product = CreateProduct("product", "description")

                val createdProduct = ProductsRepository.createProduct(orgId, product)
                val response = client.get("/api/v1/products/${createdProduct.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Product>() shouldBe Product(createdProduct.id, product.name, product.description)
                }
            }
        }

        test("PATCH /products/{id} should update a product") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                val client = createJsonClient()

                val product = CreateProduct("product", "description")
                val createdProduct = ProductsRepository.createProduct(orgId, product)

                val updatedProduct = UpdateProduct(
                    OptionalValue.Present("updatedProduct"),
                    OptionalValue.Present("updateDescription")
                )
                val response = client.patch("/api/v1/products/${createdProduct.id}") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(updatedProduct)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Product>() shouldBe Product(
                        createdProduct.id,
                        (updatedProduct.name as OptionalValue.Present).value,
                        (updatedProduct.description as OptionalValue.Present).value
                    )
                }
            }
        }

        test("DELETE /products/{id} should delete a product") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                val client = createJsonClient()

                val product = CreateProduct("product", "description")
                val createdProduct = ProductsRepository.createProduct(orgId, product)

                val response = client.delete("/api/v1/products/${createdProduct.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                ProductsRepository.listProductsForOrg(orgId) shouldBe emptyList()
            }
        }

        test("GET /products/{id}/repositories should return all repositories of an organization") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                val client = createJsonClient()

                val product = CreateProduct("product", "description")
                val createdProduct = ProductsRepository.createProduct(orgId, product)

                val repository1 = CreateRepository(RepositoryType.GIT, "https://example.com/repo1.git")
                val repository2 = CreateRepository(RepositoryType.GIT, "https://example.com/repo2.git")

                val createdRepository1 = RepositoriesRepository.createRepository(createdProduct.id, repository1)
                val createdRepository2 = RepositoriesRepository.createRepository(createdProduct.id, repository2)
                val response = client.get("/api/v1/products/${createdProduct.id}/repositories") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Repository>>() shouldBe listOf(
                        Repository(createdRepository1.id, repository1.type, repository1.url),
                        Repository(createdRepository2.id, repository2.type, repository2.url)
                    )
                }
            }
        }

        test("POST /products/{id}/repositories should create a repository") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                val client = createJsonClient()

                val product = CreateProduct("product", "description")
                val createdProduct = ProductsRepository.createProduct(orgId, product)

                val repository = CreateRepository(RepositoryType.GIT, "https://example.com/repo.git")
                val response = client.post("/api/v1/products/${createdProduct.id}/repositories") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(repository)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Repository>() shouldBe Repository(1, repository.type, repository.url)
                }
            }
        }
    }
}
