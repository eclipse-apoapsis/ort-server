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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

import java.util.EnumSet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApiSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.CredentialsType as ApiCredentialsType
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentVariableDeclaration as ApiEnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations as ApiJobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType as ApiRepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Secret
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.User as ApiUser
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup as ApiUserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups as ApiUserWithGroups
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.core.SUPERUSER
import org.eclipse.apoapsis.ortserver.core.TEST_USER
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.apimodel.valueOrThrow
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.utils.test.Integration

@Suppress("LargeClass")
class RepositoriesRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    lateinit var productService: ProductService
    lateinit var ortRunRepository: OrtRunRepository
    lateinit var pluginService: PluginService
    lateinit var secretRepository: SecretRepository
    lateinit var repositoryService: RepositoryService

    var orgId = -1L
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

        pluginService = PluginService(dbExtension.db)

        productService = ProductService(
            dbExtension.db,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            dbExtension.fixtures.ortRunRepository,
            authorizationService
        )

        repositoryService = RepositoryService(
            dbExtension.db,
            dbExtension.fixtures.ortRunRepository,
            dbExtension.fixtures.repositoryRepository,
            dbExtension.fixtures.analyzerJobRepository,
            dbExtension.fixtures.advisorJobRepository,
            dbExtension.fixtures.scannerJobRepository,
            dbExtension.fixtures.evaluatorJobRepository,
            dbExtension.fixtures.reporterJobRepository,
            dbExtension.fixtures.notifierJobRepository,
            authorizationService
        )

        ortRunRepository = dbExtension.fixtures.ortRunRepository
        secretRepository = dbExtension.fixtures.secretRepository

        orgId = organizationService.createOrganization(name = "name", description = "description").id
        productId =
            organizationService.createProduct(name = "name", description = "description", organizationId = orgId).id
    }

    val labelsMap = mapOf("label1" to "label1", "label2" to "label2")

    val repositoryType = RepositoryType.GIT
    val repositoryUrl = "https://example.org/repo.git"
    val repositoryDescription = "description"

    suspend fun createRepository(
        type: RepositoryType = repositoryType,
        url: String = repositoryUrl,
        prodId: Long = productId,
        description: String? = repositoryDescription
    ) = productService.createRepository(type, url, prodId, description)

    suspend fun addUserToGroup(username: String, organizationId: Long, groupId: String) =
        repositoryService.addUserToGroup(username, organizationId, groupId)

    fun createJobSummaries(ortRunId: Long) = dbExtension.fixtures.createJobs(ortRunId).mapToApiSummary()

    val secretPath = "path"
    val secretName = "name"
    val secretDescription = "description"

    fun createSecret(
        repositoryId: Long,
        path: String = secretPath,
        name: String = secretName,
        description: String = secretDescription,
    ) = secretRepository.create(path, name, description, RepositoryId(repositoryId))

    "GET /repositories/{repositoryId}" should {
        "return a single repository" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody
                    Repository(
                        createdRepository.id,
                        orgId,
                        productId,
                        repositoryType.mapToApi(),
                        repositoryUrl,
                        repositoryDescription
                    )
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
                    "https://svn.example.com/repos/org/repo/trunk".asPresent(),
                    "updateDescription".asPresent()
                )

                val response = superuserClient.patch("/api/v1/repositories/${createdRepository.id}") {
                    setBody(updateRepository)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Repository(
                    createdRepository.id,
                    orgId,
                    productId,
                    updateRepository.type.valueOrThrow,
                    updateRepository.url.valueOrThrow,
                    updateRepository.description.valueOrThrow
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

                productService.listRepositoriesForProduct(productId).data shouldBe emptyList()
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
                    labelsMap,
                    traceId = "trace1",
                    null
                )
                val run2 = ortRunRepository.create(
                    createdRepository.id,
                    "branch-2",
                    null,
                    JobConfigurations(),
                    "test",
                    labelsMap,
                    traceId = "trace2",
                    null
                )

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(run1.mapToApiSummary(JobSummaries()), run2.mapToApiSummary(JobSummaries())),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
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
                    traceId = "trace1",
                    null
                )

                val run2 = ortRunRepository.create(
                    createdRepository.id,
                    "revision",
                    null,
                    dbExtension.fixtures.jobConfigurations,
                    "test",
                    labelsMap,
                    traceId = "trace2",
                    null
                )

                val jobs1 = createJobSummaries(run1.id)
                val jobs2 = createJobSummaries(run2.id)

                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(run1.mapToApiSummary(jobs1), run2.mapToApiSummary(jobs2)),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("index", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val createdRepository = createRepository()

                ortRunRepository.create(
                    createdRepository.id,
                    "branch-1",
                    null,
                    JobConfigurations(),
                    null,
                    labelsMap,
                    traceId = "test-trace-id",
                    null
                )
                val run2 = ortRunRepository.create(
                    createdRepository.id,
                    "branch-2",
                    null,
                    JobConfigurations(),
                    "testContext",
                    labelsMap,
                    traceId = "trace",
                    null
                )

                val query = "?sort=-revision,-createdAt&limit=1"
                val response = superuserClient.get("/api/v1/repositories/${createdRepository.id}/runs$query")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(run2.mapToApiSummary(JobSummaries())),
                    PagingData(
                        limit = 1,
                        offset = 0,
                        totalCount = 2,
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
                    labelsMap,
                    traceId = "some-trace-id",
                    null
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
                    labelsMap,
                    traceId = "trace-id",
                    null
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
                ortRunRepository.create(
                    createdRepository.id,
                    "revision",
                    null,
                    JobConfigurations(),
                    null,
                    labelsMap,
                    traceId = "test-trace-id",
                    null
                )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(createdRepository.id)) {
                get("/api/v1/repositories/${createdRepository.id}/runs/${run.index}")
            }
        }
    }

    "DELETE /repositories/{repositoryId}/runs/{ortRunIndex}" should {
        "require role RepositoryPermission.DELETE" {
            val createdRepository = createRepository()
            val run =
                ortRunRepository.create(
                    createdRepository.id,
                    "revision",
                    null,
                    JobConfigurations(),
                    null,
                    labelsMap,
                    traceId = "test-trace-id",
                    null
                )

            requestShouldRequireRole(
                RepositoryPermission.DELETE.roleName(createdRepository.id),
                HttpStatusCode.NoContent
            ) {
                delete("/api/v1/repositories/${createdRepository.id}/runs/${run.index}")
            }
        }

        "handle a non-existing ORT run" {
            val createdRepository = createRepository()
            integrationTestApplication {
                val response = superuserClient.delete("/api/v1/repositories/${createdRepository.id}/runs/12345")

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "delete an ORT run" {
            val createdRepository = createRepository()
            val run = ortRunRepository.create(
                createdRepository.id,
                "revision",
                null,
                JobConfigurations(),
                "jobConfigContext",
                labelsMap,
                traceId = "t1",
                null
            )

            integrationTestApplication {
                val response = superuserClient.delete("/api/v1/repositories/${createdRepository.id}/runs/${run.index}")

                response shouldHaveStatus HttpStatusCode.NoContent

                ortRunRepository.get(run.id) shouldBe beNull()
                ortRunRepository.getByIndex(createdRepository.id, run.index) shouldBe beNull()
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
                    ApiEnvironmentVariableDeclaration("MY_ENV_VAR", "mySecret"),
                    ApiEnvironmentVariableDeclaration("MY_OTHER_ENV_VAR", value = "nonSensitiveData")
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
                val ruleSet = "test"
                val createRun = CreateOrtRun(
                    "main",
                    null,
                    ApiJobConfigurations(
                        analyzerJob,
                        reporter = reporterJob,
                        parameters = parameters,
                        ruleSet = ruleSet
                    ),
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

                val run = ortRunRepository.listForRepository(createdRepository.id).data.single()

                val orchestratorMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                orchestratorMessage.header.ortRunId shouldBe run.id
                orchestratorMessage.header.traceId shouldBe run.traceId

                with(run.jobConfigs.analyzer) {
                    allowDynamicVersions shouldBe true
                    val jobConfig = environmentConfig.shouldNotBeNull()
                    jobConfig.strict shouldBe false
                    jobConfig.environmentDefinitions shouldBe environmentDefinitions
                    jobConfig.infrastructureServices shouldContainExactly listOf(serviceDeclaration)
                    jobConfig.environmentVariables shouldContainExactly listOf(
                        EnvironmentVariableDeclaration("MY_ENV_VAR", "mySecret"),
                        EnvironmentVariableDeclaration("MY_OTHER_ENV_VAR", value = "nonSensitiveData")
                    )
                }

                run.jobConfigs.reporter shouldNotBeNull {
                    copyrightGarbageFile shouldBe "COPYRIGHT_GARBAGE"
                    customLicenseTextDir shouldBe "LICENSE_TEXTS"
                }

                run.jobConfigs.parameters shouldBe parameters
                run.jobConfigs.ruleSet shouldBe ruleSet
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
                val runCount = 50
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

        "respond with \"Not Found\" if repository doesn't exist" {
            integrationTestApplication {
                val nonExistingRepositoryId = 999
                val createRun = CreateOrtRun(
                    "main",
                    null,
                    ApiJobConfigurations(),
                    emptyMap()
                )

                val response = superuserClient.post("/api/v1/repositories/$nonExistingRepositoryId/runs") {
                    setBody(createRun)
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        "respond with 'BadRequest' if not installed plugins are used" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val advisorPluginId = "notInstalledAdvisor"
                val packageConfigurationProviderPluginId = "notInstalledPackageConfigurationProvider"
                val packageCurationProviderPluginId = "notInstalledPackageCurationProvider"
                val packageManagerPluginId = "notInstalledPackageManager"
                val reporterPluginId = "notInstalledReporter"
                val scannerPluginId = "notInstalledScanner"

                // Create a run with not installed plugins.
                val createRun = CreateOrtRun(
                    "main",
                    null,
                    ApiJobConfigurations(
                        analyzer = AnalyzerJobConfiguration(
                            enabledPackageManagers = listOf(packageManagerPluginId),
                            packageCurationProviders = listOf(
                                ProviderPluginConfiguration(type = packageCurationProviderPluginId)
                            )
                        ),
                        advisor = AdvisorJobConfiguration(advisors = listOf(advisorPluginId)),
                        scanner = ScannerJobConfiguration(scanners = listOf(scannerPluginId)),
                        evaluator = EvaluatorJobConfiguration(
                            packageConfigurationProviders = listOf(
                                ProviderPluginConfiguration(type = packageConfigurationProviderPluginId)
                            )
                        ),
                        reporter = ReporterJobConfiguration(
                            formats = listOf(reporterPluginId)
                        )
                    )
                )

                val response = superuserClient.post("/api/v1/repositories/${createdRepository.id}/runs") {
                    setBody(createRun)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val errorMessage = response.bodyAsText()
                errorMessage shouldContain "not installed"
                errorMessage shouldContain advisorPluginId
                errorMessage shouldContain packageConfigurationProviderPluginId
                errorMessage shouldContain packageCurationProviderPluginId
                errorMessage shouldContain packageManagerPluginId
                errorMessage shouldContain reporterPluginId
                errorMessage shouldContain scannerPluginId
            }
        }

        "respond with 'BadRequest' if disabled plugins are used" {
            integrationTestApplication {
                val createdRepository = createRepository()

                val installedPlugins = pluginService.getPlugins()
                val advisorPluginId = installedPlugins.first { it.type == PluginType.ADVISOR }.id
                val packageConfigurationProviderPluginId =
                    installedPlugins.first { it.type == PluginType.PACKAGE_CONFIGURATION_PROVIDER }.id
                val packageCurationProviderPluginId =
                    installedPlugins.first { it.type == PluginType.PACKAGE_CURATION_PROVIDER }.id
                val packageManagerPluginId = installedPlugins.first { it.type == PluginType.PACKAGE_MANAGER }.id
                val reporterPluginId = installedPlugins.first { it.type == PluginType.REPORTER }.id
                val scannerPluginId = installedPlugins.first { it.type == PluginType.SCANNER }.id

                // Disable one plugin of each type.
                suspend fun disablePlugin(pluginType: PluginType, pluginId: String) =
                    superuserClient.post("/api/v1/admin/plugins/$pluginType/$pluginId/disable")

                disablePlugin(PluginType.ADVISOR, advisorPluginId)
                disablePlugin(PluginType.PACKAGE_CONFIGURATION_PROVIDER, packageConfigurationProviderPluginId)
                disablePlugin(PluginType.PACKAGE_CURATION_PROVIDER, packageCurationProviderPluginId)
                disablePlugin(PluginType.PACKAGE_MANAGER, packageManagerPluginId)
                disablePlugin(PluginType.REPORTER, reporterPluginId)
                disablePlugin(PluginType.SCANNER, scannerPluginId)

                // Create a run with disabled plugins.
                val createRun = CreateOrtRun(
                    "main",
                    null,
                    ApiJobConfigurations(
                        analyzer = AnalyzerJobConfiguration(
                            enabledPackageManagers = listOf(packageManagerPluginId),
                            packageCurationProviders = listOf(
                                ProviderPluginConfiguration(type = packageCurationProviderPluginId)
                            )
                        ),
                        advisor = AdvisorJobConfiguration(advisors = listOf(advisorPluginId)),
                        scanner = ScannerJobConfiguration(scanners = listOf(scannerPluginId)),
                        evaluator = EvaluatorJobConfiguration(
                            packageConfigurationProviders = listOf(
                                ProviderPluginConfiguration(type = packageConfigurationProviderPluginId)
                            )
                        ),
                        reporter = ReporterJobConfiguration(
                            formats = listOf(reporterPluginId)
                        )
                    )
                )

                val response = superuserClient.post("/api/v1/repositories/${createdRepository.id}/runs") {
                    setBody(createRun)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val errorMessage = response.bodyAsText()
                errorMessage shouldContain "disabled"
                errorMessage shouldContain advisorPluginId
                errorMessage shouldContain packageConfigurationProviderPluginId
                errorMessage shouldContain packageCurationProviderPluginId
                errorMessage shouldContain packageManagerPluginId
                errorMessage shouldContain reporterPluginId
                errorMessage shouldContain scannerPluginId
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
                val repositoryId = createRepository().id

                createSecret(repositoryId, "path1", "name1", "description1")
                val secret = createSecret(repositoryId, "path2", "name2", "description2")

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/secrets?sort=-name&limit=1")

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

                secretRepository.getByIdAndName(RepositoryId(repositoryId), secret.name)?.mapToApi() shouldBe
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

                secretRepository.getByIdAndName(RepositoryId(repositoryId), secret.name)?.mapToApi().shouldBeNull()

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

                secretRepository.getByIdAndName(RepositoryId(repositoryId), secret.name)
                    ?.mapToApi() shouldBe Secret(secret.name, updatedDescription)
            }
        }

        "update a secret's value" {
            integrationTestApplication {
                val repositoryId = createRepository().id
                val secret = createSecret(repositoryId)

                val updateSecret = UpdateSecret(secretValue.asPresent(), secretDescription.asPresent())
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

                val updateSecret = UpdateSecret(secretValue.asPresent(), "newDesc".asPresent())
                superuserClient.patch("/api/v1/repositories/$repositoryId/secrets/${secret.name}") {
                    setBody(updateSecret)
                } shouldHaveStatus HttpStatusCode.InternalServerError

                secretRepository.getByIdAndName(RepositoryId(repositoryId), secret.name) shouldBe secret
            }
        }

        "require RepositoryPermission.WRITE_SECRETS" {
            val createdRepository = createRepository()
            val secret = createSecret(createdRepository.id)

            requestShouldRequireRole(RepositoryPermission.WRITE_SECRETS.roleName(createdRepository.id)) {
                val updateSecret = UpdateSecret(secretValue.asPresent(), "new description".asPresent())
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

                secretRepository.listForId(RepositoryId(repositoryId)).data shouldBe emptyList()

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

                secretRepository.getByIdAndName(RepositoryId(repositoryId), secret.name) shouldBe secret
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

    "PUT/DELETE /repositories/{repositoryId}/groups/{groupId}" should {
        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "require ProductPermission.MANAGE_GROUPS for method '${method.value}'" {
                val createdRepo = createRepository()
                val user = Username(TEST_USER.username.value)
                requestShouldRequireRole(
                    RepositoryPermission.MANAGE_GROUPS.roleName(createdRepo.id),
                    HttpStatusCode.NoContent
                ) {
                    when (method) {
                        HttpMethod.Put -> put("/api/v1/repositories/${createdRepo.id}/groups/readers") {
                            setBody(user)
                        }
                        HttpMethod.Delete -> delete(
                            "/api/v1/repositories/${createdRepo.id}/groups/readers?username=${user.username}"
                        )
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
                    val createdRepo = createRepository()
                    val user = Username("non-existing-username")
                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/repositories/${createdRepo.id}/groups/readers"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/repositories/${createdRepo.id}/groups/readers?username=${user.username}"
                        )
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
                            "/api/v1/repositories/999999/groups/readers"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/repositories/999999/groups/readers?username=${user.username}"
                        )
                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.NotFound

                    val body = response.body<ErrorResponse>()
                    body.message shouldBe "Resource not found."
                }
            }
        }

        forAll(
            row(HttpMethod.Put)
        ) { method ->
            "respond with 'BadRequest' if the request body is invalid for method '${method.value}'" {
                integrationTestApplication {
                    val createdRepo = createRepository()
                    val org = CreateOrganization(name = "name", description = "description") // Wrong request body

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/repositories/${createdRepo.id}/groups/readers"
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
                    val createdRepo = createRepository()
                    val user = Username(TEST_USER.username.value)

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/repositories/${createdRepo.id}/groups/non-existing-group"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/repositories/${createdRepo.id}/groups/non-existing-group?username=${user.username}"
                        )
                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.NotFound

                    val body = response.body<ErrorResponse>()
                    body.message shouldBe "Resource not found."
                }
            }
        }
    }

    "PUT /repositories/{orgId}/groups/{groupId}" should {
        forAll(
            row("readers"),
            row("writers"),
            row("admins")
        ) { groupId ->
            "add a user to the '$groupId' group" {
                integrationTestApplication {
                    val createdRepo = createRepository()
                    val user = Username(TEST_USER.username.value)

                    val response = superuserClient.put(
                        "/api/v1/repositories/${createdRepo.id}/groups/$groupId"
                    ) {
                        setBody(user)
                    }

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val groupName = when (groupId) {
                        "readers" -> RepositoryRole.READER.groupName(createdRepo.id)
                        "writers" -> RepositoryRole.WRITER.groupName(createdRepo.id)
                        "admins" -> RepositoryRole.ADMIN.groupName(createdRepo.id)
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

    "DELETE /repositories/{orgId}/groups/{groupId}" should {
        forAll(
            row("readers"),
            row("writers"),
            row("admins")
        ) { groupId ->
            "remove a user from the '$groupId' group" {
                integrationTestApplication {
                    val createdRepo = createRepository()
                    val user = Username(TEST_USER.username.value)
                    addUserToGroup(user.username, createdRepo.id, groupId)

                    // Check pre-condition
                    val groupName = when (groupId) {
                        "readers" -> RepositoryRole.READER.groupName(createdRepo.id)
                        "writers" -> RepositoryRole.WRITER.groupName(createdRepo.id)
                        "admins" -> RepositoryRole.ADMIN.groupName(createdRepo.id)
                        else -> error("Unknown group: $groupId")
                    }
                    val groupBefore = keycloakClient.getGroup(GroupName(groupName))
                    val membersBefore = keycloakClient.getGroupMembers(groupBefore.name)
                    membersBefore shouldHaveSize 1
                    membersBefore.map { it.username } shouldContain TEST_USER.username

                    val response = superuserClient.delete(
                        "/api/v1/repositories/${createdRepo.id}/groups/$groupId?username=${user.username}"
                    )

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val groupAfter = keycloakClient.getGroup(GroupName(groupName))
                    groupAfter.shouldNotBeNull()

                    val membersAfter = keycloakClient.getGroupMembers(groupAfter.name)
                    membersAfter.shouldBeEmpty()
                }
            }
        }
    }

    "GET /repositories/{repositoryId}/users" should {
        "return list of users that have rights for repository" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                addUserToGroup(TEST_USER.username.value, repositoryId, "READERS")
                addUserToGroup(SUPERUSER.username.value, repositoryId, "WRITERS")
                addUserToGroup(SUPERUSER.username.value, repositoryId, "ADMINS")

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/users")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        ApiUserWithGroups(
                            ApiUser(SUPERUSER.username.value, SUPERUSER.firstName, SUPERUSER.lastName, SUPERUSER.email),
                            listOf(ApiUserGroup.ADMINS, ApiUserGroup.WRITERS)
                        ),
                        ApiUserWithGroups(
                            ApiUser(TEST_USER.username.value, TEST_USER.firstName, TEST_USER.lastName, TEST_USER.email),
                            listOf(ApiUserGroup.READERS)
                        )
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("username", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return empty list if no user has rights for repository" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/users")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse<ApiUserWithGroups>(
                    emptyList(),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 0,
                        sortProperties = listOf(SortProperty("username", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "respond with 'Bad Request' if there is more than one sort field" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/users?sort=username,firstName")

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Invalid query parameters."
                body.cause shouldBe "Exactly one sort field must be defined."
            }
        }

        "respond with 'Bad Request' if there is no sort field" {
            integrationTestApplication {
                val repositoryId = createRepository().id

                val response = superuserClient.get("/api/v1/repositories/$repositoryId/users?sort=")

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Invalid query parameters."
                body.cause shouldBe "Empty sort field."
            }
        }

        "require RepositoryPermission.READ" {
            val repositoryId = createRepository().id

            requestShouldRequireRole(RepositoryPermission.READ.roleName(repositoryId)) {
                get("/api/v1/repositories/$repositoryId/users")
            }
        }
    }
})
