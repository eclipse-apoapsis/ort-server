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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.CreateRepository
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.api.v1.UpdateProduct
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent

class ProductsRouteIntegrationTest : StringSpec() {
    private lateinit var organizationRepository: OrganizationRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var repositoryRepository: RepositoryRepository

    private var orgId = -1L

    init {
        extension(
            DatabaseTestExtension {
                organizationRepository = DaoOrganizationRepository()
                productRepository = DaoProductRepository()
                repositoryRepository = DaoRepositoryRepository()

                orgId = organizationRepository.create(name = "name", description = "description").id
            }
        )

        "GET /products/{productId} should return a single product" {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val name = "name"
                val description = "description"

                val createdProduct =
                    productRepository.create(name = name, description = description, organizationId = orgId)

                val response = client.get("/api/v1/products/${createdProduct.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Product>() shouldBe Product(createdProduct.id, name, description)
                }
            }
        }

        "PATCH /products/{id} should update a product" {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val createdProduct =
                    productRepository.create(name = "name", description = "description", organizationId = orgId)

                val updatedProduct = UpdateProduct(
                    "updatedProduct".asPresent(),
                    "updateDescription".asPresent()
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

        "DELETE /products/{id} should delete a product" {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val createdProduct =
                    productRepository.create(name = "name", description = "description", organizationId = orgId)

                val response = client.delete("/api/v1/products/${createdProduct.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                productRepository.listForOrganization(orgId) shouldBe emptyList()
            }
        }

        "GET /products/{id}/repositories should return all repositories of an organization" {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val createdProduct =
                    productRepository.create(name = "name", description = "description", organizationId = orgId)

                val type = RepositoryType.GIT
                val url1 = "https://example.com/repo1.git"
                val url2 = "https://example.com/repo2.git"

                val createdRepository1 =
                    repositoryRepository.create(type = type, url = url1, productId = createdProduct.id)
                val createdRepository2 =
                    repositoryRepository.create(type = type, url = url2, productId = createdProduct.id)

                val response = client.get("/api/v1/products/${createdProduct.id}/repositories") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Repository>>() shouldBe listOf(
                        Repository(createdRepository1.id, type.mapToApi(), url1),
                        Repository(createdRepository2.id, type.mapToApi(), url2)
                    )
                }
            }
        }

        "POST /products/{id}/repositories should create a repository" {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val createdProduct =
                    productRepository.create(name = "name", description = "description", organizationId = orgId)

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git")
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
