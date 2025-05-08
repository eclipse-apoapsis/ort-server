/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import com.typesafe.config.Config

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.engine.spec.tempdir
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.withEnvironment
import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.runs

import java.io.File
import java.util.Properties

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.dao.test.verifyDatabaseModuleIncluded
import org.eclipse.apoapsis.ortserver.dao.test.withMockDatabaseModule
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageReceiverFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService

import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock

private const val JOB_ID = 1L
private const val TRACE_ID = "42"

private val messageHeader = MessageHeader(TRACE_ID, 24)

private val analyzerRequest = AnalyzerRequest(
    analyzerJobId = JOB_ID
)

private const val USERNAME = "scott"
private const val PASSWORD = "tiger"

class AnalyzerEndpointTest : KoinTest, StringSpec() {
    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        stopKoin()
        MessageReceiverFactoryForTesting.reset()
    }

    init {
        "The database module should be added" {
            runEndpointTest {
                verifyDatabaseModuleIncluded()
            }
        }

        "The worker context module should be added" {
            runEndpointTest {
                val contextFactory by inject<WorkerContextFactory>()

                contextFactory.withContext(42L) {
                    it shouldNot beNull()
                }
            }
        }

        "The build environment should contain a remotes.json file" {
            runEnvironmentTest { homeFolder ->
                val conanRemotesFile = homeFolder.resolve(".conan/remotes.json")
                val content = conanRemotesFile.readText()

                content shouldContain "conancenter"
                content shouldContain "https://center.conan.io"
                content shouldContain "verify_ssl"
                content shouldContain "true"
            }
        }

        "The build environment should contain a .git-credentials file" {
            runEnvironmentTest { homeFolder ->
                val gitCredentialsFile = homeFolder.resolve(".git-credentials")
                val content = gitCredentialsFile.readText()

                content shouldContain "https://$USERNAME:$PASSWORD@repo2.example.org/test2/other-repository.git"
            }
        }

        "The build environment should contain a .m2/settings.xml file" {
            runEnvironmentTest { homeFolder ->
                val settingsFile = homeFolder.resolve(".m2/settings.xml")
                val content = settingsFile.readText()

                content shouldContain "<id>mainRepo</id>"
                content shouldContain "<password>$PASSWORD</password>"
            }
        }

        "The build environment should contain an .npmrc file" {
            runEnvironmentTest { homeFolder ->
                val npmRcFile = homeFolder.resolve(".npmrc")
                val content = npmRcFile.readText()

                content shouldContain "@external:registry="
                content shouldContain ":email=test@example.org"
            }
        }

        "The build environment should contain a NuGet.Config file" {
            runEnvironmentTest { homeFolder ->
                val nuGetFile = homeFolder.resolve(".nuget/NuGet/NuGet.Config")
                val content = nuGetFile.readText()

                content shouldContain "packageSources"
                content shouldContain "https://api.nuget.org/v3/index.json"
                content shouldContain "packageSourceCredentials"
                content shouldContain "<add key=\"ClearTextPassword\" "
            }
        }

        "The build environment should contain a .yarnrc.yml file" {
            runEnvironmentTest { homeFolder ->
                val yarnRcFile = homeFolder.resolve(".yarnrc.yml")
                val content = yarnRcFile.readText()

                content shouldContain "npmRegistries"
                content shouldContain "https://repo.example.org/test/repository.git"
                content shouldContain "npmAuthIdent"
                content shouldContain "npmAlwaysAuth: true"
            }
        }

        "The worker is correctly configured" {
            runEndpointTest {
                val worker by inject<AnalyzerWorker>()

                worker shouldNot beNull()
            }
        }

        "Configuration for secret storage is available" {
            runEndpointTest {
                val config by inject<Config>()

                config.hasPath("secretsProvider.name") shouldBe true
            }
        }

        "A message to analyze a project should be processed" {
            runEndpointTest {
                declareMock<AnalyzerWorker> {
                    coEvery { run(JOB_ID, TRACE_ID) } returns RunResult.Success
                }

                sendAnalyzerRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe AnalyzerWorkerResult(JOB_ID)
            }
        }

        "An error message should be sent back in case of a processing error" {
            runEndpointTest {
                declareMock<AnalyzerWorker> {
                    coEvery { run(JOB_ID, TRACE_ID) } returns RunResult.Failed(IllegalStateException("Test exception"))
                }

                sendAnalyzerRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe AnalyzerWorkerError(JOB_ID, "Test exception")
            }
        }

        "A 'run finished with issues' message should be sent when ORT issues are over the threshold" {
            runEndpointTest {
                declareMock<AnalyzerWorker> {
                    coEvery { run(JOB_ID, TRACE_ID) } returns RunResult.FinishedWithIssues
                }

                sendAnalyzerRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe AnalyzerWorkerResult(JOB_ID, true)
            }
        }

        "No response should be sent if the request is ignored" {
            runEndpointTest {
                declareMock<AnalyzerWorker> {
                    coEvery { run(JOB_ID, TRACE_ID) } returns RunResult.Ignored
                }

                sendAnalyzerRequest()

                MessageSenderFactoryForTesting.expectNoMessage(OrchestratorEndpoint)
            }
        }
    }

    /**
     * Simulate an incoming request to analyze a project.
     */
    private suspend fun sendAnalyzerRequest() {
        mockkTransaction {
            val message = Message(messageHeader, analyzerRequest)
            MessageReceiverFactoryForTesting.receive(AnalyzerEndpoint, message)
        }
    }

    /**
     * Run [block] as a test for the Analyzer endpoint. Start the endpoint with a configuration that selects the
     * testing transport. Then execute the given [block].
     */
    private suspend fun runEndpointTest(block: suspend () -> Unit) {
        withMockDatabaseModule {
            val environment = mapOf(
                "ANALYZER_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ANALYZER_SECRET_PROVIDER" to ConfigSecretProviderFactoryForTesting.NAME
            )

            withEnvironment(environment) {
                main()

                MockProvider.register { mockkClass(it) }

                block()
            }
        }
    }

    /**
     * Run [block] as a test with the Analyzer build environment fully set up. Obtain the [EnvironmentService] from
     * the dependency injection framework and invoke it to set up the build environment with proper mocks. This
     * should create the configuration files declared for the environment. The specified [test][block] can then
     * check whether these files have been created correctly.
     */
    private suspend fun runEnvironmentTest(block: (File) -> Unit) {
        runEndpointTest {
            val organization = Organization(20230627065854L, "Test organization")
            val product = Product(20230627065917L, organization.id, "Test product")
            val repository = Repository(
                20230627065942L,
                organization.id,
                product.id,
                RepositoryType.GIT,
                "https://repo.example.org/test.git"
            )
            val testHierarchy = Hierarchy(repository, product, organization)

            val usernameSecret = Secret(20230627040646L, "p1", "repositoryUsername", null, null, null, repository)
            val passwordSecret = Secret(20230627070543L, "p2", "repositoryPassword", null, null, null, repository)
            declareMock<SecretRepository> {
                every { listForRepository(repository.id) } returns
                        ListQueryResult(listOf(usernameSecret, passwordSecret), ListQueryParameters.DEFAULT, 2)
            }

            declareMock<InfrastructureServiceRepository> {
                every { getOrCreateForRun(any(), any()) } answers { firstArg() }
            }

            val secretsMap = mapOf(
                usernameSecret to USERNAME,
                passwordSecret to PASSWORD
            )
            val context = mockk<WorkerContext> {
                coEvery { resolveSecrets(*anyVararg()) } returns secretsMap
                every { credentialResolverFun } returns(secretsMap::getValue)
                every { hierarchy } returns testHierarchy
                every { ortRun } returns OrtRun(
                    id = 20230627071600L,
                    index = 27,
                    organizationId = organization.id,
                    productId = product.id,
                    repositoryId = repository.id,
                    revision = "main",
                    createdAt = Instant.parse("2023-06-27T05:17:02Z"),
                    finishedAt = null,
                    jobConfigs = JobConfigurations(),
                    resolvedJobConfigs = JobConfigurations(),
                    status = OrtRunStatus.CREATED,
                    labels = emptyMap(),
                    vcsId = null,
                    vcsProcessedId = null,
                    nestedRepositoryIds = null,
                    repositoryConfigId = null,
                    issues = emptyList(),
                    jobConfigContext = null,
                    resolvedJobConfigContext = null,
                    traceId = "trace-id",
                )
                coEvery { setupAuthentication(any(), any()) } just runs
            }

            val repositoryFolder = File("src/test/resources/mavenProject")
            val homeFolder = tempdir()
            val properties = Properties().apply {
                setProperty("user.home", homeFolder.absolutePath)
            }

            val environmentService by inject<EnvironmentService>()

            withSystemProperties(properties, mode = OverrideMode.SetOrOverride) {
                environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())
            }

            block(homeFolder)
        }
    }
}
