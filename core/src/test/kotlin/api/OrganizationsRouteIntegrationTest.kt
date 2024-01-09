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

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
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
import org.ossreviewtoolkit.server.core.shouldHaveBody
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

@Suppress("LargeClass", "MaxLineLength")
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

    val secretPath = "path"
    val secretName = "name"
    val secretDescription = "description"

    fun createSecret(
        organizationId: Long,
        path: String = secretPath,
        name: String = secretName,
        description: String = secretDescription,
    ) = secretRepository.create(path, name, description, organizationId, null, null)

    "GET /organizations" should {
        "return all existing organizations" {
            integrationTestApplication {
                val org1 = createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "name2", description = "description2")

                val response = superuserClient.get("/api/v1/organizations")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody listOf(org1.mapToApi(), org2.mapToApi())
            }
        }

        "support query parameters" {
            integrationTestApplication {
                createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "name2", description = "description2")

                val response = superuserClient.get("/api/v1/organizations?sort=-name&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody listOf(org2.mapToApi())
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

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Organization(createdOrganization.id, organizationName, organizationDescription)
            }
        }

        "respond with NotFound if no organization exists" {
            integrationTestApplication {
                superuserClient.get("/api/v1/organizations/999999") shouldHaveStatus HttpStatusCode.NotFound
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

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Organization(1, org.name, org.description)

                organizationService.getOrganization(1)?.mapToApi().shouldBe(
                    Organization(1, org.name, org.description)
                )
            }
        }

        "respond with a Bad Request if the name is invalid" {
            integrationTestApplication {
                val org = CreateOrganization(name = " org_name!", description = "description")

                val response = superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateOrganization"

                organizationService.getOrganization(1)?.mapToApi().shouldBeNull()
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

                superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                } shouldHaveStatus HttpStatusCode.Conflict
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

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Organization(
                    createdOrg.id,
                    updatedOrganization.name.valueOrThrow,
                    updatedOrganization.description.valueOrThrow
                )

                organizationService.getOrganization(createdOrg.id)?.mapToApi() shouldBe Organization(
                    createdOrg.id,
                    updatedOrganization.name.valueOrThrow,
                    updatedOrganization.description.valueOrThrow
                )
            }
        }

        "respond with a Bad Request if the organization name is invalid" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                val updatedOrganization = UpdateOrganization(
                    " !!updated @382 ".asPresent(),
                    "updated description of testOrg".asPresent()
                )
                val response = superuserClient.patch("/api/v1/organizations/${createdOrg.id}") {
                    setBody(updatedOrganization)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for UpdateOrganization"

                organizationService.getOrganization(createdOrg.id)?.mapToApi() shouldBe Organization(
                    createdOrg.id,
                    createdOrg.name,
                    createdOrg.description
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

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Organization(
                    id = createdOrg.id,
                    name = organizationName,
                    description = null
                )

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

                superuserClient.delete("/api/v1/organizations/${createdOrg.id}") shouldHaveStatus
                        HttpStatusCode.NoContent

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

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Product(1, product.name, product.description)

                productService.getProduct(1)?.mapToApi() shouldBe Product(1, product.name, product.description)
            }
        }

        "respond with a Bad Request if a product name is invalid" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val product = CreateProduct(" product!", "description")
                val response = superuserClient.post("/api/v1/organizations/$orgId/products") {
                    setBody(product)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateProduct"

                productService.getProduct(1)?.mapToApi().shouldBeNull()
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

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody listOf(
                    Product(createdProduct1.id, name1, description),
                    Product(createdProduct2.id, name2, description)
                )
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

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody listOf(Product(createdProduct2.id, name2, description))
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

                val secret1 = createSecret(organizationId, "path1", "name1", "description1")
                val secret2 = createSecret(organizationId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/organizations/$organizationId/secrets")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody listOf(secret1.mapToApi(), secret2.mapToApi())
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                createSecret(organizationId, "path1", "name1", "description1")
                val secret = createSecret(organizationId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/organizations/$organizationId/secrets?sort=-name&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody listOf(secret.mapToApi())
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}/secrets")
            }
        }
    }

    "GET /organizations/{organizationId}/secrets/{secretName}" should {
        "return a single secret" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = createSecret(organizationId)

                val response = superuserClient.get("/api/v1/organizations/$organizationId/secrets/${secret.name}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody secret.mapToApi()
            }
        }

        "respond with NotFound if no secret exists" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                superuserClient.get("/api/v1/organizations/$organizationId/secrets/999999") shouldHaveStatus
                        HttpStatusCode.NotFound
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrg = createOrganization()
            val secret = createSecret(createdOrg.id)

            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}/secrets/${secret.name}")
            }
        }
    }

    "POST /organizations/{organizationId}/secrets" should {
        "create a secret in the database" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = CreateSecret(secretName, secretValue, secretDescription)

                val response = superuserClient.post("/api/v1/organizations/$organizationId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Secret(secret.name, secret.description)

                secretRepository.getByOrganizationIdAndName(organizationId, secret.name)?.mapToApi() shouldBe
                    Secret(secret.name, secret.description)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("organization_${organizationId}_${secret.name}"))?.value shouldBe secretValue
            }
        }

        "respond with CONFLICT if the secret already exists" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = CreateSecret(secretName, secretValue, secretDescription)

                superuserClient.post("/api/v1/organizations/$organizationId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Created

                superuserClient.post("/api/v1/organizations/$organizationId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Conflict
            }
        }

        "respond with Bad Request if the secret's name is invalid" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = CreateSecret(" New secret 6!", secretValue, secretDescription)

                val response = superuserClient.post("/api/v1/organizations/$organizationId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateSecret"

                secretRepository.getByOrganizationIdAndName(organizationId, secret.name)?.mapToApi().shouldBeNull()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("organization_${organizationId}_${secret.name}"))?.value.shouldBeNull()
            }
        }

        "require OrganizationPermission.WRITE_SECRETS" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(
                OrganizationPermission.WRITE_SECRETS.roleName(createdOrg.id),
                HttpStatusCode.Created
            ) {
                val createSecret = CreateSecret(secretName, secretValue, secretDescription)
                post("/api/v1/organizations/${createdOrg.id}/secrets") { setBody(createSecret) }
            }
        }
    }

    "PATCH /organizations/{organizationId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = createSecret(organizationId)

                val updatedDescription = "updated description"
                val updateSecret = UpdateSecret(secret.name.asPresent(), description = updatedDescription.asPresent())

                val response = superuserClient.patch("/api/v1/organizations/$organizationId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Secret(secret.name, updatedDescription)

                secretRepository.getByOrganizationIdAndName(organizationId, updateSecret.name.valueOrThrow)
                    ?.mapToApi() shouldBe Secret(secret.name, updatedDescription)
            }
        }

        "update a secret's value" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = createSecret(organizationId)

                val updateSecret = UpdateSecret(secret.name.asPresent(), secretValue.asPresent())
                val response = superuserClient.patch("/api/v1/organizations/$organizationId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody secret.mapToApi()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path))?.value shouldBe secretValue
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = createSecret(organizationId, path = secretErrorPath)

                val updateSecret = UpdateSecret(secret.name.asPresent(), secretValue.asPresent(), "newDesc".asPresent())
                superuserClient.patch("/api/v1/organizations/$organizationId/secrets/${secret.name}") {
                    setBody(updateSecret)
                } shouldHaveStatus HttpStatusCode.InternalServerError

                secretRepository.getByOrganizationIdAndName(organizationId, secret.name) shouldBe secret
            }
        }

        "require OrganizationPermission.WRITE_SECRETS" {
            val createdOrg = createOrganization()
            val secret = createSecret(createdOrg.id)

            requestShouldRequireRole(OrganizationPermission.WRITE_SECRETS.roleName(createdOrg.id)) {
                val updateSecret =
                    UpdateSecret(secret.name.asPresent(), secretValue.asPresent(), "new description".asPresent())
                patch("/api/v1/organizations/${createdOrg.id}/secrets/${secret.name}") { setBody(updateSecret) }
            }
        }
    }

    "DELETE /organizations/{organizationId}/secrets/{secretName}" should {
        "delete a secret" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = createSecret(organizationId)

                superuserClient.delete("/api/v1/organizations/$organizationId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.listForOrganization(organizationId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = createSecret(organizationId, path = secretErrorPath)

                superuserClient.delete("/api/v1/organizations/$organizationId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.InternalServerError

                secretRepository.getByOrganizationIdAndName(organizationId, secret.name) shouldBe secret
            }
        }

        "require OrganizationPermission.WRITE_SECRETS" {
            val createdOrg = createOrganization()
            val secret = createSecret(createdOrg.id)

            requestShouldRequireRole(
                OrganizationPermission.WRITE_SECRETS.roleName(createdOrg.id),
                HttpStatusCode.NoContent
            ) {
                delete("/api/v1/organizations/${createdOrg.id}/secrets/${secret.name}")
            }
        }
    }

    "GET /organizations/{orgId}/infrastructure-services" should {
        "list existing infrastructure services" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val userSecret = createSecret(orgId, path = "user", name = "user")
                val passSecret = createSecret(orgId, path = "pass", name = "pass")

                val services = (1..8).map { index ->
                    infrastructureServiceRepository.create(
                        "infrastructureService$index",
                        "https://repo.example.org/test$index",
                        "description$index",
                        userSecret,
                        passSecret,
                        index % 2 == 0,
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
                        service.passwordSecret.name,
                        service.excludeFromNetrc
                    )
                }

                val response = superuserClient.get("/api/v1/organizations/$orgId/infrastructure-services")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<ApiInfrastructureService>>() shouldContainExactlyInAnyOrder apiServices
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val userSecret = createSecret(orgId, path = "user", name = "user")
                val passSecret = createSecret(orgId, path = "pass", name = "pass")

                (1..8).shuffled().forEach { index ->
                    infrastructureServiceRepository.create(
                        "infrastructureService$index",
                        "https://repo.example.org/test$index",
                        "description$index",
                        userSecret,
                        passSecret,
                        false,
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

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<ApiInfrastructureService>>() shouldContainExactlyInAnyOrder apiServices
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

                val userSecret = createSecret(orgId, path = "user", name = "user")
                val passSecret = createSecret(orgId, path = "pass", name = "pass")

                val createInfrastructureService = CreateInfrastructureService(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test description",
                    userSecret.name,
                    passSecret.name,
                    excludeFromNetrc = true
                )
                val response = superuserClient.post("/api/v1/organizations/$orgId/infrastructure-services") {
                    setBody(createInfrastructureService)
                }

                val expectedService = ApiInfrastructureService(
                    createInfrastructureService.name,
                    createInfrastructureService.url,
                    createInfrastructureService.description,
                    userSecret.name,
                    passSecret.name,
                    excludeFromNetrc = true
                )

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody expectedService

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

                response shouldHaveStatus HttpStatusCode.BadRequest
                response.body<ErrorResponse>().cause shouldContain "nonExistingSecret"
            }
        }

        "require OrganizationPermission.WRITE" {
            val createdOrg = createOrganization()
            val userSecret = createSecret(createdOrg.id, path = "user", name = "user")
            val passSecret = createSecret(createdOrg.id, path = "pass", name = "pass")

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

        "respond with Bad Request if the name of infrastructure service is invalid" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val userSecret = createSecret(orgId, path = "user", name = "user")
                val passSecret = createSecret(orgId, path = "pass", name = "pass")

                val createInfrastructureService = CreateInfrastructureService(
                    " testRepository 15?!",
                    "https://repo.example.org/test",
                    "test description",
                    userSecret.name,
                    passSecret.name
                )
                val response = superuserClient.post("/api/v1/organizations/$orgId/infrastructure-services") {
                    setBody(createInfrastructureService)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateInfrastructureService"

                infrastructureServiceRepository.getByOrganizationAndName(orgId, createInfrastructureService.name)
                    .shouldBeNull()
            }
        }
    }

    "PATCH /organizations/{orgId}/infrastructure-services/{name}" should {
        "update an infrastructure service" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val userSecret = createSecret(orgId, path = "user", name = "user")
                val passSecret = createSecret(orgId, path = "pass", name = "pass")

                val service = infrastructureServiceRepository.create(
                    "updateService",
                    "http://repo1.example.org/test",
                    "test description",
                    userSecret,
                    passSecret,
                    false,
                    orgId,
                    null
                )

                val newUrl = "https://repo2.example.org/test2"
                val updateService = UpdateInfrastructureService(
                    description = null.asPresent(),
                    url = newUrl.asPresent(),
                    excludeFromNetrc = true.asPresent()
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
                    passSecret.name,
                    true
                )

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody updatedService

                val dbService =
                    infrastructureServiceRepository.getByOrganizationAndName(orgId, service.name)
                dbService.shouldNotBeNull()
                dbService.mapToApi() shouldBe updatedService
            }
        }

        "require OrganizationPermission.WRITE" {
            val createdOrg = createOrganization()
            val userSecret = createSecret(createdOrg.id, path = "user", name = "user")
            val passSecret = createSecret(createdOrg.id, path = "pass", name = "pass")

            val service = infrastructureServiceRepository.create(
                "testRepository",
                "https://repo.example.org/test",
                "test description",
                userSecret,
                passSecret,
                false,
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

                val userSecret = createSecret(orgId, path = "user", name = "user")
                val passSecret = createSecret(orgId, path = "pass", name = "pass")

                val service = infrastructureServiceRepository.create(
                    "deleteService",
                    "http://repo1.example.org/obsolete",
                    "good bye, cruel world",
                    userSecret,
                    passSecret,
                    false,
                    orgId,
                    null
                )

                val response =
                    superuserClient.delete("/api/v1/organizations/$orgId/infrastructure-services/${service.name}")

                response shouldHaveStatus HttpStatusCode.NoContent
                infrastructureServiceRepository.listForOrganization(orgId) should beEmpty()
            }
        }

        "require OrganizationPermission.WRITE" {
            val createdOrg = createOrganization()
            val userSecret = createSecret(createdOrg.id, path = "user", name = "user")
            val passSecret = createSecret(createdOrg.id, path = "pass", name = "pass")

            val service = infrastructureServiceRepository.create(
                "testRepository",
                "https://repo.example.org/test",
                "test description",
                userSecret,
                passSecret,
                false,
                organizationId = createdOrg.id,
                productId = null
            )

            requestShouldRequireRole(OrganizationPermission.WRITE.roleName(createdOrg.id), HttpStatusCode.NoContent) {
                delete("/api/v1/organizations/${createdOrg.id}/infrastructure-services/${service.name}")
            }
        }
    }
})
