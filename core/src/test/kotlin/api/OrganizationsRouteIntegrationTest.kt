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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.CreateInfrastructureService
import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.InfrastructureService as ApiInfrastructureService
import org.ossreviewtoolkit.server.api.v1.Organization
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.UpdateInfrastructureService
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakClientForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.services.DefaultAuthorizationService
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.ProductService

class OrganizationsRouteIntegrationTest : StringSpec() {
    private val dbExtension = extension(DatabaseTestExtension())
    private val keycloak = install(KeycloakTestExtension(createRealmPerTest = true))
    private val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    private val keycloakClient = keycloak.createKeycloakClientForTestRealm()

    private lateinit var organizationService: OrganizationService
    private lateinit var productService: ProductService

    private lateinit var infrastructureServiceRepository: InfrastructureServiceRepository
    private lateinit var secretRepository: SecretRepository

    init {
        beforeEach {
            val authorizationService = DefaultAuthorizationService(
                keycloakClient,
                dbExtension.db,
                dbExtension.fixtures.organizationRepository,
                dbExtension.fixtures.productRepository,
                dbExtension.fixtures.repositoryRepository
            )

            organizationService = OrganizationService(
                dbExtension.db,
                dbExtension.fixtures.organizationRepository,
                dbExtension.fixtures.productRepository,
                authorizationService
            )

            productService = ProductService(
                dbExtension.db,
                dbExtension.fixtures.productRepository,
                dbExtension.fixtures.repositoryRepository,
                authorizationService
            )

            infrastructureServiceRepository = dbExtension.fixtures.infrastructureServiceRepository
            secretRepository = dbExtension.fixtures.secretRepository
        }

        "GET /organizations should return all existing organizations" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val org1 = organizationService.createOrganization(name = "name1", description = "description1")
                val org2 = organizationService.createOrganization(name = "name2", description = "description2")

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Organization>>() shouldBe listOf(org1.mapToApi(), org2.mapToApi())
                }
            }
        }

        "GET /organizations should support query parameters" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                organizationService.createOrganization(name = "name1", description = "description1")
                val org2 = organizationService.createOrganization(name = "name2", description = "description2")

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations?sort=-name&limit=1") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Organization>>() shouldBe listOf(org2.mapToApi())
                }
            }
        }

        "GET /organizations/{organizationId} should return a single organization" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val name = "name"
                val description = "description"

                val createdOrganization = organizationService.createOrganization(name = name, description = description)

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/${createdOrganization.id}") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Organization>() shouldBe Organization(createdOrganization.id, name, description)
                }
            }
        }

        "GET /organizations/{organizationId} should respond with NotFound if no organization exists" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/999999") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        "POST /organizations should create an organization in the database" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val org = CreateOrganization(name = "name", description = "description")

                val response = client.post("/api/v1/organizations") {
                    headers { basicTestAuth() }
                    setBody(org)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Organization>() shouldBe Organization(1, org.name, org.description)
                }

                organizationService.getOrganization(1)?.mapToApi().shouldBe(
                    Organization(1, org.name, org.description)
                )
            }
        }

        "POST /organizations should create Keycloak roles" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val org = CreateOrganization(name = "name", description = "description")

                val createdOrg = client.post("/api/v1/organizations") {
                    headers { basicTestAuth() }
                    setBody(org)
                }.body<Organization>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    OrganizationPermission.getRolesForOrganization(createdOrg.id)
                )
            }
        }

        "POST /organizations with an already existing organization should respond with CONFLICT" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val name = "name"
                val description = "description"

                organizationService.createOrganization(name = name, description = description)

                val org = CreateOrganization(name = name, description = description)

                val client = createJsonClient()

                val response = client.post("/api/v1/organizations") {
                    headers { basicTestAuth() }
                    setBody(org)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }

        "PATCH /organizations/{organizationId} should update an organization" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val createdOrg = organizationService.createOrganization(name = "name", description = "description")

                val client = createJsonClient()

                val updatedOrganization = UpdateOrganization(
                    "updated".asPresent(),
                    "updated description of testOrg".asPresent()
                )
                val response = client.patch("/api/v1/organizations/${createdOrg.id}") {
                    headers { basicTestAuth() }
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

                organizationService.getOrganization(createdOrg.id)?.mapToApi() shouldBe Organization(
                    createdOrg.id,
                    (updatedOrganization.name as OptionalValue.Present).value,
                    (updatedOrganization.description as OptionalValue.Present).value
                )
            }
        }

        "PATCH /organizations/{organizationId} should be able to delete a value and ignore absent values" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val name = "name"
                val description = "description"

                val createdOrg = organizationService.createOrganization(name = name, description = description)

                val client = createJsonClient()

                val organizationUpdateRequest = UpdateOrganization(
                    name = OptionalValue.Absent,
                    description = null.asPresent()
                )

                val response = client.patch("/api/v1/organizations/${createdOrg.id}") {
                    headers { basicTestAuth() }
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

                organizationService.getOrganization(createdOrg.id)?.mapToApi() shouldBe Organization(
                    id = createdOrg.id,
                    name = name,
                    description = null
                )
            }
        }

        "DELETE /organizations/{organizationId} should delete an organization" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val createdOrg = organizationService.createOrganization(name = "name", description = "description")

                val client = createJsonClient()

                val response = client.delete("/api/v1/organizations/${createdOrg.id}") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                organizationService.listOrganizations() shouldBe emptyList()
            }
        }

        "DELETE /organizations/{organizationId} should delete Keycloak roles" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val createdOrg = organizationService.createOrganization(name = "name", description = "description")

                val client = createJsonClient()

                client.delete("/api/v1/organizations/${createdOrg.id}") {
                    headers { basicTestAuth() }
                }

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    OrganizationPermission.getRolesForOrganization(createdOrg.id)
                )
            }
        }

        "POST /organizations/{orgId}/products should create a product" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val product = CreateProduct("product", "description")
                val response = client.post("/api/v1/organizations/$orgId/products") {
                    headers { basicTestAuth() }
                    setBody(product)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Product>() shouldBe Product(1, product.name, product.description)
                }

                productService.getProduct(1)?.mapToApi() shouldBe Product(1, product.name, product.description)
            }
        }

        "POST /organizations/{orgId}/products should create Keycloak roles" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val product = CreateProduct(name = "product", description = "description")
                val createdProduct = client.post("/api/v1/organizations/$orgId/products") {
                    headers { basicTestAuth() }
                    setBody(product)
                }.body<Product>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    ProductPermission.getRolesForProduct(createdProduct.id)
                )
            }
        }

        "GET /organizations/{orgId}/products should return all products of an organization" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                val createdProduct1 =
                    organizationService.createProduct(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    organizationService.createProduct(name = name2, description = description, organizationId = orgId)

                val response = client.get("/api/v1/organizations/$orgId/products") {
                    headers { basicTestAuth() }
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
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                organizationService.createProduct(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    organizationService.createProduct(name = name2, description = description, organizationId = orgId)

                val response = client.get("/api/v1/organizations/$orgId/products?sort=-name&limit=1") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Product>>() shouldBe listOf(
                        Product(createdProduct2.id, name2, description)
                    )
                }
            }
        }

        "GET /organizations/{orgId}/infrastructure-services should list existing infrastructure services" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val userSecret = secretRepository.create("p1", "s1", null, orgId, null, null)
                val passSecret = secretRepository.create("p2", "s2", null, orgId, null, null)

                val services = (1..8).map { index ->
                    infrastructureServiceRepository.create(
                        "infrastructureService$index",
                        "https://repo.example.org/test$index",
                        "description$index",
                        userSecret,
                        passSecret,
                        orgId,
                        null
                    )
                }

                val apiServices = services.map { service ->
                    ApiInfrastructureService(
                        service.name,
                        service.url,
                        service.description,
                        service.usernameSecret.name,
                        service.passwordSecret.name
                    )
                }

                val response = client.get("/api/v1/organizations/$orgId/infrastructure-services") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<ApiInfrastructureService>>() shouldContainExactlyInAnyOrder apiServices
                }
            }
        }

        "GET /organizations/{orgId}/infrastructure-services should support query parameters" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val userSecret = secretRepository.create("p1", "s1", null, orgId, null, null)
                val passSecret = secretRepository.create("p2", "s2", null, orgId, null, null)

                (1..8).shuffled().forEach { index ->
                    infrastructureServiceRepository.create(
                        "infrastructureService$index",
                        "https://repo.example.org/test$index",
                        "description$index",
                        userSecret,
                        passSecret,
                        orgId,
                        null
                    )
                }

                val apiServices = (1..4).map { index ->
                    ApiInfrastructureService(
                        "infrastructureService$index",
                        "https://repo.example.org/test$index",
                        "description$index",
                        userSecret.name,
                        passSecret.name
                    )
                }

                val response = client.get("/api/v1/organizations/$orgId/infrastructure-services?sort=name&limit=4") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<ApiInfrastructureService>>() shouldContainExactlyInAnyOrder apiServices
                }
            }
        }

        "POST /organizations/{orgId}/infrastructure-services should create an infrastructure service" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val userSecret = secretRepository.create("p1", "s1", null, orgId, null, null)
                val passSecret = secretRepository.create("p2", "s2", null, orgId, null, null)

                val createInfrastructureService = CreateInfrastructureService(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test description",
                    userSecret.name,
                    passSecret.name
                )
                val response = client.post("/api/v1/organizations/$orgId/infrastructure-services") {
                    headers { basicTestAuth() }
                    setBody(createInfrastructureService)
                }

                val expectedService = ApiInfrastructureService(
                    createInfrastructureService.name,
                    createInfrastructureService.url,
                    createInfrastructureService.description,
                    userSecret.name,
                    passSecret.name
                )

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<ApiInfrastructureService>() shouldBe expectedService
                }

                val dbService =
                    infrastructureServiceRepository.getByOrganizationAndName(orgId, createInfrastructureService.name)
                dbService.shouldNotBeNull()
                dbService.mapToApi() shouldBe expectedService
            }
        }

        "POST /organizations/{orgId}/infrastructure-services should handle an invalid secret reference" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val createInfrastructureService = CreateInfrastructureService(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test description",
                    "nonExistingSecret1",
                    "nonExistingSecret2"
                )
                val response = client.post("/api/v1/organizations/$orgId/infrastructure-services") {
                    headers { basicTestAuth() }
                    setBody(createInfrastructureService)
                }

                with(response) {
                    status shouldBe HttpStatusCode.BadRequest
                    val error = body<ErrorResponse>()
                    error.cause shouldContain "nonExistingSecret"
                }
            }
        }

        "PATCH /organizations/{orgId}/infrastructure-services/{name} should update an infrastructure service" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val userSecret = secretRepository.create("p1", "s1", null, orgId, null, null)
                val passSecret = secretRepository.create("p2", "s2", null, orgId, null, null)

                val service = infrastructureServiceRepository.create(
                    "updateService",
                    "http://repo1.example.org/test",
                    "test description",
                    userSecret,
                    passSecret,
                    orgId,
                    null
                    )

                val newUrl = "https://repo2.example.org/test2"
                val updateService = UpdateInfrastructureService(
                    description = null.asPresent(),
                    url = newUrl.asPresent()
                )
                val response = client.patch("/api/v1/organizations/$orgId/infrastructure-services/${service.name}") {
                    headers { basicTestAuth() }
                    setBody(updateService)
                }

                val updatedService = ApiInfrastructureService(
                    service.name,
                    newUrl,
                    null,
                    userSecret.name,
                    passSecret.name
                )

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<ApiInfrastructureService>() shouldBe updatedService
                }

                val dbService =
                    infrastructureServiceRepository.getByOrganizationAndName(orgId, service.name)
                dbService.shouldNotBeNull()
                dbService.mapToApi() shouldBe updatedService
            }
        }

        "DELETE /organizations/{orgId}/infrastructure-services/{name} should delete an infrastructure service" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val orgId = organizationService.createOrganization(name = "name", description = "description").id

                val userSecret = secretRepository.create("p1", "s1", null, orgId, null, null)
                val passSecret = secretRepository.create("p2", "s2", null, orgId, null, null)

                val service = infrastructureServiceRepository.create(
                    "deleteService",
                    "http://repo1.example.org/obsolete",
                    "good bye, cruel world",
                    userSecret,
                    passSecret,
                    orgId,
                    null
                )

                val response = client.delete("/api/v1/organizations/$orgId/infrastructure-services/${service.name}") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                val services = infrastructureServiceRepository.listForOrganization(orgId)
                services should beEmpty()
            }
        }
    }
}
