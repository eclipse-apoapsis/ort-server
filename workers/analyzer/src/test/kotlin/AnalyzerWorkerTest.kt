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

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify

import java.io.File

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig

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
    id = 1L,
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
    ortRunId = 12,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = AnalyzerJobConfiguration(),
    status = JobStatus.CREATED
)

/**
 * Helper function to invoke this worker with test parameters.
 */
private suspend fun AnalyzerWorker.testRun(): RunResult = run(JOB_ID, TRACE_ID)

class AnalyzerWorkerTest : StringSpec({
    "A private repository should be analyzed successfully" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext> {
            coEvery { resolveProviderPluginConfigSecrets(any()) } returns mockk(relaxed = true)
        }

        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val infrastructureServices = listOf<InfrastructureService>(mockk(relaxed = true), mockk(relaxed = true))
        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServicesForRepository(context, null) } returns infrastructureServices
            coEvery { generateNetRcFile(context, infrastructureServices) } just runs
            coEvery {
                setUpEnvironment(context, projectDir, null, infrastructureServices)
            } returns ResolvedEnvironmentConfig()
        }

        val worker = AnalyzerWorker(
            mockk(),
            downloader,
            AnalyzerRunner(ConfigFactory.empty()),
            ortRunService,
            contextFactory,
            envService
        )

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                ortRunService.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID })
                ortRunService.storeRepositoryInformation(any(), any())
            }

            coVerifyOrder {
                envService.generateNetRcFile(context, infrastructureServices)
                downloader.downloadRepository(repository.url, ortRun.revision)
                envService.setUpEnvironment(context, projectDir, null, infrastructureServices)
            }
        }
    }

    "A repository without credentials should be analyzed successfully" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext> {
            coEvery { resolveProviderPluginConfigSecrets(any()) } returns mockk(relaxed = true)
        }

        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val worker = AnalyzerWorker(
            mockk(),
            downloader,
            AnalyzerRunner(ConfigFactory.empty()),
            ortRunService,
            contextFactory,
            envService
        )

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                ortRunService.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID })
            }

            coVerify(exactly = 0) {
                envService.generateNetRcFile(any(), any())
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
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext> {
            coEvery { resolveProviderPluginConfigSecrets(any()) } returns mockk(relaxed = true)
        }

        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServicesForRepository(context, envConfig) } returns emptyList()
            coEvery {
                setUpEnvironment(context, projectDir, envConfig, emptyList())
            } returns ResolvedEnvironmentConfig()
        }

        val worker = AnalyzerWorker(
            mockk(),
            downloader,
            AnalyzerRunner(ConfigFactory.empty()),
            ortRunService,
            contextFactory,
            envService
        )

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                ortRunService.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID })
            }

            coVerify {
                envService.setUpEnvironment(context, projectDir, envConfig, emptyList())
            }
        }
    }

    "AnalyzerRunner should be invoked correctly with an environment config from the job configuration" {
        val envConfig = mockk<EnvironmentConfig>()
        val jobConfig = AnalyzerJobConfiguration(environmentConfig = envConfig)
        val job = analyzerJob.copy(configuration = jobConfig)

        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns job
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns job
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val resolvedEnvConfig = mockk<ResolvedEnvironmentConfig>()
        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServicesForRepository(context, envConfig) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, envConfig, emptyList()) } returns resolvedEnvConfig
        }

        val testException = IllegalStateException("AnalyzerRunner test exception")
        val runner = mockk<AnalyzerRunner> {
            coEvery { run(context, any(), jobConfig, resolvedEnvConfig) } throws testException
        }

        val worker = AnalyzerWorker(
            mockk(),
            downloader,
            runner,
            ortRunService,
            contextFactory,
            envService
        )

        mockkTransaction {
            when (val result = worker.testRun()) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "AnalyzerRunner should be invoked correctly with an environment configuration from the repository" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val resolvedEnvConfig = mockk<ResolvedEnvironmentConfig>()
        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServicesForRepository(context, any()) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns resolvedEnvConfig
        }

        val testException = IllegalStateException("AnalyzerRunner test exception")
        val runner = mockk<AnalyzerRunner> {
            coEvery { run(context, any(), analyzerJob.configuration, resolvedEnvConfig) } throws testException
        }

        val worker = AnalyzerWorker(
            mockk(),
            downloader,
            runner,
            ortRunService,
            contextFactory,
            envService
        )

        mockkTransaction {
            when (val result = worker.testRun()) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } throws testException
        }

        val worker = AnalyzerWorker(
            mockk(),
            mockk(),
            AnalyzerRunner(ConfigFactory.empty()),
            ortRunService,
            mockk(),
            mockk()
        )

        mockkTransaction {
            when (val result = worker.testRun()) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = analyzerJob.copy(status = JobStatus.FINISHED)
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns invalidJob
        }

        val worker = AnalyzerWorker(
            mockk(),
            mockk(),
            AnalyzerRunner(ConfigFactory.empty()),
            ortRunService,
            mockk(),
            mockk()
        )

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.Ignored
        }
    }

    "A 'finished with issues' result should be returned if the analyzer run finished with issues" {
        val ortRunService = mockk<OrtRunService> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRun(any()) } returns ortRun
            every { startAnalyzerJob(any()) } returns analyzerJob
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
            every { storeResolvedPackageCurations(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext> {
            coEvery { resolveProviderPluginConfigSecrets(any()) } returns mockk(relaxed = true)
        }

        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServicesForRepository(context, null) } returns emptyList()
            coEvery { setUpEnvironment(context, projectDir, null, emptyList()) } returns ResolvedEnvironmentConfig()
        }

        val runnerMock = spyk(AnalyzerRunner(ConfigFactory.empty())) {
            coEvery { run(any(), any(), any(), any()) } answers {
                OrtTestData.result
            }
        }

        val worker = AnalyzerWorker(
            mockk(),
            downloader,
            runnerMock,
            ortRunService,
            contextFactory,
            envService
        )

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.FinishedWithIssues
        }
    }
})
