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

import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.Organization
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.migrate
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class OrganizationsRouteIntegrationTest : DatabaseTest() {
    private lateinit var organizationRepository: OrganizationRepository
    private lateinit var productRepository: ProductRepository

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()
        dataSource.migrate()

        organizationRepository = DaoOrganizationRepository()
        productRepository = DaoProductRepository()
    }

    init {
        test("GET /organizations should return all existing organizations") {
            ortServerTestApplication(noDbConfig) {
                val org1 = organizationRepository.create(name = "name1", description = "description1")
                val org2 = organizationRepository.create(name = "name2", description = "description2")

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Organization>>() shouldBe listOf(org1.mapToApi(), org2.mapToApi())
                }
            }
        }

        test("GET /organizations/{organizationId} should return a single organization") {
            ortServerTestApplication(noDbConfig) {
                val name = "name"
                val description = "description"

                val createdOrganization = organizationRepository.create(name = name, description = description)

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/${createdOrganization.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Organization>() shouldBe Organization(createdOrganization.id, name, description)
                }
            }
        }

        test("GET /organizations/{organizationId} should respond with NotFound if no organization exists") {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/999999") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        test("POST /organizations should create an organization in the database") {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val org = CreateOrganization(name = "name", description = "description")

                val response = client.post("/api/v1/organizations") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(org)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Organization>() shouldBe Organization(1, org.name, org.description)
                }

                organizationRepository.get(1)?.mapToApi().shouldBe(
                    Organization(1, org.name, org.description)
                )
            }
        }

        test("POST /organizations with an already existing organization should respond with CONFLICT") {
            ortServerTestApplication(noDbConfig) {
                val name = "name"
                val description = "description"

                organizationRepository.create(name = name, description = description)

                val org = CreateOrganization(name = name, description = description)

                val client = createJsonClient()

                val response = client.post("/api/v1/organizations") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(org)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }

        test("PATCH /organizations/{organizationId} should update an organization") {
            ortServerTestApplication(noDbConfig) {
                val createdOrg = organizationRepository.create(name = "name", description = "description")

                val client = createJsonClient()

                val updatedOrganization = UpdateOrganization(
                    OptionalValue.Present("updated"),
                    OptionalValue.Present("updated description of testOrg")
                )
                val response = client.patch("/api/v1/organizations/${createdOrg.id}") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(updatedOrganization)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Organization>() shouldBe Organization(
                        createdOrg.id,
                        (updatedOrganization.name as OptionalValue.Present).value,
                        (updatedOrganization.description as OptionalValue.Present).value
                    )
                }

                organizationRepository.get(createdOrg.id)?.mapToApi() shouldBe Organization(
                    createdOrg.id,
                    (updatedOrganization.name as OptionalValue.Present).value,
                    (updatedOrganization.description as OptionalValue.Present).value
                )
            }
        }

        test("PATCH /organizations/{organizationId} should be able to delete a value and ignore absent values") {
            ortServerTestApplication(noDbConfig) {
                val name = "name"
                val description = "description"

                val createdOrg = organizationRepository.create(name = name, description = description)

                val client = createJsonClient(Json)

                val organizationUpdateRequest = UpdateOrganization(
                    name = OptionalValue.Absent,
                    description = OptionalValue.Present(null)
                )

                val response = client.patch("/api/v1/organizations/${createdOrg.id}") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(organizationUpdateRequest)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Organization>() shouldBe Organization(
                        id = createdOrg.id,
                        name = name,
                        description = null
                    )
                }

                organizationRepository.get(createdOrg.id)?.mapToApi() shouldBe Organization(
                    id = createdOrg.id,
                    name = name,
                    description = null
                )
            }
        }

        test("DELETE /organizations/{organizationId} should delete an organization") {
            ortServerTestApplication(noDbConfig) {
                val createdOrg = organizationRepository.create(name = "name", description = "description")

                val client = createJsonClient()

                val response = client.delete("/api/v1/organizations/${createdOrg.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                organizationRepository.list() shouldBe emptyList()
            }
        }

        test("POST /organizations/{orgId}/products should create a product") {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val orgId = organizationRepository.create(name = "name", description = "description").id

                val product = CreateProduct("product", "description")
                val response = client.post("/api/v1/organizations/$orgId/products") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(product)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Product>() shouldBe Product(1, product.name, product.description)
                }

                productRepository.get(1)?.mapToApi() shouldBe Product(1, product.name, product.description)
            }
        }

        test("GET /organizations/{orgId}/products should return all products of an organization") {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                val orgId = organizationRepository.create(name = "name", description = "description").id

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                val createdProduct1 =
                    productRepository.create(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    productRepository.create(name = name2, description = description, organizationId = orgId)

                val response = client.get("/api/v1/organizations/$orgId/products") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Product>>() shouldBe listOf(
                        Product(createdProduct1.id, name1, description),
                        Product(createdProduct2.id, name2, description)
                    )
                }
            }
        }
    }
}
