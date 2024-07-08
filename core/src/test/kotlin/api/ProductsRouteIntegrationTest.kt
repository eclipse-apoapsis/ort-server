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
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
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

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.PagingOptions
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType as ApiRepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.Secret
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.asPresent
import org.eclipse.apoapsis.ortserver.core.shouldHaveBody
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.authorization.ProductPermission
import org.eclipse.apoapsis.ortserver.model.authorization.ProductRole
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryPermission
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryRole
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class ProductsRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    lateinit var organizationService: OrganizationService
    lateinit var productService: ProductService
    lateinit var secretRepository: SecretRepository
    lateinit var infrastructureServiceRepository: InfrastructureServiceRepository

    var orgId = -1L

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

        orgId = organizationService.createOrganization(name = "name", description = "description").id
    }

    val productName = "name"
    val productDescription = "description"

    suspend fun createProduct(
        name: String = productName,
        description: String = productDescription,
        organizationId: Long = orgId
    ) = organizationService.createProduct(name, description, organizationId)

    val secretPath = "path"
    val secretName = "name"
    val secretDescription = "description"

    fun createSecret(
        productId: Long,
        path: String = secretPath,
        name: String = secretName,
        description: String = secretDescription,
    ) = secretRepository.create(path, name, description, null, productId, null)

    "GET /products/{productId}" should {
        "return a single product" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val response = superuserClient.get("/api/v1/products/${createdProduct.id}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Product(createdProduct.id, orgId, productName, productDescription)
            }
        }

        "require ProductPermission.READ" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.READ.roleName(createdProduct.id)) {
                get("/api/v1/products/${createdProduct.id}")
            }
        }
    }

    "PATCH /products/{id}" should {
        "update a product" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val updatedProduct = UpdateProduct(
                    "updatedProduct".asPresent(),
                    "updateDescription".asPresent()
                )
                val response = superuserClient.patch("/api/v1/products/${createdProduct.id}") {
                    setBody(updatedProduct)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Product(
                    createdProduct.id,
                    orgId,
                    updatedProduct.name.valueOrThrow,
                    updatedProduct.description.valueOrThrow
                )
            }
        }

        "require ProductPermission.WRITE" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.WRITE.roleName(createdProduct.id)) {
                val updatedProduct = UpdateProduct("updatedName".asPresent(), "updatedDescription".asPresent())
                patch("/api/v1/products/${createdProduct.id}") { setBody(updatedProduct) }
            }
        }

        "respond with 'Bad Request' if the product's name is invalid" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val updatedProduct = UpdateProduct(
                    " updatedProduct! ".asPresent(),
                    "updateDescription".asPresent()
                )
                val response = superuserClient.patch("/api/v1/products/${createdProduct.id}") {
                    setBody(updatedProduct)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for UpdateProduct"
            }
        }
    }

    "DELETE /products/{id}" should {
        "delete a product" {
            integrationTestApplication {
                val createdProduct = createProduct()

                superuserClient.delete("/api/v1/products/${createdProduct.id}") shouldHaveStatus
                        HttpStatusCode.NoContent

                organizationService.listProductsForOrganization(orgId).data shouldBe emptyList()
            }
        }

        "delete Keycloak roles and groups" {
            integrationTestApplication {
                val createdProduct = createProduct()

                superuserClient.delete("/api/v1/products/${createdProduct.id}")

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    ProductPermission.getRolesForProduct(createdProduct.id) +
                            ProductRole.getRolesForProduct(createdProduct.id)
                )

                keycloakClient.getGroups().map { it.name.value } shouldNot containAnyOf(
                    ProductRole.getGroupsForProduct(createdProduct.id)
                )
            }
        }

        "require ProductPermission.DELETE" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.DELETE.roleName(createdProduct.id), HttpStatusCode.NoContent) {
                delete("/api/v1/products/${createdProduct.id}")
            }
        }
    }

    "GET /products/{id}/repositories" should {
        "return all repositories of an organization" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val type = RepositoryType.GIT
                val url1 = "https://example.com/repo1.git"
                val url2 = "https://example.com/repo2.git"

                val createdRepository1 =
                    productService.createRepository(type = type, url = url1, productId = createdProduct.id)
                val createdRepository2 =
                    productService.createRepository(type = type, url = url2, productId = createdProduct.id)

                val response = superuserClient.get("/api/v1/products/${createdProduct.id}/repositories")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        Repository(createdRepository1.id, orgId, createdProduct.id, type.mapToApi(), url1),
                        Repository(createdRepository2.id, orgId, createdProduct.id, type.mapToApi(), url2)
                    ),
                    PagingOptions(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        sortProperties = listOf(SortProperty("url", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val type = RepositoryType.GIT
                val url1 = "https://example.com/repo1.git"
                val url2 = "https://example.com/repo2.git"

                productService.createRepository(type = type, url = url1, productId = createdProduct.id)
                val createdRepository2 =
                    productService.createRepository(type = type, url = url2, productId = createdProduct.id)

                val response =
                    superuserClient.get("/api/v1/products/${createdProduct.id}/repositories?sort=-url&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(Repository(createdRepository2.id, orgId, createdProduct.id, type.mapToApi(), url2)),
                    PagingOptions(
                        limit = 1,
                        offset = 0,
                        sortProperties = listOf(SortProperty("url", SortDirection.DESCENDING))
                    )
                )
            }
        }

        "require ProductPermission.READ_REPOSITORIES" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.READ_REPOSITORIES.roleName(createdProduct.id)) {
                get("/api/v1/products/${createdProduct.id}/repositories")
            }
        }
    }

    "POST /products/{id}/repositories" should {
        "create a repository" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git")
                val response = superuserClient.post("/api/v1/products/${createdProduct.id}/repositories") {
                    setBody(repository)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Repository(1, orgId, createdProduct.id, repository.type, repository.url)
            }
        }

        "respond with 'Bad Request' if the repository's URL is malformed" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://git hub.com/org/repo.git")
                val response = superuserClient.post("/api/v1/products/${createdProduct.id}/repositories") {
                    setBody(repository)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateRepository"
            }
        }

        "respond with 'Bad Request' if the repository's URL contains userinfo" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://user:password@github.com")
                val response = superuserClient.post("/api/v1/products/${createdProduct.id}/repositories") {
                    setBody(repository)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateRepository"
            }
        }

        "create Keycloak roles and groups" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git")
                val createdRepository = superuserClient.post("/api/v1/products/${createdProduct.id}/repositories") {
                    setBody(repository)
                }.body<Repository>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    RepositoryPermission.getRolesForRepository(createdRepository.id) +
                            RepositoryRole.getRolesForRepository(createdRepository.id)
                )

                keycloakClient.getGroups().map { it.name.value } should containAll(
                    RepositoryRole.getGroupsForRepository(createdRepository.id)
                )
            }
        }

        "require ProductPermission.CREATE_REPOSITORY" {
            val createdProduct = createProduct()
            requestShouldRequireRole(
                ProductPermission.CREATE_REPOSITORY.roleName(createdProduct.id),
                HttpStatusCode.Created
            ) {
                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git")
                post("/api/v1/products/${createdProduct.id}/repositories") { setBody(repository) }
            }
        }
    }

    "GET /products/{productId}/secrets" should {
        "return all secrets for this product" {
            integrationTestApplication {
                val productId = createProduct().id

                val secret1 = createSecret(productId, "path1", "name1", "description1")
                val secret2 = createSecret(productId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/products/$productId/secrets")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(secret1.mapToApi(), secret2.mapToApi()),
                    PagingOptions(
                        limit = 20,
                        offset = 0,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val productId = createProduct().id

                createSecret(productId, "path1", "name1", "description1")
                val secret = createSecret(productId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/products/$productId/secrets?sort=-name&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(secret.mapToApi()),
                    PagingOptions(
                        limit = 1,
                        offset = 0,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING))
                    )
                )
            }
        }

        "require ProductPermission.READ" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.READ.roleName(createdProduct.id)) {
                get("/api/v1/products/${createdProduct.id}/secrets")
            }
        }
    }

    "GET /products/{productId}/secrets/{secretId}" should {
        "return a single secret" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = createSecret(productId)

                val response = superuserClient.get("/api/v1/products/$productId/secrets/${secret.name}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody secret.mapToApi()
            }
        }

        "respond with NotFound if no secret exists" {
            integrationTestApplication {
                val productId = createProduct().id

                superuserClient.get("/api/v1/products/$productId/secrets/999999") shouldHaveStatus
                        HttpStatusCode.NotFound
            }
        }

        "require ProductPermission.READ" {
            val createdProduct = createProduct()
            val secret = createSecret(createdProduct.id)

            requestShouldRequireRole(ProductPermission.READ.roleName(createdProduct.id)) {
                get("/api/v1/products/${createdProduct.id}/secrets/${secret.name}")
            }
        }
    }

    "POST /products/{productId}/secrets" should {
        "create a secret in the database" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = CreateSecret(secretName, secretValue, secretDescription)

                val response = superuserClient.post("/api/v1/products/$productId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Secret(secret.name, secret.description)

                secretRepository.getByProductIdAndName(productId, secret.name)?.mapToApi() shouldBe
                    Secret(secret.name, secret.description)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("product_${productId}_${secret.name}"))?.value shouldBe secretValue
            }
        }

        "respond with CONFLICT if the secret already exists" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = CreateSecret(secretName, secretValue, secretDescription)

                superuserClient.post("/api/v1/products/$productId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Created

                superuserClient.post("/api/v1/products/$productId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Conflict
            }
        }

        "respond with 'Bad Request' if the secret's name is invalid" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = CreateSecret(" New secret 6!", secretValue, secretDescription)

                val response = superuserClient.post("/api/v1/products/$productId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateSecret"

                secretRepository.getByProductIdAndName(productId, secret.name)?.mapToApi().shouldBeNull()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("product_${productId}_${secret.name}"))?.value shouldBe null
            }
        }

        "require ProductPermission.WRITE_SECRETS" {
            val createdProduct = createProduct()
            requestShouldRequireRole(
                ProductPermission.WRITE_SECRETS.roleName(createdProduct.id),
                HttpStatusCode.Created
            ) {
                val createSecret = CreateSecret(secretName, secretValue, secretDescription)
                post("/api/v1/products/${createdProduct.id}/secrets") { setBody(createSecret) }
            }
        }
    }

    "PATCH /products/{productId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = createSecret(productId)

                val updatedDescription = "updated description"
                val updateSecret = UpdateSecret(secret.name.asPresent(), description = updatedDescription.asPresent())

                val response = superuserClient.patch("/api/v1/products/$productId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Secret(secret.name, updatedDescription)

                secretRepository.getByProductIdAndName(productId, updateSecret.name.valueOrThrow)?.mapToApi() shouldBe
                        Secret(secret.name, updatedDescription)
            }
        }

        "update a secret's value" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = createSecret(productId)

                val updateSecret = UpdateSecret(secret.name.asPresent(), secretValue.asPresent())
                val response = superuserClient.patch("/api/v1/products/$productId/secrets/${secret.name}") {
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
                val productId = createProduct().id
                val secret = createSecret(productId, path = secretErrorPath)

                val updateSecret = UpdateSecret(secret.name.asPresent(), secretValue.asPresent(), "newDesc".asPresent())
                superuserClient.patch("/api/v1/products/$productId/secrets/${secret.name}") {
                    setBody(updateSecret)
                } shouldHaveStatus HttpStatusCode.InternalServerError

                secretRepository.getByProductIdAndName(productId, secret.name) shouldBe secret
            }
        }

        "require ProductPermission.WRITE_SECRETS" {
            val createdProduct = createProduct()
            val secret = createSecret(createdProduct.id)

            requestShouldRequireRole(ProductPermission.WRITE_SECRETS.roleName(createdProduct.id)) {
                val updateSecret =
                    UpdateSecret(secret.name.asPresent(), secretValue.asPresent(), "new description".asPresent())
                patch("/api/v1/products/${createdProduct.id}/secrets/${secret.name}") { setBody(updateSecret) }
            }
        }
    }

    "DELETE /products/{productId}/secrets/{secretName}" should {
        "delete a secret" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = createSecret(productId)

                superuserClient.delete("/api/v1/products/$productId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.listForProduct(productId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "respond with Conflict when secret is in use" {
            integrationTestApplication {
                val productId = createProduct().id

                val userSecret = createSecret(productId, path = "user", name = "user")
                val passSecret = createSecret(productId, path = "pass", name = "pass")

                val service = infrastructureServiceRepository.create(
                    name = "testService",
                    url = "http://repo1.example.org/obsolete",
                    description = "good bye, cruel world",
                    usernameSecret = userSecret,
                    passwordSecret = passSecret,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE),
                    organizationId = null,
                    productId = productId
                )

                val response = superuserClient.delete("/api/v1/products/$productId/secrets/${userSecret.name}")
                response shouldHaveStatus HttpStatusCode.Conflict

                val body = response.body<ErrorResponse>()
                body.message shouldBe "The entity you tried to delete is in use."
                body.cause shouldContain service.name
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = createSecret(productId, path = secretErrorPath)

                superuserClient.delete("/api/v1/products/$productId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.InternalServerError

                secretRepository.getByProductIdAndName(productId, secret.name) shouldBe secret
            }
        }

        "require ProductPermission.WRITE_SECRETS" {
            val createdProduct = createProduct()
            val secret = createSecret(createdProduct.id)

            requestShouldRequireRole(
                ProductPermission.WRITE_SECRETS.roleName(createdProduct.id),
                HttpStatusCode.NoContent
            ) {
                delete("/api/v1/products/${createdProduct.id}/secrets/${secret.name}")
            }
        }
    }
})
