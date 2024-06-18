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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.shouldContainExactly
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

import java.util.EnumSet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApiSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.CredentialsType as ApiCredentialsType
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentVariableDeclaration as ApiEnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.api.v1.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations as ApiJobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.PagingOptions
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType as ApiRepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.Secret
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.asPresent
import org.eclipse.apoapsis.ortserver.core.shouldHaveBody
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryPermission
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryRole
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.utils.test.Integration

@Suppress("LargeClass")
class RepositoriesRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

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

    fun createJobSummaries(ortRunId: Long) = dbExtension.fixtures.createJobs(ortRunId).mapToApiSummary()

    val secretPath = "path"
    val secretName = "name"
    val secretDescription = "description"

    fun createSecret(
        repositoryId: Long,
        path: String = secretPath,
        name: String = secretName,
        description: String = secretDescription,
    ) = secretRepository.create(path, name, description, null, null, repositoryId)

    "GET /repositories/{repositoryId}" should {
        "return a single repository" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Repository(createdRepository.id, repositoryType.mapToApi(), repositoryUrl)
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

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Repository(
                    createdRepository.id,
                    updateRepository.type.valueOrThrow,
                    updateRepository.url.valueOrThrow
                )
            }
        }

        "respond with 'Bad Request' if the repository's URL is malformed" {
            integrationTestApplication {
                val createRepository = createRepository()

                val repository = UpdateRepository(
                    ApiRepositoryType.SUBVERSION.asPresent(),
                    "ht tps://github.com/org/repo.git".asPresent()
                )

                val response = superuserClient.patch("/api/v1/repositories/${createRepository.id}") {
                    setBody(repository)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for UpdateRepository"
            }
        }

        "respond with 'Bad Request' if the repository's URL contains userinfo" {
            integrationTestApplication {
                val createRepository = createRepository()

                val repository = UpdateRepository(
                    ApiRepositoryType.SUBVERSION.asPresent(),
                    "https://user:password@github.com".asPresent()
                )

                val response = superuserClient.patch("/api/v1/repositories/${createRepository.id}") {
                    setBody(repository)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for UpdateRepository"
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

                superuserClient.delete("/api/v1/repositories/${createdRepository.id}") shouldHaveStatus
                        HttpStatusCode.NoContent

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

                val run1 = ortRunRepository.create(
                    createdRepository.id,
                    "branch-1",
                    null,
                    JobConfigurations(),
                    null,
                    labelsMap
                )
                val run2 = ortRunRepository.create(
                    createdRepository.id,
                    "branch-2",
                    null,
                    JobConfigurations(),
                    "test",
                    labelsMap
                )

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(run1.mapToApiSummary(JobSummaries()), run2.mapToApiSummary(JobSummaries())),
                    PagingOptions(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        sortProperties = listOf(SortProperty("index", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "include job details" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val run1 = ortRunRepository.create(
                    createdRepository.id,
                    "revision",
                    null,
                    dbExtension.fixtures.jobConfigurations,
                    null,
                    labelsMap,
                )

                val run2 = ortRunRepository.create(
                    createdRepository.id,
                    "revision",
                    null,
                    dbExtension.fixtures.jobConfigurations,
                    "test",
                    labelsMap
                )

                val jobs1 = createJobSummaries(run1.id)
                val jobs2 = createJobSummaries(run2.id)

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(run1.mapToApiSummary(jobs1), run2.mapToApiSummary(jobs2)),
                    PagingOptions(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        sortProperties = listOf(SortProperty("index", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val createdRepository = createRepository()

                ortRunRepository.create(createdRepository.id, "branch-1", null, JobConfigurations(), null, labelsMap)
                val run2 = ortRunRepository.create(
                    createdRepository.id,
                    "branch-2",
                    null,
                    JobConfigurations(),
                    "testContext",
                    labelsMap
                )

                val query = "?sort=-revision,-createdAt&limit=1"
                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs$query")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(run2.mapToApiSummary(JobSummaries())),
                    PagingOptions(
                        limit = 1,
                        offset = 0,
                        sortProperties = listOf(
                            SortProperty("revision", SortDirection.DESCENDING),
                            SortProperty("createdAt", SortDirection.DESCENDING)
                        )
                    )
                )
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val createdRepository = createRepository()
            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(createdRepository.id)) {
                get("/api/v1/repositories/${createdRepository.id}/runs")
            }
        }
    }

    "GET /repositories/{repositoryId}/runs/{ortRunIndex}" should {
        "return the requested ORT run" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val run = ortRunRepository.create(
                    createdRepository.id,
                    "revision",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap
                )

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs/${run.index}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody run.mapToApi(Jobs())
            }
        }

        "include job details" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val run = ortRunRepository.create(
                    createdRepository.id,
                    "revision",
                    null,
                    JobConfigurations(),
                    "testContext",
                    labelsMap
                )

                val jobs = dbExtension.fixtures.createJobs(run.id).mapToApi()

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs/${run.index}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody run.mapToApi(jobs)
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val createdRepository = createRepository()
            val run =
                ortRunRepository.create(createdRepository.id, "revision", null, JobConfigurations(), null, labelsMap)

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(createdRepository.id)) {
                get("/api/v1/repositories/${createdRepository.id}/runs/${run.index}")
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
                    passwordSecretRef = "repositoryPassword",
                    credentialsTypes = EnumSet.of(ApiCredentialsType.NETRC_FILE)
                )
                val environmentDefinitions = mapOf(
                    "maven" to listOf(mapOf("id" to "repositoryServer"))
                )
                val environmentVariables = listOf(
                    ApiEnvironmentVariableDeclaration("MY_ENV_VAR", "mySecret")
                )
                val envConfig = EnvironmentConfig(
                    infrastructureServices = listOf(service),
                    environmentDefinitions = environmentDefinitions,
                    environmentVariables = environmentVariables,
                    strict = false
                )
                val analyzerJob = AnalyzerJobConfiguration(
                    allowDynamicVersions = true,
                    environmentConfig = envConfig
                )
                val reporterJob = ReporterJobConfiguration(
                    copyrightGarbageFile = "COPYRIGHT_GARBAGE",
                    customLicenseTextDir = "LICENSE_TEXTS"
                )
                val parameters = mapOf("p1" to "v1", "p2" to "v2")
                val createRun = CreateOrtRun(
                    "main",
                    null,
                    ApiJobConfigurations(analyzerJob, reporter = reporterJob, parameters = parameters),
                    labelsMap
                )

                val serviceDeclaration = InfrastructureServiceDeclaration(
                    name = service.name,
                    url = service.url,
                    description = service.description,
                    usernameSecret = service.usernameSecretRef,
                    passwordSecret = service.passwordSecretRef,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE)
                )

                val response = superuserClient.post("/api/v1/repositories/${createdRepository.id}/runs") {
                    setBody(createRun)
                }

                response shouldHaveStatus HttpStatusCode.Created
                val runResponse = response.body<OrtRun>()
                runResponse.jobConfigs.analyzer.environmentConfig shouldBe envConfig
                runResponse.jobConfigs.parameters shouldBe parameters

                val run = ortRunRepository.listForRepository(createdRepository.id).single()

                val orchestratorMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                orchestratorMessage.header.ortRunId shouldBe run.id

                with(run.jobConfigs.analyzer) {
                    allowDynamicVersions shouldBe true
                    val jobConfig = environmentConfig.shouldNotBeNull()
                    jobConfig.strict shouldBe false
                    jobConfig.environmentDefinitions shouldBe environmentDefinitions
                    jobConfig.infrastructureServices shouldContainExactly listOf(serviceDeclaration)
                    jobConfig.environmentVariables shouldContainExactly listOf(
                        EnvironmentVariableDeclaration("MY_ENV_VAR", "mySecret")
                    )
                }

                with(run.jobConfigs.reporter.shouldNotBeNull()) {
                    copyrightGarbageFile shouldBe "COPYRIGHT_GARBAGE"
                    customLicenseTextDir shouldBe "LICENSE_TEXTS"
                }

                run.jobConfigs.parameters shouldBe parameters
            }
        }

        "require RepositoryPermission.TRIGGER_ORT_RUN" {
            val createdRepository = createRepository()
            requestShouldRequireRole(
                RepositoryPermission.TRIGGER_ORT_RUN.roleName(createdRepository.id),
                HttpStatusCode.Created
            ) {
                val createRun = CreateOrtRun("main", null, ApiJobConfigurations(), labelsMap)
                post("/api/v1/repositories/${createdRepository.id}/runs") { setBody(createRun) }
            }
        }

        "handle concurrent requests to create runs for the same repository" {
            integrationTestApplication {
                val createdRepository = createRepository()
                val runCount = 3
                val analyzerJob = AnalyzerJobConfiguration(
                    allowDynamicVersions = true
                )

                val responses = withContext(Dispatchers.IO) {
                    (1..runCount).map { idx ->
                        async {
                            val createRun = CreateOrtRun(
                                "branch-$idx",
                                null,
                                ApiJobConfigurations(analyzerJob)
                            )
                            superuserClient.post("/api/v1/repositories/${createdRepository.id}/runs") {
                                setBody(createRun)
                            }
                        }
                    }.awaitAll()
                }

                responses.forAll { it shouldHaveStatus HttpStatusCode.Created }
            }
        }
    }

    "GET /repositories/{repositoryId}/secrets" should {
        "return all secrets for this repository" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val secret1 = createSecret(repositoryId, "path1", "name1", "description1")
                val secret2 = createSecret(repositoryId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/secrets")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(secret1.mapToApi(), secret2.mapToApi()),
                    PagingOptions(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                createSecret(repositoryId, "path1", "name1", "description1")
                val secret = createSecret(repositoryId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/secrets?sort=-name&limit=1")

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
                val secret = createSecret(repositoryId)

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/secrets/${secret.name}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody secret.mapToApi()
            }
        }

        "respond with NotFound if no secret exists" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                superuserClient.get("/api/v1/repositories/$repositoryId/secrets/999999") shouldHaveStatus
                        HttpStatusCode.NotFound
            }
        }

        "require RepositoryPermission.READ" {
            val createdRepository = createRepository()
            val secret = createSecret(createdRepository.id)

            requestShouldRequireRole(RepositoryPermission.READ.roleName(createdRepository.id)) {
                get("/api/v1/repositories/${createdRepository.id}/secrets/${secret.name}")
            }
        }
    }

    "POST /repositories/{repositoryId}/secrets" should {
        "create a secret in the database" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = CreateSecret(secretName, secretValue, secretDescription)

                val response = superuserClient.post("/api/v1/repositories/$repositoryId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Secret(secret.name, secret.description)

                secretRepository.getByRepositoryIdAndName(repositoryId, secret.name)?.mapToApi() shouldBe
                    Secret(secret.name, secret.description)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("repository_${repositoryId}_${secret.name}"))?.value shouldBe secretValue
            }
        }

        "respond with CONFLICT if the secret already exists" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = CreateSecret(secretName, secretValue, secretDescription)

                superuserClient.post("/api/v1/repositories/$repositoryId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Created

                superuserClient.post("/api/v1/repositories/$repositoryId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Conflict
            }
        }

        "require RepositoryPermission.WRITE_SECRETS" {
            val createdRepository = createRepository()
            requestShouldRequireRole(
                RepositoryPermission.WRITE_SECRETS.roleName(createdRepository.id),
                HttpStatusCode.Created
            ) {
                val createSecret = CreateSecret(secretName, secretValue, secretDescription)
                post("/api/v1/repositories/${createdRepository.id}/secrets") { setBody(createSecret) }
            }
        }

        "respond with 'Bad Request' if the secret's name is invalid" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = CreateSecret(" secret_28! ", secretValue, secretDescription)

                val response = superuserClient.post("/api/v1/repositories/$repositoryId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateSecret"

                secretRepository.getByRepositoryIdAndName(repositoryId, secret.name)?.mapToApi().shouldBeNull()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("repository_${repositoryId}_${secret.name}"))?.value.shouldBeNull()
            }
        }
    }

    "PATCH /repositories/{repositoryId}/secrets/{secretName}" should {
        "update a secret's metadata" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = createSecret(repositoryId)

                val updatedDescription = "updated description"
                val updateSecret = UpdateSecret(secret.name.asPresent(), description = updatedDescription.asPresent())

                val response = superuserClient.patch("/api/v1/repositories/$repositoryId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Secret(secret.name, updatedDescription)

                secretRepository.getByRepositoryIdAndName(repositoryId, updateSecret.name.valueOrThrow)
                    ?.mapToApi() shouldBe Secret(secret.name, updatedDescription)
            }
        }

        "update a secret's value" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = createSecret(repositoryId)

                val updateSecret = UpdateSecret(secret.name.asPresent(), secretValue.asPresent())
                val response = superuserClient.patch("/api/v1/repositories/$repositoryId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody secret.mapToApi()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path))?.value shouldBe secretValue
            }
        }

        "handle a failure from the SecretsStorage" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = createSecret(repositoryId, path = secretErrorPath)

                val updateSecret = UpdateSecret(secret.name.asPresent(), secretValue.asPresent(), "newDesc".asPresent())
                superuserClient.patch("/api/v1/repositories/$repositoryId/secrets/${secret.name}") {
                    setBody(updateSecret)
                } shouldHaveStatus HttpStatusCode.InternalServerError

                secretRepository.getByRepositoryIdAndName(repositoryId, secret.name) shouldBe secret
            }
        }

        "require RepositoryPermission.WRITE_SECRETS" {
            val createdRepository = createRepository()
            val secret = createSecret(createdRepository.id)

            requestShouldRequireRole(RepositoryPermission.WRITE_SECRETS.roleName(createdRepository.id)) {
                val updateSecret =
                    UpdateSecret(secret.name.asPresent(), secretValue.asPresent(), "new description".asPresent())
                patch("/api/v1/repositories/${createdRepository.id}/secrets/${secret.name}") { setBody(updateSecret) }
            }
        }
    }

    "DELETE /repositories/{repositoryId}/secrets/{secretName}" should {
        "delete a secret" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = createSecret(repositoryId)

                superuserClient.delete("/api/v1/repositories/$repositoryId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.listForRepository(repositoryId) shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = createSecret(repositoryId, path = secretErrorPath)

                superuserClient.delete("/api/v1/repositories/$repositoryId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.InternalServerError

                secretRepository.getByRepositoryIdAndName(repositoryId, secret.name) shouldBe secret
            }
        }

        "require RepositoryPermission.WRITE_SECRETS" {
            val createdRepository = createRepository()
            val secret = createSecret(createdRepository.id)

            requestShouldRequireRole(
                RepositoryPermission.WRITE_SECRETS.roleName(createdRepository.id),
                HttpStatusCode.NoContent
            ) {
                delete("/api/v1/repositories/${createdRepository.id}/secrets/${secret.name}")
            }
        }
    }
})
