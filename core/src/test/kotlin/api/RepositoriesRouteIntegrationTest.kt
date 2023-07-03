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

import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.OrtRun
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateRepository
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
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryRole
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
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

class RepositoriesRouteIntegrationTest : WordSpec({
    val dbExtension: DatabaseTestExtension = extension(DatabaseTestExtension())
    val keycloak = install(KeycloakTestExtension(createRealmPerTest = true))
    val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    val keycloakClient = keycloak.createKeycloakClientForTestRealm()
    val labelsMap = mapOf("label1" to "label1", "label2" to "label2")

    val secretsConfig = mapOf(
        "${SecretStorage.CONFIG_PREFIX}.${SecretStorage.NAME_PROPERTY}" to SecretsProviderFactoryForTesting.NAME,
        "${SecretStorage.CONFIG_PREFIX}.${SecretsProviderFactoryForTesting.ERROR_PATH_PROPERTY}" to ERROR_PATH
    )

    lateinit var productService: ProductService
    lateinit var ortRunRepository: OrtRunRepository
    lateinit var secretRepository: SecretRepository

    var productId = -1L

    beforeEach {
        val authorizationService = DefaultAuthorizationService(
            keycloakClient,
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository
        )

        val organizationService = OrganizationService(
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

        ortRunRepository = dbExtension.fixtures.ortRunRepository
        secretRepository = dbExtension.fixtures.secretRepository

        val orgId = organizationService.createOrganization(name = "name", description = "description").id
        productId =
            organizationService.createProduct(name = "name", description = "description", organizationId = orgId).id
    }

    "GET /repositories/{repositoryId}" should {
        "return a single repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val type = RepositoryType.GIT
                val url = "https://example.com/repo.git"

                val createdRepository = productService.createRepository(type = type, url = url, productId = productId)

                val response = client.get("/api/v1/repositories/${createdRepository.id}") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Repository>() shouldBe Repository(createdRepository.id, type.mapToApi(), url)
                }
            }
        }
    }

    "PATCH /repositories/{repositoryId}" should {
        "update a repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdRepository = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                val updateRepository = UpdateRepository(
                    ApiRepositoryType.SUBVERSION.asPresent(),
                    "https://svn.example.com/repos/org/repo/trunk".asPresent()
                )

                val response = client.patch("/api/v1/repositories/${createdRepository.id}") {
                    headers { basicTestAuth() }
                    setBody(updateRepository)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Repository>() shouldBe Repository(
                        createdRepository.id,
                        (updateRepository.type as OptionalValue.Present).value,
                        (updateRepository.url as OptionalValue.Present).value
                    )
                }
            }
        }
    }

    "DELETE /repositories/{repositoryId}" should {
        "delete a repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdRepository = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                val response = client.delete("/api/v1/repositories/${createdRepository.id}") {
                    headers { basicTestAuth() }
                }

                response.status shouldBe HttpStatusCode.NoContent
                productService.listRepositoriesForProduct(productId) shouldBe emptyList()
            }
        }

        "delete Keycloak roles and groups" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdRepository = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                client.delete("/api/v1/repositories/${createdRepository.id}") {
                    headers { basicTestAuth() }
                }

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    RepositoryPermission.getRolesForRepository(createdRepository.id) +
                            RepositoryRole.getRolesForRepository(createdRepository.id)
                )

                keycloakClient.getGroups().map { it.name.value } shouldNot containAnyOf(
                    RepositoryRole.getGroupsForRepository(createdRepository.id)
                )
            }
        }
    }

    "GET /repositories/{repositoryId}/runs" should {
        "return the ORT runs on a repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val createdRepository = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                val run1 = ortRunRepository.create(createdRepository.id, "branch-1", JobConfigurations(), labelsMap)
                val run2 = ortRunRepository.create(createdRepository.id, "branch-2", JobConfigurations(), labelsMap)

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/${createdRepository.id}/runs") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<OrtRun>>() shouldBe listOf(run1.mapToApi(), run2.mapToApi())
                }
            }
        }

        "support query parameters" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val createdRepository = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                ortRunRepository.create(createdRepository.id, "branch-1", JobConfigurations(), labelsMap)
                val run2 = ortRunRepository.create(createdRepository.id, "branch-2", JobConfigurations(), labelsMap)

                val client = createJsonClient()

                val query = "?sort=-revision,-createdAt&limit=1"
                val response = client.get("/api/v1/repositories/${createdRepository.id}/runs$query") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<OrtRun>>() shouldBe listOf(run2.mapToApi())
                }
            }
        }
    }

    "GET /repositories/{repositoryId}/secrets" should {
        "return all secrets for this repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_1",
                    "New secret 1",
                    "The new repo secret",
                    null,
                    null,
                    repositoryId
                )
                val secret2 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_2",
                    "New secret 2",
                    "The new repo secret",
                    null,
                    null,
                    repositoryId
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/$repositoryId/secrets") {
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
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_3",
                    "New secret 3",
                    "The new repo secret",
                    null,
                    null,
                    repositoryId
                )
                val secret1 = secretRepository.create(
                    "https://secret-storage.com/ssh_host_rsa_key_4",
                    "New secret 4",
                    "The new repo secret",
                    null,
                    null,
                    repositoryId
                )

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/$repositoryId/secrets?sort=-name&limit=1") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi())
                }
            }
        }
    }

    "GET /repositories/{repositoryId}/secrets/{secretId}" should {
        "return a single secret" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val path = "https://secret-storage.com/ssh_host_rsa_key_5"
                val name = "New secret 5"
                val description = "description"

                secretRepository.create(path, name, description, null, null, repositoryId)

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/$repositoryId/secrets/$name") {
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
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/$repositoryId/secrets/999999") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }

    "POST /repositories/{repositoryId}/secrets" should {
        "create a secret in the database" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val client = createJsonClient()

                val name = "New secret 6"

                val secret = CreateSecret(
                    name,
                    SECRET,
                    "The new repo secret"
                )

                val response = client.post("/api/v1/repositories/$repositoryId/secrets") {
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

                secretRepository.getByRepositoryIdAndName(repositoryId, name)?.mapToApi().shouldBe(
                    Secret(secret.name, secret.description)
                )

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("repository_${repositoryId}_$name"))?.value shouldBe SECRET
            }
        }

        "respond with CONFLICT if the secret already exists" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val name = "New secret 7"
                val description = "description"

                val secret = CreateSecret(name, SECRET, description)

                val client = createJsonClient()

                val response1 = client.post("/api/v1/repositories/$repositoryId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response1) {
                    status shouldBe HttpStatusCode.Created
                }

                val response2 = client.post("/api/v1/repositories/$repositoryId/secrets") {
                    headers { basicTestAuth() }
                    setBody(secret)
                }

                with(response2) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }
    }

    "PATCH /repositories/{repositoryId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val updatedDescription = "updated description"
                val name = "name"
                val path = "path"

                secretRepository.create(path, name, "description", null, null, repositoryId)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), description = updatedDescription.asPresent())
                val response = client.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, updatedDescription)
                }

                secretRepository.getByRepositoryIdAndName(
                    repositoryId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(name, updatedDescription)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(path)) should beNull()
            }
        }

        "handle a failure from the SecretsStorage" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val name = "name"
                val desc = "description"

                secretRepository.create(ERROR_PATH, name, desc, null, null, repositoryId)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), "newVal".asPresent(), "newDesc".asPresent())
                val response = client.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                    setBody(updateSecret)
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByRepositoryIdAndName(
                    repositoryId,
                    (updateSecret.name as OptionalValue.Present).value
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }

        "update a secret's value" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val name = "name"
                val path = "path"
                val desc = "some description"

                secretRepository.create(path, name, desc, null, null, repositoryId)

                val client = createJsonClient()

                val updateSecret = UpdateSecret(name.asPresent(), SECRET.asPresent())
                val response = client.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
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
    }

    "DELETE /repositories/{repositoryId}/secrets/{secretName}" should {
        "delete a secret" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val path = SecretsProviderFactoryForTesting.SERVICE_PATH
                val name = "New secret 8"
                secretRepository.create(path.path, name, "description", null, null, repositoryId)

                val client = createJsonClient()

                val response = client.delete("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                secretRepository.listForRepository(repositoryId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(path) should beNull()
            }
        }

        "handle failures from the SecretStorage" {
            ortServerTestApplication(dbExtension.db, noDbConfig, secretsConfig) {
                val repositoryId = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                ).id

                val name = "New secret 8"
                val desc = "description"
                secretRepository.create(ERROR_PATH, name, desc, null, null, repositoryId)

                val client = createJsonClient()

                val response = client.delete("/api/v1/repositories/$repositoryId/secrets/$name") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByRepositoryIdAndName(
                    repositoryId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }
    }
})
