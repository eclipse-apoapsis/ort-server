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
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.nulls.beNull
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

import org.ossreviewtoolkit.server.api.v1.CreateRepository
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateProduct
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakClientForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.ProductRole
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryRole
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.SecretStorage
import org.ossreviewtoolkit.server.secrets.SecretsProviderFactoryForTesting
import org.ossreviewtoolkit.server.services.DefaultAuthorizationService
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.ProductService

private const val SECRET = "secret-value"
private const val ERROR_PATH = "error-path"

class ProductsRouteIntegrationTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())
    val keycloak = install(KeycloakTestExtension(createRealmPerTest = true))
    val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    val keycloakClient = keycloak.createKeycloakClientForTestRealm()

    val secretsConfig = mapOf(
        "${SecretStorage.CONFIG_PREFIX}.${SecretStorage.NAME_PROPERTY}" to SecretsProviderFactoryForTesting.NAME,
        "${SecretStorage.CONFIG_PREFIX}.${SecretsProviderFactoryForTesting.ERROR_PATH_PROPERTY}" to ERROR_PATH
    )

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

        secretRepository = dbExtension.fixtures.secretRepository

        orgId = organizationService.createOrganization(name = "name", description = "description").id
    }

    "GET /products/{productId}" should {
        "return a single product" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val name = "name"
                val description = "description"

                val createdProduct =
                    organizationService.createProduct(name = name, description = description, organizationId = orgId)

                val response = client.get("/api/v1/products/${createdProduct.id}") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Product>() shouldBe Product(createdProduct.id, name, description)
                }
            }
        }
    }

    "PATCH /products/{id}" should {
        "update a product" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdProduct = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                )

                val updatedProduct = UpdateProduct(
                    "updatedProduct".asPresent(),
                    "updateDescription".asPresent()
                )
                val response = client.patch("/api/v1/products/${createdProduct.id}") {
                    headers { basicTestAuth() }
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
    }

    "DELETE /products/{id}" should {
        "delete a product" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdProduct = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                )

                val response = client.delete("/api/v1/products/${createdProduct.id}") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                organizationService.listProductsForOrganization(orgId) shouldBe emptyList()
            }
        }

        "delete Keycloak roles and groups" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdProduct = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                )

                client.delete("/api/v1/products/${createdProduct.id}") {
                    headers { basicTestAuth() }
                }

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    ProductPermission.getRolesForProduct(createdProduct.id) +
                            ProductRole.getRolesForProduct(createdProduct.id)
                )

                keycloakClient.getGroups().map { it.name.value } shouldNot containAnyOf(
                    ProductRole.getGroupsForProduct(createdProduct.id)
                )
            }
        }
    }

    "GET /products/{id}/repositories" should {
        "return all repositories of an organization" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdProduct = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                )

                val type = RepositoryType.GIT
                val url1 = "https://example.com/repo1.git"
                val url2 = "https://example.com/repo2.git"

                val createdRepository1 =
                    productService.createRepository(type = type, url = url1, productId = createdProduct.id)
                val createdRepository2 =
                    productService.createRepository(type = type, url = url2, productId = createdProduct.id)

                val response = client.get("/api/v1/products/${createdProduct.id}/repositories") {
                    headers { basicTestAuth() }
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

        "support query parameters" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdProduct = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                )

                val type = RepositoryType.GIT
                val url1 = "https://example.com/repo1.git"
                val url2 = "https://example.com/repo2.git"

                productService.createRepository(type = type, url = url1, productId = createdProduct.id)
                val createdRepository2 =
                    productService.createRepository(type = type, url = url2, productId = createdProduct.id)

                val response = client.get("/api/v1/products/${createdProduct.id}/repositories?sort=-url&limit=1") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Repository>>() shouldBe listOf(
                        Repository(createdRepository2.id, type.mapToApi(), url2)
                    )
                }
            }
        }
    }

    "POST /products/{id}/repositories" should {
        "create a repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdProduct = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                )

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git")
                val response = client.post("/api/v1/products/${createdProduct.id}/repositories") {
                    headers { basicTestAuth() }
                    setBody(repository)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Repository>() shouldBe Repository(1, repository.type, repository.url)
                }
            }
        }

        "create Keycloak roles and groups" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdProduct = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                )

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git")
                val createdRepository = client.post("/api/v1/products/${createdProduct.id}/repositories") {
                    headers { basicTestAuth() }
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
    }

    "GET /products/{productId}/secrets" should {
        "return all secrets for this product" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_1",
                    "New secret 1",
                    "The new prod secret",
                    null,
                    productId,
                    null
                )
                val secret2 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_2",
                    "New secret 2",
                    "The new prod secret",
                    null,
                    productId,
                    null
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/products/$productId/secrets") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi(), secret2.mapToApi())
                }
            }
        }

        "support query parameters" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_3",
                    "New secret 3",
                    "The new prod secret",
                    null,
                    productId,
                    null
                )
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_4",
                    "New secret 4",
                    "The new prod secret",
                    null,
                    productId,
                    null
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/products/$productId/secrets?sort=-name&limit=1") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi())
                }
            }
        }
    }

    "GET /products/{productId}/secrets/{secretId}" should {
        "return a single secret" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val path = "https://secret-storage.com/ssh_host_rsa_key_5"
                val name = "New secret 5"
                val description = "description"

                secretRepository.create(path, name, description, null, productId, null)

                val client = createJsonClient()

                val response = client.get("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, description)
                }
            }
        }

        "respond with NotFound if no secret exists" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val client = createJsonClient()

                val response = client.get("/api/v1/products/$productId/secrets/999999") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }

    "POST /products/{productId}/secrets" should {
        "create a secret in the database" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val client = createJsonClient()

                val name = "New secret 6"

                val secret = CreateSecret(
                    name,
                    SECRET,
                    "The new prod secret"
                )

                val response = client.post("/api/v1/products/$productId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Secret>() shouldBe Secret(
                        secret.name,
                        secret.description
                    )
                }

                secretRepository.getByProductIdAndName(productId, name)?.mapToApi().shouldBe(
                    Secret(secret.name, secret.description)
                )

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("product_${productId}_$name"))?.value shouldBe SECRET
            }
        }

        "respond with CONFLICT if the secret already exists" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val name = "New secret 7"
                val description = "description"

                val secret = CreateSecret(name, SECRET, description)

                val client = createJsonClient()

                val response1 = client.post("/api/v1/products/$productId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response1) {
                    status shouldBe HttpStatusCode.Created
                }

                val response2 = client.post("/api/v1/products/$productId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response2) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }
    }

    "PATCH /products/{productId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val updatedDescription = "updated description"
                val name = "name"
                val path = "path"

                secretRepository.create(path, name, "description", null, productId, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), description = updatedDescription.asPresent())
                val response = client.patch("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, updatedDescription)
                }

                secretRepository.getByProductIdAndName(
                    orgId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(name, updatedDescription)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path)) should beNull()
            }
        }

        "update a secret's value" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val name = "name"
                val path = "path"
                val desc = "some description"

                secretRepository.create(path, name, desc, null, productId, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), SECRET.asPresent())
                val response = client.patch("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, desc)
                }

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path))?.value shouldBe SECRET
            }
        }

        "handle a failure from the SecretsStorage" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val name = "name"
                val desc = "description"

                secretRepository.create(ERROR_PATH, name, desc, null, productId, null)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), "newVal".asPresent(), "newDesc".asPresent())
                val response = client.patch("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByProductIdAndName(
                    orgId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }
    }

    "DELETE /products/{productId}/secrets/{secretName}" should {
        "delete a secret" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val path = SecretsProviderFactoryForTesting.PASSWORD_PATH
                val name = "New secret 8"
                secretRepository.create(path.path, name, "description", null, productId, null)

                val client = createJsonClient()

                val response = client.delete("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                secretRepository.listForProduct(productId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(path) should beNull()
            }
        }

        "handle a failure from the SecretsStorage" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val productId = organizationService.createProduct(
                    name = "name",
                    description = "description",
                    organizationId = orgId
                ).id

                val name = "New secret 8"
                val desc = "description"
                secretRepository.create(ERROR_PATH, name, desc, null, productId, null)

                val client = createJsonClient()

                val response = client.delete("/api/v1/products/$productId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByProductIdAndName(
                    productId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }
    }
})
