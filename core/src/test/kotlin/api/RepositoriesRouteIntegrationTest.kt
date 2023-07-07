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

import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
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

import org.ossreviewtoolkit.server.api.v1.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.CreateOrtRun
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.EnvironmentConfig
import org.ossreviewtoolkit.server.api.v1.InfrastructureService
import org.ossreviewtoolkit.server.api.v1.JobConfigurations as ApiJobConfigurations
import org.ossreviewtoolkit.server.api.v1.OrtRun
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateRepository
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.model.InfrastructureServiceDeclaration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryRole
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.SecretsProviderFactoryForTesting
import org.ossreviewtoolkit.server.services.DefaultAuthorizationService
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.ProductService
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.testing.MessageSenderFactoryForTesting

class RepositoriesRouteIntegrationTest : AbstractIntegrationTest({
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
            dbExtension.fixtures.repositoryRepository,
            keycloakGroupPrefix = ""
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

    val labelsMap = mapOf("label1" to "label1", "label2" to "label2")

    val repositoryType = RepositoryType.GIT
    val repositoryUrl = "https://example.org/repo.git"

    suspend fun createRepository(
        type: RepositoryType = repositoryType,
        url: String = repositoryUrl,
        prodId: Long = productId
    ) = productService.createRepository(type, url, prodId)

    "GET /repositories/{repositoryId}" should {
        "return a single repository" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Repository>() shouldBe
                            Repository(createdRepository.id, repositoryType.mapToApi(), repositoryUrl)
                }
            }
        }

        "require RepositoryPermission.READ" {
            val createdRepository = createRepository()
            requestShouldRequireRole(RepositoryPermission.READ.roleName(createdRepository.id)) {
                get("/api/v1/repositories/${createdRepository.id}")
            }
        }
    }

    "PATCH /repositories/{repositoryId}" should {
        "update a repository" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val updateRepository = UpdateRepository(
                    ApiRepositoryType.SUBVERSION.asPresent(),
                    "https://svn.example.com/repos/org/repo/trunk".asPresent()
                )

                val response = superuserClient.patch("/api/v1/repositories/${createdRepository.id}") {
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

        "require RepositoryPermission.WRITE" {
            val createdRepository = createRepository()
            requestShouldRequireRole(RepositoryPermission.WRITE.roleName(createdRepository.id)) {
                val updateRepository = UpdateRepository(
                    ApiRepositoryType.SUBVERSION.asPresent(),
                    "https://svn.example.com/repos/org/repo/trunk".asPresent()
                )
                patch("/api/v1/repositories/${createdRepository.id}") { setBody(updateRepository) }
            }
        }
    }

    "DELETE /repositories/{repositoryId}" should {
        "delete a repository" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val response = superuserClient.delete("/api/v1/repositories/${createdRepository.id}")

                response.status shouldBe HttpStatusCode.NoContent
                productService.listRepositoriesForProduct(productId) shouldBe emptyList()
            }
        }

        "delete Keycloak roles and groups" {
            integrationTestApplication {
                val createdRepository = createRepository()

                superuserClient.delete("/api/v1/repositories/${createdRepository.id}")

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    RepositoryPermission.getRolesForRepository(createdRepository.id) +
                            RepositoryRole.getRolesForRepository(createdRepository.id)
                )

                keycloakClient.getGroups().map { it.name.value } shouldNot containAnyOf(
                    RepositoryRole.getGroupsForRepository(createdRepository.id)
                )
            }
        }

        "require RepositoryPermission.DELETE" {
            val createdRepository = createRepository()
            requestShouldRequireRole(
                RepositoryPermission.DELETE.roleName(createdRepository.id),
                HttpStatusCode.NoContent
            ) {
                delete("/api/v1/repositories/${createdRepository.id}")
            }
        }
    }

    "GET /repositories/{repositoryId}/runs" should {
        "return the ORT runs on a repository" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val run1 = ortRunRepository.create(createdRepository.id, "branch-1", JobConfigurations(), labelsMap)
                val run2 = ortRunRepository.create(createdRepository.id, "branch-2", JobConfigurations(), labelsMap)

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<OrtRun>>() shouldBe listOf(run1.mapToApi(), run2.mapToApi())
                }
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val createdRepository = createRepository()

                ortRunRepository.create(createdRepository.id, "branch-1", JobConfigurations(), labelsMap)
                val run2 = ortRunRepository.create(createdRepository.id, "branch-2", JobConfigurations(), labelsMap)

                val query = "?sort=-revision,-createdAt&limit=1"
                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs$query")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<OrtRun>>() shouldBe listOf(run2.mapToApi())
                }
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val createdRepository = createRepository()
            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(createdRepository.id)) {
                get("/api/v1/repositories/${createdRepository.id}/runs")
            }
        }
    }

    "POST /repositories/{repositoryId}/runs" should {
        "create a new ORT run" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val service = InfrastructureService(
                    name = "privateRepository",
                    url = "https://repo.example.org/test",
                    description = "a private repository used by this repository",
                    usernameSecretRef = "repositoryUsername",
                    passwordSecretRef = "repositoryPassword"
                )
                val environmentDefinitions = mapOf(
                    "maven" to listOf(mapOf("id" to "repositoryServer"))
                )
                val envConfig = EnvironmentConfig(
                    infrastructureServices = listOf(service),
                    environmentDefinitions = environmentDefinitions,
                    strict = false
                )
                val analyzerJob = AnalyzerJobConfiguration(
                    allowDynamicVersions = true,
                    environmentConfig = envConfig
                )
                val createRun = CreateOrtRun("main", ApiJobConfigurations(analyzerJob), labelsMap)

                val serviceDeclaration = InfrastructureServiceDeclaration(
                    name = service.name,
                    url = service.url,
                    description = service.description,
                    usernameSecret = service.usernameSecretRef,
                    passwordSecret = service.passwordSecretRef
                )

                val response = superuserClient.post("/api/v1/repositories/${createdRepository.id}/runs") {
                    setBody(createRun)
                }

                response.status shouldBe HttpStatusCode.Created
                val run = response.body<OrtRun>()
                run.jobs.analyzer.environmentConfig shouldBe envConfig

                MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)

                val runs = ortRunRepository.listForRepository(createdRepository.id)

                with(runs.single().jobs.analyzer) {
                    allowDynamicVersions shouldBe true
                    val jobConfig = environmentConfig.shouldNotBeNull()
                    jobConfig.strict shouldBe false
                    jobConfig.environmentDefinitions shouldBe environmentDefinitions
                    jobConfig.infrastructureServices shouldContainExactly listOf(serviceDeclaration)
                }
            }
        }

        "require RepositoryPermission.TRIGGER_ORT_RUN" {
            val createdRepository = createRepository()
            requestShouldRequireRole(
                RepositoryPermission.TRIGGER_ORT_RUN.roleName(createdRepository.id),
                HttpStatusCode.Created
            ) {
                val createRun = CreateOrtRun("main", ApiJobConfigurations(), labelsMap)
                post("/api/v1/repositories/${createdRepository.id}/runs") { setBody(createRun) }
            }
        }
    }

    "GET /repositories/{repositoryId}/secrets" should {
        "return all secrets for this repository" {
            integrationTestApplication {
                val repositoryId = createRepository().id

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

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/secrets")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi(), secret2.mapToApi())
                }
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val repositoryId = createRepository().id

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

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/secrets?sort=-name&limit=1")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Secret>>() shouldBe listOf(secret1.mapToApi())
                }
            }
        }

        "require RepositoryPermission.READ" {
            val createdRepository = createRepository()
            requestShouldRequireRole(RepositoryPermission.READ.roleName(createdRepository.id)) {
                get("/api/v1/repositories/${createdRepository.id}/secrets")
            }
        }
    }

    "GET /repositories/{repositoryId}/secrets/{secretId}" should {
        "return a single secret" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val path = "https://secret-storage.com/ssh_host_rsa_key_5"
                val name = "New secret 5"
                val description = "description"

                secretRepository.create(path, name, description, null, null, repositoryId)

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/secrets/$name")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Secret>() shouldBe Secret(name, description)
                }
            }
        }

        "respond with NotFound if no secret exists" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/secrets/999999")

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        "require RepositoryPermission.READ" {
            val createdRepository = createRepository()

            val path = "https://secret-storage.com/ssh_host_rsa_key_5"
            val name = "New secret 5"
            val description = "description"

            secretRepository.create(path, name, description, null, null, createdRepository.id)

            requestShouldRequireRole(RepositoryPermission.READ.roleName(createdRepository.id)) {
                get("/api/v1/repositories/${createdRepository.id}/secrets/$name")
            }
        }
    }

    "POST /repositories/{repositoryId}/secrets" should {
        "create a secret in the database" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val name = "New secret 6"

                val secret = CreateSecret(
                    name,
                    secretValue,
                    "The new repo secret"
                )

                val response = superuserClient.post("/api/v1/repositories/$repositoryId/secrets") {
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
                provider.readSecret(Path("repository_${repositoryId}_$name"))?.value shouldBe secretValue
            }
        }

        "respond with CONFLICT if the secret already exists" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val name = "New secret 7"
                val description = "description"

                val secret = CreateSecret(name, secretValue, description)

                val response1 = superuserClient.post("/api/v1/repositories/$repositoryId/secrets") {
                    setBody(secret)
                }

                with(response1) {
                    status shouldBe HttpStatusCode.Created
                }

                val response2 = superuserClient.post("/api/v1/repositories/$repositoryId/secrets") {
                    setBody(secret)
                }

                with(response2) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }

        "require RepositoryPermission.WRITE_SECRETS" {
            val createdRepository = createRepository()
            requestShouldRequireRole(
                RepositoryPermission.WRITE_SECRETS.roleName(createdRepository.id),
                HttpStatusCode.Created
            ) {
                val createSecret = CreateSecret("name", secretValue, "description")
                post("/api/v1/repositories/${createdRepository.id}/secrets") { setBody(createSecret) }
            }
        }
    }

    "PATCH /repositories/{repositoryId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val updatedDescription = "updated description"
                val name = "name"
                val path = "path"

                secretRepository.create(path, name, "description", null, null, repositoryId)

                val updateSecret = UpdateSecret(name.asPresent(), description = updatedDescription.asPresent())
                val response = superuserClient.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
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
            integrationTestApplication {
                val repositoryId = createRepository().id

                val name = "name"
                val desc = "description"

                secretRepository.create(secretErrorPath, name, desc, null, null, repositoryId)

                val updateSecret = UpdateSecret(name.asPresent(), "newVal".asPresent(), "newDesc".asPresent())
                val response = superuserClient.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
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
            integrationTestApplication {
                val repositoryId = createRepository().id

                val name = "name"
                val path = "path"
                val desc = "some description"

                secretRepository.create(path, name, desc, null, null, repositoryId)

                val updateSecret = UpdateSecret(name.asPresent(), secretValue.asPresent())
                val response = superuserClient.patch("/api/v1/repositories/$repositoryId/secrets/$name") {
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

        "require RepositoryPermission.WRITE_SECRETS" {
            val createdRepository = createRepository()

            val name = "name"
            val desc = "description"
            val path = "path"

            secretRepository.create(path, name, desc, null, null, createdRepository.id)

            requestShouldRequireRole(RepositoryPermission.WRITE_SECRETS.roleName(createdRepository.id)) {
                val updateSecret =
                    UpdateSecret(name.asPresent(), secretValue.asPresent(), "new description".asPresent())
                patch("/api/v1/repositories/${createdRepository.id}/secrets/$name") { setBody(updateSecret) }
            }
        }
    }

    "DELETE /repositories/{repositoryId}/secrets/{secretName}" should {
        "delete a secret" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val path = SecretsProviderFactoryForTesting.SERVICE_PATH
                val name = "New secret 8"
                secretRepository.create(path.path, name, "description", null, null, repositoryId)

                val response = superuserClient.delete("/api/v1/repositories/$repositoryId/secrets/$name")

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                secretRepository.listForRepository(repositoryId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(path) should beNull()
            }
        }

        "handle failures from the SecretStorage" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val name = "New secret 8"
                val desc = "description"
                secretRepository.create(secretErrorPath, name, desc, null, null, repositoryId)

                val response = superuserClient.delete("/api/v1/repositories/$repositoryId/secrets/$name")

                with(response) {
                    status shouldBe HttpStatusCode.InternalServerError
                }

                secretRepository.getByRepositoryIdAndName(
                    repositoryId,
                    name
                )?.mapToApi() shouldBe Secret(name, desc)
            }
        }

        "require RepositoryPermission.WRITE_SECRETS" {
            val createdRepository = createRepository()

            val path = SecretsProviderFactoryForTesting.TOKEN_PATH
            val name = "New secret 8"
            secretRepository.create(path.path, name, "description", null, null, createdRepository.id)

            requestShouldRequireRole(
                RepositoryPermission.WRITE_SECRETS.roleName(createdRepository.id),
                HttpStatusCode.NoContent
            ) {
                delete("/api/v1/repositories/${createdRepository.id}/secrets/$name")
            }
        }
    }
})
