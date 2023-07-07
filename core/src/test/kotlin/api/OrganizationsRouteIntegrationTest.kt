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

import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.CreateInfrastructureService
import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.InfrastructureService as ApiInfrastructureService
import org.ossreviewtoolkit.server.api.v1.Organization
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateInfrastructureService
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.OrganizationRole
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.ProductRole
import org.ossreviewtoolkit.server.model.authorization.Superuser
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.SecretsProviderFactoryForTesting
import org.ossreviewtoolkit.server.services.DefaultAuthorizationService
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.ProductService

@Suppress("LargeClass")
class OrganizationsRouteIntegrationTest : AbstractIntegrationTest({
    lateinit var organizationService: OrganizationService
    lateinit var productService: ProductService

    lateinit var infrastructureServiceRepository: InfrastructureServiceRepository
    lateinit var secretRepository: SecretRepository

    beforeEach {
        val authorizationService = DefaultAuthorizationService(
            keycloakClient,
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            keycloakGroupPrefix = ""
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

    val organizationName = "name"
    val organizationDescription = "description"

    suspend fun createOrganization(name: String = organizationName, description: String = organizationDescription) =
        organizationService.createOrganization(name, description)

    "GET /organizations" should {
        "return all existing organizations" {
            integrationTestApplication {
                val org1 = createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "name2", description = "description2")

                val response = superuserClient.get("/api/v1/organizations")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Organization>>() shouldBe listOf(org1.mapToApi(), org2.mapToApi())
                }
            }
        }

        "support query parameters" {
            integrationTestApplication {
                createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "name2", description = "description2")

                val response = superuserClient.get("/api/v1/organizations?sort=-name&limit=1")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Organization>>() shouldBe listOf(org2.mapToApi())
                }
            }
        }

        "require the superuser role" {
            requestShouldRequireRole(Superuser.ROLE_NAME) {
                get("/api/v1/organizations")
            }
        }
    }

    "GET /organizations/{organizationId}" should {
        "return a single organization" {
            integrationTestApplication {
                val createdOrganization = createOrganization()

                val response = superuserClient.get("/api/v1/organizations/${createdOrganization.id}")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Organization>() shouldBe
                            Organization(createdOrganization.id, organizationName, organizationDescription)
                }
            }
        }

        "respond with NotFound if no organization exists" {
            integrationTestApplication {
                val response = superuserClient.get("/api/v1/organizations/999999")

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}")
            }
        }
    }

    "POST /organizations" should {
        "create an organization in the database" {
            integrationTestApplication {
                val org = CreateOrganization(name = "name", description = "description")

                val response = superuserClient.post("/api/v1/organizations") {
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

        "create Keycloak roles and groups" {
            integrationTestApplication {
                val org = CreateOrganization(name = "name", description = "description")

                val createdOrg = superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                }.body<Organization>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    OrganizationPermission.getRolesForOrganization(createdOrg.id) +
                            OrganizationRole.getRolesForOrganization(createdOrg.id)
                )

                keycloakClient.getGroups().map { it.name.value } should containAll(
                    OrganizationRole.getGroupsForOrganization(createdOrg.id)
                )
            }
        }

        "respond with CONFLICT if the organization already exists" {
            integrationTestApplication {
                createOrganization()

                val org = CreateOrganization(name = organizationName, description = organizationDescription)

                val response = superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }

        "require the superuser role" {
            requestShouldRequireRole(Superuser.ROLE_NAME, HttpStatusCode.Created) {
                val org = CreateOrganization(name = "name", description = "description")
                post("/api/v1/organizations") { setBody(org) }
            }
        }
    }

    "PATCH /organizations/{organizationId}" should {
        "update an organization" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                val updatedOrganization = UpdateOrganization(
                    "updated".asPresent(),
                    "updated description of testOrg".asPresent()
                )
                val response = superuserClient.patch("/api/v1/organizations/${createdOrg.id}") {
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

        "be able to delete a value and ignore absent values" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                val organizationUpdateRequest = UpdateOrganization(
                    name = OptionalValue.Absent,
                    description = null.asPresent()
                )

                val response = superuserClient.patch("/api/v1/organizations/${createdOrg.id}") {
                    setBody(organizationUpdateRequest)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Organization>() shouldBe Organization(
                        id = createdOrg.id,
                        name = organizationName,
                        description = null
                    )
                }

                organizationService.getOrganization(createdOrg.id)?.mapToApi() shouldBe Organization(
                    id = createdOrg.id,
                    name = organizationName,
                    description = null
                )
            }
        }

        "require OrganizationPermission.WRITE" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.WRITE.roleName(createdOrg.id)) {
                val updateOrg = UpdateOrganization("updated".asPresent(), "updated".asPresent())
                patch("/api/v1/organizations/${createdOrg.id}") { setBody(updateOrg) }
            }
        }
    }

    "DELETE /organizations/{organizationId}" should {
        "delete an organization" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                val response = superuserClient.delete("/api/v1/organizations/${createdOrg.id}")

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                organizationService.listOrganizations() shouldBe emptyList()
            }
        }

        "delete Keycloak roles and groups" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                superuserClient.delete("/api/v1/organizations/${createdOrg.id}")

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    OrganizationPermission.getRolesForOrganization(createdOrg.id) +
                            OrganizationRole.getRolesForOrganization(createdOrg.id)
                )

                keycloakClient.getGroups().map { it.name.value } shouldNot containAnyOf(
                    OrganizationRole.getGroupsForOrganization(createdOrg.id)
                )
            }
        }

        "require OrganizationPermission.DELETE" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.DELETE.roleName(createdOrg.id), HttpStatusCode.NoContent) {
                delete("/api/v1/organizations/${createdOrg.id}")
            }
        }
    }

    "POST /organizations/{orgId}/products" should {
        "create a product" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val product = CreateProduct("product", "description")
                val response = superuserClient.post("/api/v1/organizations/$orgId/products") {
                    setBody(product)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Product>() shouldBe Product(1, product.name, product.description)
                }

                productService.getProduct(1)?.mapToApi() shouldBe Product(1, product.name, product.description)
            }
        }

        "create Keycloak roles and groups" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val product = CreateProduct(name = "product", description = "description")
                val createdProduct = superuserClient.post("/api/v1/organizations/$orgId/products") {
                    setBody(product)
                }.body<Product>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    ProductPermission.getRolesForProduct(createdProduct.id) +
                            ProductRole.getRolesForProduct(createdProduct.id)
                )

                keycloakClient.getGroups().map { it.name.value } should containAll(
                    ProductRole.getGroupsForProduct(createdProduct.id)
                )
            }
        }

        "require OrganizationPermission.CREATE_PRODUCT" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(
                OrganizationPermission.CREATE_PRODUCT.roleName(createdOrg.id),
                HttpStatusCode.Created
            ) {
                val createProduct = CreateProduct(name = "product", description = "description")
                post("/api/v1/organizations/${createdOrg.id}/products") { setBody(createProduct) }
            }
        }
    }

    "GET /organizations/{orgId}/products" should {
        "return all products of an organization" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                val createdProduct1 =
                    organizationService.createProduct(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    organizationService.createProduct(name = name2, description = description, organizationId = orgId)

                val response = superuserClient.get("/api/v1/organizations/$orgId/products")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Product>>() shouldBe listOf(
                        Product(createdProduct1.id, name1, description),
                        Product(createdProduct2.id, name2, description)
                    )
                }
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                organizationService.createProduct(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    organizationService.createProduct(name = name2, description = description, organizationId = orgId)

                val response = superuserClient.get("/api/v1/organizations/$orgId/products?sort=-name&limit=1")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Product>>() shouldBe listOf(
                        Product(createdProduct2.id, name2, description)
                    )
                }
            }
        }

        "require OrganizationPermission.READ_PRODUCTS" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ_PRODUCTS.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}/products")
            }
        }
    }

    "GET /organizations/{organizationId}/secrets" should {
        "return all secrets for this organization" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_1",
                    "New secret 1",
                    "The new org secret",
                    organizationId,
                    null,
                    null
                )
                val secret2 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_2",
                    "New secret 2",
                    "The new org secret",
                    organizationId,
                    null,
                    null
                )

                val response = superuserClient.get("/api/v1/organizations/$organizationId/secrets")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi(), secret2.mapToApi())
                }
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_3",
                    "New secret 3",
                    "The new org secret",
                    organizationId,
                    null,
                    null
                )
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_4",
                    "New secret 4",
                    "The new org secret",
                    organizationId,
                    null,
                    null
                )

                val response = superuserClient.get("/api/v1/organizations/$organizationId/secrets?sort=-name&limit=1")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi())
                }
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}/secrets")
            }
        }
    }

    "GET /organizations/{organizationId}/secrets/{secretId}" should {
        "return a single secret" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val path = "https://secret-storage.com/ssh_host_rsa_key_5"
                val name = "New secret 5"
                val description = "description"

                secretRepository.create(path, name, description, organizationId, null, null)

                val response = superuserClient.get("/api/v1/organizations/$organizationId/secrets/$name")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, description)
                }
            }
        }

        "respond with NotFound if no secret exists" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val response = superuserClient.get("/api/v1/organizations/$organizationId/secrets/999999")

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrg = createOrganization()

            val path = "https://secret-storage.com/ssh_host_rsa_key_5"
            val name = "New secret 5"
            val description = "description"

            secretRepository.create(path, name, description, createdOrg.id, null, null)

            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}/secrets/$name")
            }
        }
    }

    "POST /organizations/{organizationId}/secrets" should {
        "create a secret in the database" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val name = "New secret 6"

                val secret = CreateSecret(
                    name,
                    secretValue,
                    "The new org secret"
                )

                val response = superuserClient.post("/api/v1/organizations/$organizationId/secrets") {
                    setBody(secret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Secret>() shouldBe Secret(
                        secret.name,
                        secret.description
                    )
                }

                secretRepository.getByOrganizationIdAndName(organizationId, name)?.mapToApi().shouldBe(
                    Secret(secret.name, secret.description)
                )

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("organization_${organizationId}_$name"))?.value shouldBe secretValue
            }
        }

        "respond with CONFLICT if the secret already exists" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val name = "New secret 7"
                val description = "description"

                val secret1 = CreateSecret(name, secretValue, description)
                val secret2 = secret1.copy(value = "someOtherValue")

                val response1 = superuserClient.post("/api/v1/organizations/$organizationId/secrets") {
                    setBody(secret1)
                }

                with(response1) {
                    status shouldBe HttpStatusCode.Created
                }

                val response2 = superuserClient.post("/api/v1/organizations/$organizationId/secrets") {
                    setBody(secret2)
                }

                with(response2) {
                    status shouldBe HttpStatusCode.Conflict
                }

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("organization_${organizationId}_$name"))?.value shouldBe secretValue
            }
        }

        "require OrganizationPermission.WRITE_SECRETS" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(
                OrganizationPermission.WRITE_SECRETS.roleName(createdOrg.id),
                HttpStatusCode.Created
            ) {
                val createSecret = CreateSecret("name", secretValue, "description")
                post("/api/v1/organizations/${createdOrg.id}/secrets") { setBody(createSecret) }
            }
        }
    }

    "PATCH /organizations/{organizationId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val updatedDescription = "updated description"
                val name = "name"
                val path = "path"

                secretRepository.create(path, name, "description", organizationId, null, null)

                val updateSecret = UpdateSecret(name.asPresent(), description = updatedDescription.asPresent())
                val response = superuserClient.patch("/api/v1/organizations/$organizationId/secrets/$name") {
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, updatedDescription)
                }

                secretRepository.getByOrganizationIdAndName(
                    organizationId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(name, updatedDescription)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path)) should beNull()
            }
        }

        "update a secret's value" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val name = "name"
                val desc = "description"
                val path = "path"

                secretRepository.create(path, name, desc, organizationId, null, null)

                val updateSecret = UpdateSecret(name.asPresent(), secretValue.asPresent())
                val response = superuserClient.patch("/api/v1/organizations/$organizationId/secrets/$name") {
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, desc)
                }

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path))?.value shouldBe secretValue
            }
        }

        "handle failures of the SecretStorage" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val name = "name"
                val desc = "description"

                secretRepository.create(secretErrorPath, name, desc, organizationId, null, null)

                val updateSecret = UpdateSecret(name.asPresent(), secretValue.asPresent(), "newDesc".asPresent())
                val response = superuserClient.patch("/api/v1/organizations/$organizationId/secrets/$name") {
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByOrganizationIdAndName(
                    organizationId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }

        "require OrganizationPermission.WRITE_SECRETS" {
            val createdOrg = createOrganization()

            val name = "name"
            val desc = "description"
            val path = "path"

            secretRepository.create(path, name, desc, createdOrg.id, null, null)

            requestShouldRequireRole(OrganizationPermission.WRITE_SECRETS.roleName(createdOrg.id)) {
                val updateSecret =
                    UpdateSecret(name.asPresent(), secretValue.asPresent(), "new description".asPresent())
                patch("/api/v1/organizations/${createdOrg.id}/secrets/$name") { setBody(updateSecret) }
            }
        }
    }

    "DELETE /organizations/{organizationId}/secrets/{secretName}" should {
        "delete a secret" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val path = SecretsProviderFactoryForTesting.TOKEN_PATH
                val name = "New secret 8"
                secretRepository.create(path.path, name, "description", organizationId, null, null)

                val response = superuserClient.delete("/api/v1/organizations/$organizationId/secrets/$name")

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                secretRepository.listForOrganization(organizationId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(path) should beNull()
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val name = "New secret 8"
                val desc = "description"
                secretRepository.create(secretErrorPath, name, desc, organizationId, null, null)

                val response = superuserClient.delete("/api/v1/organizations/$organizationId/secrets/$name")

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByOrganizationIdAndName(
                    organizationId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }

        "require OrganizationPermission.WRITE_SECRETS" {
            val createdOrg = createOrganization()

            val path = SecretsProviderFactoryForTesting.TOKEN_PATH
            val name = "New secret 8"
            secretRepository.create(path.path, name, "description", createdOrg.id, null, null)

            requestShouldRequireRole(
                OrganizationPermission.WRITE_SECRETS.roleName(createdOrg.id),
                HttpStatusCode.NoContent
            ) {
                delete("/api/v1/organizations/${createdOrg.id}/secrets/$name")
            }
        }
    }

    "GET /organizations/{orgId}/infrastructure-services" should {
        "list existing infrastructure services" {
            integrationTestApplication {
                val orgId = createOrganization().id

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

                val response = superuserClient.get("/api/v1/organizations/$orgId/infrastructure-services")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<ApiInfrastructureService>>() shouldContainExactlyInAnyOrder apiServices
                }
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val orgId = createOrganization().id

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

                val response =
                    superuserClient.get("/api/v1/organizations/$orgId/infrastructure-services?sort=name&limit=4")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<ApiInfrastructureService>>() shouldContainExactlyInAnyOrder apiServices
                }
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}/infrastructure-services")
            }
        }
    }

    "POST /organizations/{orgId}/infrastructure-services" should {
        "create an infrastructure service" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val userSecret = secretRepository.create("p1", "s1", null, orgId, null, null)
                val passSecret = secretRepository.create("p2", "s2", null, orgId, null, null)

                val createInfrastructureService = CreateInfrastructureService(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test description",
                    userSecret.name,
                    passSecret.name
                )
                val response = superuserClient.post("/api/v1/organizations/$orgId/infrastructure-services") {
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

        "handle an invalid secret reference" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val createInfrastructureService = CreateInfrastructureService(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test description",
                    "nonExistingSecret1",
                    "nonExistingSecret2"
                )
                val response = superuserClient.post("/api/v1/organizations/$orgId/infrastructure-services") {
                    setBody(createInfrastructureService)
                }

                with(response) {
                    status shouldBe HttpStatusCode.BadRequest
                    val error = body<ErrorResponse>()
                    error.cause shouldContain "nonExistingSecret"
                }
            }
        }

        "require OrganizationPermission.WRITE" {
            val createdOrg = createOrganization()
            val userSecret = secretRepository.create("p1", "s1", null, createdOrg.id, null, null)
            val passSecret = secretRepository.create("p2", "s2", null, createdOrg.id, null, null)

            requestShouldRequireRole(
                OrganizationPermission.WRITE.roleName(createdOrg.id),
                HttpStatusCode.Created
            ) {
                val createInfrastructureService = CreateInfrastructureService(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test description",
                    userSecret.name,
                    passSecret.name
                )

                post("/api/v1/organizations/${createdOrg.id}/infrastructure-services") {
                    setBody(createInfrastructureService)
                }
            }
        }
    }

    "PATCH /organizations/{orgId}/infrastructure-services/{name}" should {
        "update an infrastructure service" {
            integrationTestApplication {
                val orgId = createOrganization().id

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
                val response =
                    superuserClient.patch("/api/v1/organizations/$orgId/infrastructure-services/${service.name}") {
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

        "require OrganizationPermission.WRITE" {
            val createdOrg = createOrganization()
            val userSecret = secretRepository.create("p1", "s1", null, createdOrg.id, null, null)
            val passSecret = secretRepository.create("p2", "s2", null, createdOrg.id, null, null)
            val service = infrastructureServiceRepository.create(
                "testRepository",
                "https://repo.example.org/test",
                "test description",
                userSecret,
                passSecret,
                organizationId = createdOrg.id,
                productId = null
            )

            requestShouldRequireRole(OrganizationPermission.WRITE.roleName(createdOrg.id)) {
                val updateService = UpdateInfrastructureService(
                    description = null.asPresent(),
                    url = "https://repo2.example.org/test2".asPresent()
                )

                patch("/api/v1/organizations/${createdOrg.id}/infrastructure-services/${service.name}") {
                    setBody(updateService)
                }
            }
        }
    }

    "DELETE /organizations/{orgId}/infrastructure-services/{name}" should {
        "delete an infrastructure service" {
            integrationTestApplication {
                val orgId = createOrganization().id

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

                val response =
                    superuserClient.delete("/api/v1/organizations/$orgId/infrastructure-services/${service.name}")

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                val services = infrastructureServiceRepository.listForOrganization(orgId)
                services should beEmpty()
            }
        }

        "require OrganizationPermission.WRITE" {
            val createdOrg = createOrganization()
            val userSecret = secretRepository.create("p1", "s1", null, createdOrg.id, null, null)
            val passSecret = secretRepository.create("p2", "s2", null, createdOrg.id, null, null)
            val service = infrastructureServiceRepository.create(
                "testRepository",
                "https://repo.example.org/test",
                "test description",
                userSecret,
                passSecret,
                organizationId = createdOrg.id,
                productId = null
            )

            requestShouldRequireRole(OrganizationPermission.WRITE.roleName(createdOrg.id), HttpStatusCode.NoContent) {
                delete("/api/v1/organizations/${createdOrg.id}/infrastructure-services/${service.name}")
            }
        }
    }
})
