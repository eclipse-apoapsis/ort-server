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

package org.eclipse.apoapsis.ortserver.core.api

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
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
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.CredentialsType as ApiCredentialsType
import org.eclipse.apoapsis.ortserver.api.v1.model.InfrastructureService as ApiInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.OptionalValue
import org.eclipse.apoapsis.ortserver.api.v1.model.Organization
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.PagingData
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.Secret
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.asPresent
import org.eclipse.apoapsis.ortserver.api.v1.model.valueOrThrow
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.core.TEST_USER
import org.eclipse.apoapsis.ortserver.core.addUserRole
import org.eclipse.apoapsis.ortserver.core.shouldHaveBody
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.authorization.OrganizationPermission
import org.eclipse.apoapsis.ortserver.model.authorization.OrganizationRole
import org.eclipse.apoapsis.ortserver.model.authorization.OrganizationRole.ADMIN
import org.eclipse.apoapsis.ortserver.model.authorization.OrganizationRole.READER
import org.eclipse.apoapsis.ortserver.model.authorization.OrganizationRole.WRITER
import org.eclipse.apoapsis.ortserver.model.authorization.ProductPermission
import org.eclipse.apoapsis.ortserver.model.authorization.ProductRole
import org.eclipse.apoapsis.ortserver.model.authorization.Superuser
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.utils.test.Integration

@Suppress("LargeClass")
class OrganizationsRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

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

    suspend fun addUserToGroup(username: String, organizationId: Long, groupId: String) =
        organizationService.addUserToGroup(username, organizationId, groupId)

    "GET /organizations" should {
        "return all existing organizations for the superuser" {
            integrationTestApplication {
                val org1 = createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "name2", description = "description2")

                val response = superuserClient.get("/api/v1/organizations")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org1.mapToApi(), org2.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return only organizations for which the user has OrganizationPermission.READ" {
            integrationTestApplication {
                createOrganization(name = "org1")
                val org2 = createOrganization(name = "org2")
                createOrganization(name = "org3")
                val org4 = createOrganization(name = "org4")
                createOrganization(name = "org5")

                keycloak.keycloakAdminClient.addUserRole(
                    TEST_USER.username.value,
                    OrganizationPermission.READ.roleName(org2.id)
                )
                keycloak.keycloakAdminClient.addUserRole(
                    TEST_USER.username.value,
                    OrganizationPermission.READ.roleName(org4.id)
                )

                val response = testUserClient.get("/api/v1/organizations")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org2.mapToApi(), org4.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING)),
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "name2", description = "description2")

                val response = superuserClient.get("/api/v1/organizations?sort=-name&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org2.mapToApi()),
                    PagingData(
                        limit = 1,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING)),
                    )
                )
            }
        }

        "require authentication" {
            requestShouldRequireAuthentication {
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

        "respond with 'Bad Request' if the organization's name is invalid" {
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

        "respond with 'Bad Request' if the request body is invalid" {
            integrationTestApplication {
                val invalidJson = """
                    {
                      "name": "Example Organization",
                      "description": This description is missing double quotes.,
                    }
                """.trimIndent()

                val response = superuserClient.post("/api/v1/organizations") {
                    setBody(invalidJson)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Invalid request body."
                body.cause.shouldContain(
                    "Illegal input: Unexpected JSON token at offset 53: Expected quotation mark '\"'"
                )

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

        "respond with 'Bad Request' if the organization's name is invalid" {
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
                response shouldHaveBody Product(1, orgId, product.name, product.description)

                productService.getProduct(1)?.mapToApi() shouldBe Product(1, orgId, product.name, product.description)
            }
        }

        "respond with 'Bad Request' if the product's name is invalid" {
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
                response shouldHaveBody PagedResponse(
                    listOf(
                        Product(createdProduct1.id, orgId, name1, description),
                        Product(createdProduct2.id, orgId, name2, description)
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                    )
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
                response shouldHaveBody PagedResponse(
                    listOf(Product(createdProduct2.id, orgId, name2, description)),
                    PagingData(
                        limit = 1,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING))
                    )
                )
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
                response shouldHaveBody PagedResponse(
                    listOf(secret1.mapToApi(), secret2.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                createSecret(organizationId, "path1", "name1", "description1")
                val secret = createSecret(organizationId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/organizations/$organizationId/secrets?sort=-name&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(secret.mapToApi()),
                    PagingData(
                        limit = 1,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING))
                    )
                )
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

        "respond with 'Bad Request' if the secret's name is invalid" {
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
                val updateSecret = UpdateSecret(secretValue.asPresent(), description = updatedDescription.asPresent())

                val response = superuserClient.patch("/api/v1/organizations/$organizationId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Secret(secret.name, updatedDescription)

                secretRepository.getByOrganizationIdAndName(organizationId, secret.name)
                    ?.mapToApi() shouldBe Secret(secret.name, updatedDescription)
            }
        }

        "update a secret's value" {
            integrationTestApplication {
                val organizationId = createOrganization().id
                val secret = createSecret(organizationId)

                val updateSecret = UpdateSecret(secretValue.asPresent(), secretDescription.asPresent())
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

                val updateSecret = UpdateSecret(secretValue.asPresent(), "newDesc".asPresent())
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
                    UpdateSecret(secretValue.asPresent(), "new description".asPresent())
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

                secretRepository.listForOrganization(organizationId).data shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "respond with Conflict when secret is in use" {
            integrationTestApplication {
                val organizationId = createOrganization().id

                val userSecret = createSecret(organizationId, path = "user", name = "user")
                val passSecret = createSecret(organizationId, path = "pass", name = "pass")

                val service = infrastructureServiceRepository.create(
                    name = "testService",
                    url = "http://repo1.example.org/obsolete",
                    description = "good bye, cruel world",
                    usernameSecret = userSecret,
                    passwordSecret = passSecret,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE),
                    organizationId = organizationId,
                    productId = null
                )

                val response =
                    superuserClient.delete("/api/v1/organizations/$organizationId/secrets/${userSecret.name}")
                response shouldHaveStatus HttpStatusCode.Conflict

                val body = response.body<ErrorResponse>()
                body.message shouldBe "The entity you tried to delete is in use."
                body.cause shouldContain service.name
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
                        if (index % 2 == 0) {
                            EnumSet.of(CredentialsType.NETRC_FILE, CredentialsType.GIT_CREDENTIALS_FILE)
                        } else {
                            emptySet()
                        },
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
                        if (service.credentialsTypes.isEmpty()) {
                            emptySet()
                        } else {
                            EnumSet.of(ApiCredentialsType.NETRC_FILE, ApiCredentialsType.GIT_CREDENTIALS_FILE)
                        }
                    )
                }

                val response = superuserClient.get("/api/v1/organizations/$orgId/infrastructure-services")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<PagedResponse<ApiInfrastructureService>>().data shouldContainExactlyInAnyOrder
                        apiServices
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
                        EnumSet.of(CredentialsType.NETRC_FILE),
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
                        passSecret.name,
                        EnumSet.of(ApiCredentialsType.NETRC_FILE)
                    )
                }

                val response =
                    superuserClient.get("/api/v1/organizations/$orgId/infrastructure-services?sort=name&limit=4")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<PagedResponse<ApiInfrastructureService>>().data shouldContainExactlyInAnyOrder
                        apiServices
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
                    credentialsTypes = emptySet()
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
                    emptySet()
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

        "respond with 'Bad Request' if the infrastructure service's name is invalid" {
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
                    emptySet(),
                    orgId,
                    null
                )

                val newUrl = "https://repo2.example.org/test2"
                val updateService = UpdateInfrastructureService(
                    description = null.asPresent(),
                    url = newUrl.asPresent(),
                    credentialsTypes = EnumSet.of(
                        ApiCredentialsType.NETRC_FILE,
                        ApiCredentialsType.GIT_CREDENTIALS_FILE
                    ).asPresent()
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
                    EnumSet.of(ApiCredentialsType.NETRC_FILE, ApiCredentialsType.GIT_CREDENTIALS_FILE)
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
                emptySet(),
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
                    emptySet(),
                    orgId,
                    null
                )

                val response =
                    superuserClient.delete("/api/v1/organizations/$orgId/infrastructure-services/${service.name}")

                response shouldHaveStatus HttpStatusCode.NoContent
                infrastructureServiceRepository.listForOrganization(orgId).data should beEmpty()
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
                emptySet(),
                organizationId = createdOrg.id,
                productId = null
            )

            requestShouldRequireRole(OrganizationPermission.WRITE.roleName(createdOrg.id), HttpStatusCode.NoContent) {
                delete("/api/v1/organizations/${createdOrg.id}/infrastructure-services/${service.name}")
            }
        }
    }

    "PUT/DELETE /organizations/{orgId}/groups/{groupId}" should {
        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "require OrganizationPermission.WRITE for method '${method.value}'" {
                val createdOrg = createOrganization()
                val user = Username(TEST_USER.username.value)

                requestShouldRequireRole(
                    OrganizationPermission.WRITE.roleName(createdOrg.id),
                    HttpStatusCode.NoContent
                ) {
                    when (method) {
                        HttpMethod.Put -> put("/api/v1/organizations/${createdOrg.id}/groups/readers") {
                            setBody(user)
                        }
                        HttpMethod.Delete -> delete("/api/v1/organizations/${createdOrg.id}/groups/readers") {
                            setBody(user)
                        }
                        else -> error("Unsupported method: $method")
                    }
                }
            }
        }

        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "respond with 'NotFound' if the user does not exist for method '${method.value}'" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val user = Username("non-existing-username")

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/organizations/${createdOrg.id}/groups/readers"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/organizations/${createdOrg.id}/groups/readers"
                        ) {
                            setBody(user)
                        }
                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.InternalServerError

                    val body = response.body<ErrorResponse>()
                    body.cause shouldContain "Could not find user"
                }
            }
        }

        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "respond with 'NotFound' if the organization does not exist for method '${method.value}'" {
                integrationTestApplication {
                    val user = Username(TEST_USER.username.value)

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/organizations/999999/groups/readers"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/organizations/999999/groups/readers"
                        ) {
                            setBody(user)
                        }
                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.NotFound

                    val body = response.body<ErrorResponse>()
                    body.message shouldBe "Resource not found."
                }
            }
        }

        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "respond with 'BadRequest' if the request body is invalid for method '${method.value}'" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val org = CreateOrganization(name = "name", description = "description") // Wrong request body

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/organizations/${createdOrg.id}/groups/readers"
                        ) {
                            setBody(org)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/organizations/${createdOrg.id}/groups/readers"
                        ) {
                            setBody(org)
                        }
                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.BadRequest
                }
            }
        }

        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "respond with 'NotFound' if the group does not exist for method '${method.value}'" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val user = Username(TEST_USER.username.value)

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/organizations/${createdOrg.id}/groups/non-existing-group"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/organizations/${createdOrg.id}/groups/non-existing-group"
                        ) {
                            setBody(user)
                        }
                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.NotFound

                    val body = response.body<ErrorResponse>()
                    body.message shouldBe "Resource not found."
                }
            }
        }
    }

    "PUT /organizations/{orgId}/groups/{groupId}" should {
        forAll(
            row("readers"),
            row("writers"),
            row("admins")
        ) { groupId ->
            "add a user to the '$groupId' group" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val user = Username(TEST_USER.username.value)

                    val response = superuserClient.put(
                        "/api/v1/organizations/${createdOrg.id}/groups/$groupId"
                    ) {
                        setBody(user)
                    }

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val groupName = when (groupId) {
                        "readers" -> READER.groupName(createdOrg.id)
                        "writers" -> WRITER.groupName(createdOrg.id)
                        "admins" -> ADMIN.groupName(createdOrg.id)
                        else -> error("Unknown group: $groupId")
                    }
                    val group = keycloakClient.getGroup(GroupName(groupName))
                    group.shouldNotBeNull()

                    val members = keycloakClient.getGroupMembers(group.name)
                    members shouldHaveSize 1
                    members.map { it.username } shouldContain TEST_USER.username
                }
            }
        }
    }

    "DELETE /organizations/{orgId}/groups/{groupId}" should {
        forAll(
            row("readers"),
            row("writers"),
            row("admins")
        ) { groupId ->
            "remove a user from the '$groupId' group" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val user = Username(TEST_USER.username.value)
                    addUserToGroup(user.username, createdOrg.id, groupId)

                    // Check pre-condition
                    val groupName = when (groupId) {
                        "readers" -> READER.groupName(createdOrg.id)
                        "writers" -> WRITER.groupName(createdOrg.id)
                        "admins" -> ADMIN.groupName(createdOrg.id)
                        else -> error("Unknown group: $groupId")
                    }
                    val groupBefore = keycloakClient.getGroup(GroupName(groupName))
                    val membersBefore = keycloakClient.getGroupMembers(groupBefore.name)
                    membersBefore shouldHaveSize 1
                    membersBefore.map { it.username } shouldContain TEST_USER.username

                    val response = superuserClient.delete(
                        "/api/v1/organizations/${createdOrg.id}/groups/$groupId"
                    ) {
                        setBody(user)
                    }

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val groupAfter = keycloakClient.getGroup(GroupName(groupName))
                    groupAfter.shouldNotBeNull()

                    val membersAfter = keycloakClient.getGroupMembers(groupAfter.name)
                    membersAfter.shouldBeEmpty()
                }
            }
        }
    }
})
