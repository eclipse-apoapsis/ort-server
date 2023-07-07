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

import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.CreateRepository
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateProduct
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.ProductRole
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryRole
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.SecretsProviderFactoryForTesting
import org.ossreviewtoolkit.server.services.DefaultAuthorizationService
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.ProductService

class ProductsRouteIntegrationTest : AbstractIntegrationTest({
    lateinit var organizationService: OrganizationService
    lateinit var productService: ProductService
    lateinit var secretRepository: SecretRepository

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

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Product>() shouldBe Product(createdProduct.id, productName, productDescription)
                }
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

        "require ProductPermission.WRITE" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.WRITE.roleName(createdProduct.id)) {
                val updatedProduct = UpdateProduct("updatedName".asPresent(), "updatedDescription".asPresent())
                patch("/api/v1/products/${createdProduct.id}") { setBody(updatedProduct) }
            }
        }
    }

    "DELETE /products/{id}" should {
        "delete a product" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val response = superuserClient.delete("/api/v1/products/${createdProduct.id}")

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                organizationService.listProductsForOrganization(orgId) shouldBe emptyList()
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

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Repository>>() shouldBe listOf(
                        Repository(createdRepository1.id, type.mapToApi(), url1),
                        Repository(createdRepository2.id, type.mapToApi(), url2)
                    )
                }
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

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Repository>>() shouldBe listOf(
                        Repository(createdRepository2.id, type.mapToApi(), url2)
                    )
                }
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

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Repository>() shouldBe Repository(1, repository.type, repository.url)
                }
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

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi(), secret2.mapToApi())
                }
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val productId = createProduct().id

                createSecret(productId, "path1", "name1", "description1")
                val secret = createSecret(productId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/products/$productId/secrets?sort=-name&limit=1")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret.mapToApi())
                }
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

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe secret.mapToApi()
                }
            }
        }

        "respond with NotFound if no secret exists" {
            integrationTestApplication {
                val productId = createProduct().id

                val response = superuserClient.get("/api/v1/products/$productId/secrets/999999")

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
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

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Secret>() shouldBe Secret(secret.name, secret.description)
                }

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

                val response1 = superuserClient.post("/api/v1/products/$productId/secrets") {
                    setBody(secret)
                }

                with(response1) {
                    status shouldBe HttpStatusCode.Created
                }

                val response2 = superuserClient.post("/api/v1/products/$productId/secrets") {
                    setBody(secret)
                }

                with(response2) {
                    status shouldBe HttpStatusCode.Conflict
                }
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

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(secret.name, updatedDescription)
                }

                secretRepository.getByProductIdAndName(
                    orgId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(secret.name, updatedDescription)
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

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe secret.mapToApi()
                }

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path))?.value shouldBe secretValue
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = createSecret(productId, path = secretErrorPath)

                val updateSecret = UpdateSecret(secret.name.asPresent(), secretValue.asPresent(), "newDesc".asPresent())
                val response = superuserClient.patch("/api/v1/products/$productId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByProductIdAndName(orgId, secret.name) shouldBe secret
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

                val response = superuserClient.delete("/api/v1/products/$productId/secrets/${secret.name}")

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                secretRepository.listForProduct(productId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication {
                val productId = createProduct().id
                val secret = createSecret(productId, path = secretErrorPath)

                val response = superuserClient.delete("/api/v1/products/$productId/secrets/${secret.name}")

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

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
