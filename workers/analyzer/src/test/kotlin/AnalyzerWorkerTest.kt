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

import com.github.michaelbull.result.Ok

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.AssertionErrorBuilder
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.ByteArrayInputStream
import java.io.File
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerPhase as AnalyzerPhaseEnum
import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.AppliedPackageCurationRef
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel
import org.eclipse.apoapsis.ortserver.shared.orttestdata.OrtTestData
import org.eclipse.apoapsis.ortserver.shared.orttestdata.OrtTestData.TIME_STAMP_SECONDS
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerOrtConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentForkHelper
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SecretVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SimpleVariableDefinition

import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.common.Os

private const val JOB_ID = 1L
private const val TRACE_ID = "42"

private val projectDir = File("src/test/resources/mavenProject/").absoluteFile

private val repository = Repository(
    id = 1L,
    organizationId = 1L,
    productId = 1L,
    type = RepositoryType.GIT,
    url = "https://example.com/git/repository.git"
)

private val hierarchy = Hierarchy(repository, mockk(), mockk())

private val ortRun = OrtRun(
    id = 12L,
    index = 1L,
    organizationId = 1L,
    productId = 1L,
    repositoryId = repository.id,
    revision = "main",
    path = null,
    createdAt = Clock.System.now(),
    jobConfigs = JobConfigurations(),
    resolvedJobConfigs = JobConfigurations(),
    status = OrtRunStatus.ACTIVE,
    finishedAt = null,
    labels = emptyMap(),
    vcsId = 1L,
    vcsProcessedId = 1L,
    nestedRepositoryIds = emptyMap(),
    repositoryConfigId = 1L,
    issues = emptyList(),
    jobConfigContext = "context",
    resolvedJobConfigContext = "context",
    traceId = "trace-id"
)

private val analyzerJob = AnalyzerJob(
    id = JOB_ID,
    ortRunId = ortRun.id,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = AnalyzerJobConfiguration(),
    status = JobStatus.CREATED,
    errorMessage = null
)

/**
 * Helper function to invoke this worker with the given [phase], [args] and further test parameters.
 */
private suspend fun AnalyzerWorker.testRun(phase: AnalyzerPhase, args: Array<String> = emptyArray()): RunResult =
    run(JOB_ID, TRACE_ID, phase, args)

@Suppress("LargeClass")
class AnalyzerWorkerTest : StringSpec({
    beforeEach {
        mockkObject(EndpointComponent)
        coEvery { EndpointComponent.generateKeepAliveFile() } just runs
    }

    afterEach {
        unmockkAll()
    }

    "A private repository should be analyzed successfully" {
        val jobConfig = AnalyzerJobConfiguration(
            enabledPackageManagers = listOf("Maven"),
            packageCurationProviders = listOf(mockk()),
            keepAliveWorker = true,
            keepAlivePhases = EnumSet.of(AnalyzerPhaseEnum.ANALYSIS)
        )
        val job = analyzerJob.copy(configuration = jobConfig)

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns job
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val infrastructureServices = listOf<InfrastructureService>(mockk(relaxed = true), mockk(relaxed = true))
        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns infrastructureServices
            coEvery { setupAuthentication(context, infrastructureServices) } just runs
            coEvery {
                setUpEnvironment(context, projectDir, null, infrastructureServices)
            } returns ResolvedEnvironmentConfig()
        }

        val phase = spyk(
            FullPhase(
                mockk(),
                ortRunService,
                contextFactory,
                envService,
                mockk(relaxed = true),
                mockIssueResolutionService()
            )
        )
        val worker = AnalyzerWorker(downloader, AnalyzerRunner(ConfigFactory.empty()))

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                ortRunService.updateResolvedRevision(ortRun.id, "resolvedRevision")
                ortRunService.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID }, any())
                ortRunService.storeRepositoryInformation(any(), any())
            }

            coVerifyOrder {
                envService.setupAuthentication(context, infrastructureServices)
                downloader.downloadRepository(repository.url, ortRun.revision)
                envService.setUpEnvironment(context, projectDir, null, infrastructureServices)
            }

            coVerify {
                phase.handleKeepAlive(jobConfig)
            }
        }
    }

    "A repository without credentials should be analyzed successfully" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, AnalyzerRunner(ConfigFactory.empty()))

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                ortRunService.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID }, any())
            }

            coVerify(exactly = 0) {
                envService.setupAuthentication(any(), any())
            }

            coVerify {
                envService.setUpEnvironment(context, projectDir, null, emptyList())
            }
        }
    }

    "An environment configuration in the job configuration should be supported" {
        val envConfig = mockk<EnvironmentConfig>()
        val jobConfig = AnalyzerJobConfiguration(environmentConfig = envConfig)
        val job = analyzerJob.copy(configuration = jobConfig)

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns job
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns job
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, envConfig) } returns emptyList()
            coEvery {
                setUpEnvironment(context, projectDir, envConfig, emptyList())
            } returns ResolvedEnvironmentConfig()
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, AnalyzerRunner(ConfigFactory.empty()))

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                ortRunService.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID }, any())
            }

            coVerify {
                envService.setUpEnvironment(context, projectDir, envConfig, emptyList())
            }
        }
    }

    "AnalyzerRunner should be invoked correctly with an environment config from the job configuration" {
        val envConfig = mockk<EnvironmentConfig>()
        val jobConfig = AnalyzerJobConfiguration(
            environmentConfig = envConfig,
            enabledPackageManagers = listOf("Maven"),
            packageCurationProviders = listOf(mockk())
        )
        val job = analyzerJob.copy(configuration = jobConfig)

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns job
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns job
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val packageCurationProvidersConfigs = listOf(mockk<ProviderPluginConfiguration>())
        val context = mockWorkerContext {
            coEvery {
                resolveProviderPluginConfigSecrets(jobConfig.packageCurationProviders)
            } returns packageCurationProvidersConfigs
        }

        val contextFactory = mockContextFactory(context)

        val resolvedEnvConfig = ResolvedEnvironmentConfig()
        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, envConfig) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, envConfig, emptyList()) } returns resolvedEnvConfig
        }

        val runnerConfig = runnerConfig(jobConfig, packageCurationProvidersConfigs)
        val testException = IllegalStateException("AnalyzerRunner test exception")
        val runner = mockk<AnalyzerRunner> {
            coEvery { run(any(), runnerConfig, emptyMap()) } throws testException
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runner)

        mockkTransaction {
            when (val result = worker.testRun(phase)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> AssertionErrorBuilder.fail("Unexpected result: $result")
            }
        }
    }

    "AnalyzerRunner should be invoked correctly with an environment configuration from the repository" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val resolvedEnvConfig = ResolvedEnvironmentConfig()
        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, any()) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns resolvedEnvConfig
        }

        val testException = IllegalStateException("AnalyzerRunner test exception")
        val runner = mockk<AnalyzerRunner> {
            coEvery {
                run(
                    any(),
                    runnerConfig(analyzerJob.configuration),
                    emptyMap()
                )
            } throws testException
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runner)

        mockkTransaction {
            when (val result = worker.testRun(phase)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> AssertionErrorBuilder.fail("Unexpected result: $result")
            }
        }
    }

    "AnalyzerRunner should be invoked correctly with resolved environment variables" {
        val envConfig = mockk<EnvironmentConfig>()
        val jobConfig = AnalyzerJobConfiguration(
            environmentConfig = envConfig,
            enabledPackageManagers = listOf("Maven"),
            packageCurationProviders = listOf(mockk())
        )
        val job = analyzerJob.copy(configuration = jobConfig)

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns job
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns job
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val secret1 = mockk<Secret>()
        val secret2 = mockk<Secret>()
        val resolvedEnvConfig = ResolvedEnvironmentConfig(
            environmentVariables = setOf(
                SecretVariableDefinition("MY_ENV_VAR", secret1),
                SecretVariableDefinition("ANOTHER_ENV_VAR", secret2),
                SimpleVariableDefinition("SIMPLE_ENV_VAR", "simpleValue"),
                SimpleVariableDefinition("ANOTHER_SIMPLE_ENV_VAR", "anotherSimpleValue")
            )
        )
        val expectedEnvironmentVariables = mapOf(
            "MY_ENV_VAR" to "mySecret",
            "ANOTHER_ENV_VAR" to "anotherSecret",
            "SIMPLE_ENV_VAR" to "simpleValue",
            "ANOTHER_SIMPLE_ENV_VAR" to "anotherSimpleValue"
        )

        val secretsToResolve = mutableListOf<Secret>()
        val packageCurationProvidersConfigs = listOf(mockk<ProviderPluginConfiguration>())
        val context = mockWorkerContext {
            coEvery {
                resolveProviderPluginConfigSecrets(jobConfig.packageCurationProviders)
            } returns packageCurationProvidersConfigs

            coEvery { resolveSecrets(*varargAll { secretsToResolve.add(it) }) } answers {
                mapOf(
                    secret1 to "mySecret",
                    secret2 to "anotherSecret"
                )
            }
        }

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, envConfig) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, envConfig, emptyList()) } returns resolvedEnvConfig
        }

        val runnerConfig = runnerConfig(jobConfig, packageCurationProvidersConfigs)
        val testException = IllegalStateException("AnalyzerRunner test exception")
        val runner = mockk<AnalyzerRunner> {
            coEvery { run(any(), runnerConfig, expectedEnvironmentVariables) } throws testException
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runner)

        mockkTransaction {
            when (val result = worker.testRun(phase)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> AssertionErrorBuilder.fail("Unexpected result: $result")
            }
        }

        secretsToResolve should containExactlyInAnyOrder(secret1, secret2)
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } throws testException
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            mockk(),
            mockk(),
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(mockk(), AnalyzerRunner(ConfigFactory.empty()))

        mockkTransaction {
            when (val result = worker.testRun(phase)) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> AssertionErrorBuilder.fail("Unexpected result: $result")
            }
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = analyzerJob.copy(status = JobStatus.FINISHED)
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns invalidJob
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            mockk(),
            mockk(),
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(mockk(), AnalyzerRunner(ConfigFactory.empty()))

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Ignored
        }
    }

    "Resolved items should be stored for analyzer issues only" {
        val analyzerIssue = Issue(
            timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            source = "analyzer",
            message = "Analyzer issue",
            severity = Severity.ERROR
        )
        val nonAnalyzerIssue = Issue(
            timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            source = "scanner",
            message = "Scanner issue",
            severity = Severity.ERROR
        )

        val resolvedItemsSlot = slot<ResolvedItemsResult>()

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), capture(resolvedItemsSlot)) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val ortResult = OrtTestData.result.copy(
            repository = OrtTestData.result.repository.copy(
                config = OrtTestData.result.repository.config.copy(
                    resolutions = Resolutions(
                        issues = listOf(
                            IssueResolution(
                                message = analyzerIssue.message,
                                reason = IssueResolutionReason.CANT_FIX_ISSUE,
                                comment = "Analyzer resolution."
                            ),
                            IssueResolution(
                                message = nonAnalyzerIssue.message,
                                reason = IssueResolutionReason.CANT_FIX_ISSUE,
                                comment = "Non-analyzer resolution."
                            )
                        )
                    )
                )
            ),
            analyzer = OrtTestData.result.analyzer?.copy(
                result = OrtTestData.result.analyzer?.result!!.copy(
                    issues = mapOf(OrtIdentifier("Maven:com.example:package:1.0") to listOf(analyzerIssue))
                )
            ),
            scanner = OrtTestData.result.scanner?.copy(
                scanResults = OrtTestData.result.scanner!!.scanResults.map { scanResult ->
                    scanResult.copy(summary = scanResult.summary.copy(issues = listOf(nonAnalyzerIssue)))
                }.toSet()
            )
        )

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any()) } answers { ortResult }
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runnerMock)

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success

            val resolvedIssue = resolvedItemsSlot.captured.issues.keys.single()
            resolvedIssue.message shouldBe analyzerIssue.message
            resolvedIssue.source shouldBe analyzerIssue.source
            resolvedIssue.severity.name shouldBe analyzerIssue.severity.name
        }
    }

    "Resolved items should be stored even if no resolutions match" {
        val analyzerIssue = Issue(
            timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            source = "analyzer",
            message = "Unresolved analyzer issue",
            severity = Severity.ERROR
        )

        val resolvedItemsSlot = slot<ResolvedItemsResult>()

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), capture(resolvedItemsSlot)) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext {
            every { configManager } returns mockk<ConfigManager> {
                every { getFile(any(), any()) } returns ByteArrayInputStream(
                    """
                        ---
                        issues: []
                        rule_violations: []
                        vulnerabilities: []
                    """.trimIndent().toByteArray()
                )
            }
        }

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val ortResult = OrtTestData.result.copy(
            repository = OrtTestData.result.repository.copy(
                config = OrtTestData.result.repository.config.copy(
                    resolutions = Resolutions()
                )
            ),
            analyzer = OrtTestData.result.analyzer?.copy(
                result = OrtTestData.result.analyzer?.result!!.copy(
                    issues = mapOf(OrtIdentifier("Maven:com.example:package:1.0") to listOf(analyzerIssue))
                )
            )
        )

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any()) } answers { ortResult }
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runnerMock)

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.FinishedWithIssues
            resolvedItemsSlot.captured.issues should beEmpty()
        }
    }

    "Package curation associations should be stored after analyzer run" {
        val associationsSlot =
            slot<Map<Identifier, List<AppliedPackageCurationRef>>>()

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), capture(associationsSlot)) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any()) } returns OrtTestData.result
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runnerMock)

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                ortRunService.storePackageCurationAssociations(analyzerJob.ortRunId, any())
            }

            associationsSlot.captured shouldBe mapOf(
                Identifier("Maven", "com.example", "package", "1.0") to
                    listOf(
                        AppliedPackageCurationRef(
                            providerName = ResolvedPackageCurations.REPOSITORY_CONFIGURATION_PROVIDER_ID,
                            curationRank = 0
                        )
                    )
            )
        }
    }

    "Package curation associations should not be stored when no curations are applied" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val ortResultWithoutCurations = OrtTestData.result.copy(
            resolvedConfiguration = OrtTestData.result.resolvedConfiguration.copy(packageCurations = emptyList())
        )

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any()) } returns ortResultWithoutCurations
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runnerMock)

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success

            verify(exactly = 0) {
                ortRunService.storePackageCurationAssociations(any(), any())
            }
        }
    }

    "Excluded packages and projects should be captured correctly" {
        val excludedPackageIdsSlot = slot<Set<Identifier>>()
        val excludedProjectIdsSlot = slot<Set<Identifier>>()

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every {
                storeAnalyzerRun(any(), any(), capture(excludedPackageIdsSlot), capture(excludedProjectIdsSlot))
            } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val ortResultWithExcludedProject = OrtTestData.result.copy(
            analyzer = OrtTestData.result.analyzer?.copy(
                result = OrtTestData.result.analyzer?.result!!.copy(
                    projects = setOf(OrtTestData.project.copy(definitionFilePath = "excluded/pom.xml"))
                )
            )
        )

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any()) } returns ortResultWithExcludedProject
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runnerMock)

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success

            excludedPackageIdsSlot.captured shouldBe setOf(Identifier("Maven", "com.example", "package", "1.0"))
            excludedProjectIdsSlot.captured shouldBe setOf(Identifier("Maven", "com.example", "project", "1.0"))
        }
    }

    "A 'finished with issues' result should be returned if the analyzer run finished with issues" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any()) } answers {
                OrtTestData.result.copy(
                    repository = OrtTestData.result.repository.copy(
                        config = OrtTestData.result.repository.config.copy(
                            resolutions = Resolutions()
                        )
                    )
                )
            }
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runnerMock)

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.FinishedWithIssues
        }
    }

    "A 'success' result should be returned if the analyzer run finished with issues with severity HINT" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any()) } answers {
                OrtTestData.result.copy(
                    resolvedConfiguration = OrtTestData.result.resolvedConfiguration.copy(
                        resolutions = OrtTestData.result.resolvedConfiguration.resolutions?.copy(
                            issues = emptyList() // Remove any issue resolutions to simulate unresolved issues.
                        )
                    ),

                    analyzer = OrtTestData.result.analyzer?.copy(
                        result = OrtTestData.result.analyzer?.result!!.copy(
                            issues = mapOf(
                                OrtIdentifier("Maven:com.example:package:1.0") to listOf(
                                    Issue(
                                        timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS)
                                            .toJavaInstant(),
                                        source = "tool-x",
                                        message = "An issue occurred.",
                                        severity = Severity.HINT
                                    )
                                )
                            )
                        )
                    )
                )
            }
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runnerMock)

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success
        }
    }

    "A 'success' result should be returned if the analyzer run finished with resolved issues" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val context = mockWorkerContext()

        val contextFactory = mockContextFactory(context)

        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any()) } answers {
                OrtTestData.result
            }
        }

        val phase = FullPhase(
            mockk(),
            ortRunService,
            contextFactory,
            envService,
            mockk(relaxed = true),
            mockIssueResolutionService()
        )
        val worker = AnalyzerWorker(downloader, runnerMock)

        mockkTransaction {
            val result = worker.testRun(phase)

            result shouldBe RunResult.Success
        }
    }

    "The preparation phase should be executed" {
        val exchangeDir = tempdir()
        val jobDir = exchangeDir.resolve("$JOB_DIR_PREFIX${analyzerJob.id}")

        val jobConfig = AnalyzerJobConfiguration(
            enabledPackageManagers = listOf("Maven"),
            packageCurationProviders = listOf(mockk()),
            keepAliveWorker = true,
            keepAlivePhases = EnumSet.of(AnalyzerPhaseEnum.ANALYSIS)
        )
        val job = analyzerJob.copy(configuration = jobConfig)

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns job
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns job
            every { updateResolvedRevision(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every {
                downloadRepository(any(), any(), runId = ortRun.id, targetDir = jobDir)
            } returns
                    DownloadResult(projectDir, "main", "resolvedRevision")
        }

        val pluginConfigs = listOf(
            ProviderPluginConfiguration(
                type = "test-provider",
                options = mapOf("option1" to "value1", "option2" to "value2"),
                secrets = mapOf("password" to "secret-password")
            )
        )

        val context = mockWorkerContext {
            coEvery { resolveProviderPluginConfigSecrets(jobConfig.packageCurationProviders) } returns pluginConfigs
        }

        val refContextOpen = AtomicBoolean()
        val contextFactory = mockContextFactory(context, refContextOpen)

        val infrastructureServices = listOf<InfrastructureService>(mockk(relaxed = true), mockk(relaxed = true))
        val envService = mockk<EnvironmentService> {
            coEvery { findInfrastructureServicesForRepository(context, null) } returns infrastructureServices
            coEvery { setupAuthentication(context, infrastructureServices) } just runs
            coEvery {
                setUpEnvironment(context, projectDir, null, infrastructureServices, any())
            } returns ResolvedEnvironmentConfig(
                environmentVariables = setOf(
                    SimpleVariableDefinition("SIMPLE_ENV_VAR", "simpleValue")
                )
            )
        }

        mockkObject(EnvironmentForkHelper) {
            every { EnvironmentForkHelper.persistAuthenticationInfo(any()) } answers {
                refContextOpen.get() shouldBe true
            }

            val phase = spyk(
                PreparationPhase(
                    ortRunService,
                    contextFactory,
                    envService
                )
            )
            val runner = mockk<AnalyzerRunner>()
            val worker = AnalyzerWorker(downloader, runner)

            mockkTransaction {
                val result = worker.testRun(phase, arrayOf(exchangeDir.absolutePath))

                result shouldBe RunResult.Ignored

                verify(exactly = 1) {
                    ortRunService.updateResolvedRevision(ortRun.id, "resolvedRevision")

                    EnvironmentForkHelper.persistAuthenticationInfo(jobDir.resolve(AUTH_INFO_FILE))
                }

                coVerifyOrder {
                    envService.setupAuthentication(context, infrastructureServices)
                    downloader.downloadRepository(
                        repository.url,
                        ortRun.revision,
                        targetDir = jobDir,
                        runId = ortRun.id
                    )
                    envService.setUpEnvironment(
                        context,
                        projectDir,
                        null,
                        infrastructureServices,
                        jobDir.resolve(CONFIG_DIR)
                    )
                }

                coVerify { phase.handleKeepAlive(jobConfig) }

                coVerify(exactly = 0) {
                    runner.run(any(), any(), any())
                }
            }
        }

        val exchangeFile = jobDir.resolve(PREPARATION_EXCHANGE_FILE)
        val preparationExchange = exchangeFile.readValue<PreparationExchange>()

        with(preparationExchange) {
            environment shouldBe mapOf("SIMPLE_ENV_VAR" to "simpleValue")

            runnerConfig.packageCurationProviders shouldContainExactlyInAnyOrder pluginConfigs
            runnerConfig.enabledPackageManagers shouldContainExactlyInAnyOrder listOf("Maven")
            runnerConfig.keepAliveWorker shouldBe true
            runnerConfig.keepAlivePhases shouldContainOnly EnumSet.of(AnalyzerPhaseEnum.ANALYSIS)

            runId shouldBe ortRun.id
        }
    }

    "The analysis phase should be executed" {
        val exchangeDir = tempdir()
        val jobDir = exchangeDir.resolve("$JOB_DIR_PREFIX${analyzerJob.id}")
        val cloneDir = jobDir.resolve("analyzer-worker-${ortRun.id}-download").apply { mkdirs() }
        val prepareResult = PrepareResult(
            cloneDirectory = cloneDir,
            resolvedEnvironment = mapOf("SOME_VARIABLE" to "someValue"),
            runnerConfig = AnalyzerRunnerConfig(
                allowDynamicVersions = true,
                skipExcluded = false,
                enabledPackageManagers = listOf("Maven"),
                disabledPackageManagers = listOf("Gradle"),
                packageManagerOptions = mapOf(
                    "Maven" to PackageManagerConfiguration(
                        options = mapOf("someOption" to "someValue")
                    )
                ),
                packageCurationProviders = listOf(
                    ProviderPluginConfiguration(
                        type = "test-provider",
                        options = mapOf("option1" to "value1", "option2" to "value2"),
                        secrets = mapOf("password" to "secret-password")
                    )
            ),
                repositoryConfigPath = "test/path",
                keepAliveWorker = true,
                keepAlivePhases = EnumSet.of(AnalyzerPhaseEnum.RESULT)
            )
        )
        val prepareExchange = PreparationExchange(
            environment = prepareResult.resolvedEnvironment,
            runnerConfig = prepareResult.runnerConfig,
            runId = ortRun.id
        )
        val prepareFile = jobDir.resolve(PREPARATION_EXCHANGE_FILE)
        prepareFile.writeValue(prepareExchange)

        val userHomeDir = tempdir()
        val configDir = jobDir.resolve(CONFIG_DIR)
        val subConfigDir = configDir.resolve("sub-config").apply { mkdirs() }
        configDir.resolve("settings.xml").writeText("Maven configuration")
        subConfigDir.resolve("test.conf").writeText("Test configuration")

        val ortResult = OrtTestData.result.copy(scanner = null, advisor = null)
        val runner = mockk<AnalyzerRunner> {
            coEvery {
                run(prepareResult.cloneDirectory, prepareResult.runnerConfig, prepareResult.resolvedEnvironment)
            } answers {
                Os.userHomeDirectory.resolve("settings.xml").readText() shouldBe "Maven configuration"
                Os.userHomeDirectory.resolve("sub-config/test.conf").readText() shouldBe "Test configuration"
                ortResult
            }
        }

        mockkObject(EnvironmentForkHelper, Os, WorkerOrtConfig) {
            val workerOrtConfig = mockk<WorkerOrtConfig> {
                every { setUpOrtEnvironment() } just runs
            }
            every { WorkerOrtConfig.create() } returns workerOrtConfig
            every { EnvironmentForkHelper.setupAuthentication(any(), any()) } just runs
            every { Os.userHomeDirectory } returns userHomeDir

            val worker = AnalyzerWorker(mockk(), runner)
            val phase = spyk(AnalysisPhase())
            worker.testRun(phase, arrayOf(exchangeDir.absolutePath)) shouldBe RunResult.Ignored

            val analysisResult: OrtResult = jobDir.resolve(ORT_RESULT_FILE).readValue()
            analysisResult shouldBe ortResult

            verify {
                EnvironmentForkHelper.setupAuthentication(jobDir.resolve(AUTH_INFO_FILE), any())
                workerOrtConfig.setUpOrtEnvironment()
            }

            coVerify {
                phase.handleKeepAlive(true, EnumSet.of(AnalyzerPhaseEnum.RESULT))
            }
        }
    }

    "The analysis phase should write a sync file when it is done" {
        val exchangeDir = tempdir()
        val jobDir = exchangeDir.resolve("$JOB_DIR_PREFIX${analyzerJob.id}")
        val downloadDir = jobDir.resolve("analyzer-worker-${ortRun.id}-download")
        downloadDir.mkdirs()
        val syncFile = exchangeDir.resolve("some/sync/i-am-done.sync")
        val prepareResult = PrepareResult(
            cloneDirectory = tempdir(),
            resolvedEnvironment = emptyMap(),
            runnerConfig = AnalyzerRunnerConfig(
                allowDynamicVersions = true,
                skipExcluded = false,
                enabledPackageManagers = null,
                disabledPackageManagers = null,
                packageManagerOptions = null,
                packageCurationProviders = emptyList(),
                repositoryConfigPath = null,
                keepAliveWorker = false,
                keepAlivePhases = emptySet()
            )
        )
        val prepareExchange = PreparationExchange(
            environment = prepareResult.resolvedEnvironment,
            runnerConfig = prepareResult.runnerConfig,
            runId = ortRun.id
        )
        val prepareFile = jobDir.resolve(PREPARATION_EXCHANGE_FILE)
        prepareFile.writeValue(prepareExchange)
        jobDir.resolve(CONFIG_DIR).mkdirs() // Config directory must exist.

        val ortResult = OrtTestData.result.copy(scanner = null, advisor = null)
        val runner = mockk<AnalyzerRunner> {
            coEvery { run(any(), any(), any()) } returns ortResult
        }

        mockkObject(EnvironmentForkHelper, WorkerOrtConfig) {
            every { WorkerOrtConfig.create() } returns mockk(relaxed = true)
            every { EnvironmentForkHelper.setupAuthentication(any(), any()) } just runs

            val worker = AnalyzerWorker(mockk(), runner)
            val result = worker.testRun(
                AnalysisPhase(),
                arrayOf(exchangeDir.absolutePath, syncFile.absolutePath)
            )
            result shouldBe RunResult.Ignored

            syncFile.isFile shouldBe true
        }
    }

    "The result phase should be executed" {
        val exchangeDir = tempdir()
        val jobDir = exchangeDir.resolve("$JOB_DIR_PREFIX${analyzerJob.id}")

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { storeAnalyzerRun(any(), any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
            every { storePackageCurationAssociations(any(), any()) } just runs
            every { storeResolvedItems(any(), any()) } just runs
        }

        val context = mockWorkerContext()
        val contextFactory = mockContextFactory(context)

        val analyzerIssue = Issue(
            timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            source = "analyzer",
            message = "Analyzer issue",
            severity = Severity.ERROR
        )
        val ortResult = OrtTestData.result.copy(
            analyzer = OrtTestData.result.analyzer?.copy(
                result = OrtTestData.result.analyzer?.result!!.copy(
                    issues = mapOf(OrtIdentifier("Maven:com.example:package:1.0") to listOf(analyzerIssue))
                )
            )
        )

        jobDir.resolve(ORT_RESULT_FILE).writeValue(ortResult)

        val phase = spyk(
            ResultPhase(
                mockk(),
                ortRunService,
                contextFactory,
                mockk(relaxed = true),
                mockIssueResolutionService()
            )
        )
        val worker = AnalyzerWorker(mockk(), mockk())

        mockkTransaction {
            val result = worker.testRun(phase, arrayOf(exchangeDir.absolutePath))

            result shouldBe RunResult.FinishedWithIssues

            verify(exactly = 1) {
                ortRunService.storeAnalyzerRun(
                    withArg {
                        // There is no exact match because of different orders of elements in sets.
                        val expectedAnalyzerRun = ortResult.analyzer?.mapToModel(JOB_ID)
                        it.analyzerJobId shouldBe JOB_ID
                        it.config shouldBe expectedAnalyzerRun?.config
                        it.environment shouldBe expectedAnalyzerRun?.environment
                        it.packages.map(Package::identifier) shouldBe
                                expectedAnalyzerRun?.packages?.map(Package::identifier)
                    },
                    any()
                )
                ortRunService.storeRepositoryInformation(ortRun.id, OrtTestData.result.repository)
            }

            coVerify {
                phase.handleKeepAlive(analyzerJob.configuration)
            }
        }
    }
})

/**
 * Create a mock [WorkerContextFactory] and prepare it to return the given [context]. Use the given [refContextOpen] to
 * track when the context is open.
 */
private fun mockContextFactory(
    context: WorkerContext = mockk(),
    refContextOpen: AtomicBoolean = AtomicBoolean()
): WorkerContextFactory {
    val slot = slot<suspend (WorkerContext) -> Any>()
    return mockk {
        coEvery { withContext(analyzerJob.ortRunId, capture(slot)) } coAnswers {
            refContextOpen.set(true)
            slot.captured(context).also {
                refContextOpen.set(false)
            }
        }
    }
}

private fun mockConfigManager() = mockk<ConfigManager> {
    every { getFile(any(), any()) } returns
            File("src/test/resources/resolutions.yml").inputStream()
}

private fun mockIssueResolutionService() = mockk<IssueResolutionService> {
    every { getResolutionsForRepository(any()) } returns Ok(emptyList())
}

/**
 * Return an [AnalyzerRunnerConfig] based on the given [jobConfig] and optional [packageCurationProviderConfigs].
 */
private fun runnerConfig(
    jobConfig: AnalyzerJobConfiguration,
    packageCurationProviderConfigs: List<ProviderPluginConfiguration>? = null
): AnalyzerRunnerConfig =
    AnalyzerRunnerConfig(
        allowDynamicVersions = jobConfig.allowDynamicVersions,
        skipExcluded = jobConfig.skipExcluded,
        enabledPackageManagers = jobConfig.enabledPackageManagers,
        disabledPackageManagers = jobConfig.disabledPackageManagers,
        packageManagerOptions = jobConfig.packageManagerOptions,
        repositoryConfigPath = jobConfig.repositoryConfigPath,
        packageCurationProviders = packageCurationProviderConfigs.orEmpty(),
        keepAliveWorker = jobConfig.keepAliveWorker,
        keepAlivePhases = jobConfig.keepAlivePhases
    )

/**
 * Create a mock [WorkerContext] with default stubs for the properties commonly used in tests.
 * An optional [extraSetup] block can be used to override or add additional stubs.
 */
private fun mockWorkerContext(extraSetup: WorkerContext.() -> Unit = {}): WorkerContext =
    mockk {
        coEvery { resolveProviderPluginConfigSecrets(any()) } returns emptyList()
        every { configManager } returns mockConfigManager()
        every { ortRun } returns org.eclipse.apoapsis.ortserver.workers.analyzer.ortRun
        extraSetup()
    }
