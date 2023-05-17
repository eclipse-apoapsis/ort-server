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

import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.Organization
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakClientForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent

class OrganizationsRouteIntegrationTest : StringSpec() {
    private val keycloak = install(KeycloakTestExtension(createRealmPerTest = true))
    private val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    private val keycloakClient = keycloak.createKeycloakClientForTestRealm()

    private lateinit var organizationRepository: OrganizationRepository
    private lateinit var productRepository: ProductRepository

    override suspend fun beforeTest(testCase: TestCase) {
        organizationRepository = DaoOrganizationRepository()
        productRepository = DaoProductRepository()
    }

    init {
        extension(DatabaseTestExtension())

        "GET /organizations should return all existing organizations" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
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

        "GET /organizations should support query parameters" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
                organizationRepository.create(name = "name1", description = "description1")
                val org2 = organizationRepository.create(name = "name2", description = "description2")

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations?sort=-name&limit=1") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Organization>>() shouldBe listOf(org2.mapToApi())
                }
            }
        }

        "GET /organizations/{organizationId} should return a single organization" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
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

        "GET /organizations/{organizationId} should respond with NotFound if no organization exists" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
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

        "POST /organizations should create an organization in the database" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
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

        "POST /organizations should create Keycloak roles" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val org = CreateOrganization(name = "name", description = "description")

                val createdOrg = client.post("/api/v1/organizations") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(org)
                }.body<Organization>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    OrganizationPermission.getRolesForOrganization(createdOrg.id)
                )
            }
        }

        "POST /organizations with an already existing organization should respond with CONFLICT" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
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

        "PATCH /organizations/{organizationId} should update an organization" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val createdOrg = organizationRepository.create(name = "name", description = "description")

                val client = createJsonClient()

                val updatedOrganization = UpdateOrganization(
                    "updated".asPresent(),
                    "updated description of testOrg".asPresent()
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

        "PATCH /organizations/{organizationId} should be able to delete a value and ignore absent values" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val name = "name"
                val description = "description"

                val createdOrg = organizationRepository.create(name = name, description = description)

                val client = createJsonClient()

                val organizationUpdateRequest = UpdateOrganization(
                    name = OptionalValue.Absent,
                    description = null.asPresent()
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

        "DELETE /organizations/{organizationId} should delete an organization" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
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

        "DELETE /organizations/{organizationId} should delete Keycloak roles" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val createdOrg = organizationRepository.create(name = "name", description = "description")

                val client = createJsonClient()

                client.delete("/api/v1/organizations/${createdOrg.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    OrganizationPermission.getRolesForOrganization(createdOrg.id)
                )
            }
        }

        "POST /organizations/{orgId}/products should create a product" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
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

        "GET /organizations/{orgId}/products should return all products of an organization" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
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

        "GET /organizations/{orgId}/products should support query parameters" {
            ortServerTestApplication(noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationRepository.create(name = "name", description = "description").id

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                productRepository.create(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    productRepository.create(name = name2, description = description, organizationId = orgId)

                val response = client.get("/api/v1/organizations/$orgId/products?sort=-name&limit=1") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Product>>() shouldBe listOf(
                        Product(createdProduct2.id, name2, description)
                    )
                }
            }
        }
    }
}
