/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.analyzer

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import java.io.File

import kotlin.test.fail

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.EnvironmentConfig
import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory
import org.ossreviewtoolkit.server.workers.common.env.EnvironmentService

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

private val ortRun = OrtRun(
    id = 1L,
    index = 1L,
    repositoryId = repository.id,
    revision = "main",
    createdAt = Clock.System.now(),
    config = JobConfigurations(),
    resolvedConfig = JobConfigurations(),
    status = OrtRunStatus.ACTIVE,
    labels = emptyMap(),
    vcsId = 1L,
    vcsProcessedId = 1L,
    nestedRepositoryIds = emptyMap(),
    repositoryConfigId = 1L,
    issues = emptyList(),
    configContext = "context",
    resolvedConfigContext = "context"
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
private fun AnalyzerWorker.testRun(): RunResult = runBlocking { run(JOB_ID, TRACE_ID) }

class AnalyzerWorkerTest : StringSpec({
    "A private repository should be analyzed successfully" {
        val dao = mockk<AnalyzerWorkerDao> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getOrtRun(any()) } returns ortRun
            every { getRepository(any()) } returns repository
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val infrastructureService = mockk<InfrastructureService>()
        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServiceForRepository(context) } returns infrastructureService
            coEvery { generateNetRcFile(context, listOf(infrastructureService)) } just runs
            coEvery { setUpEnvironment(context, projectDir, infrastructureService) } returns mockk()
        }

        val worker = AnalyzerWorker(mockk(), downloader, AnalyzerRunner(), dao, contextFactory, envService)

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                dao.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID })
                dao.storeRepositoryInformation(any(), any())
            }

            coVerifyOrder {
                envService.generateNetRcFile(context, listOf(infrastructureService))
                downloader.downloadRepository(repository.url, ortRun.revision)
                envService.setUpEnvironment(context, projectDir, infrastructureService)
            }
        }
    }

    "A repository without credentials should be analyzed successfully" {
        val dao = mockk<AnalyzerWorkerDao> {
            every { getAnalyzerJob(any()) } returns analyzerJob
            every { getOrtRun(any()) } returns ortRun
            every { getRepository(any()) } returns repository
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and
            // the repository is not cloned.
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServiceForRepository(context) } returns null
            coEvery { setUpEnvironment(context, projectDir, null) } returns mockk()
        }

        val worker = AnalyzerWorker(mockk(), downloader, AnalyzerRunner(), dao, contextFactory, envService)

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                dao.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID })
            }

            coVerify(exactly = 0) {
                envService.generateNetRcFile(any(), any())
            }

            coVerify {
                envService.setUpEnvironment(context, projectDir, null)
            }
        }
    }

    "An environment configuration in the job configuration should be supported" {
        val envConfig = mockk<EnvironmentConfig>()
        val jobConfig = AnalyzerJobConfiguration(environmentConfig = envConfig)
        val job = analyzerJob.copy(configuration = jobConfig)

        val dao = mockk<AnalyzerWorkerDao> {
            every { getAnalyzerJob(any()) } returns job
            every { getOrtRun(any()) } returns ortRun
            every { getRepository(any()) } returns repository
            every { storeAnalyzerRun(any()) } just runs
            every { storeRepositoryInformation(any(), any()) } just runs
        }

        val downloader = mockk<AnalyzerDownloader> {
            every { downloadRepository(any(), any()) } returns projectDir
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockk<WorkerContextFactory> {
            every { createContext(analyzerJob.ortRunId) } returns context
        }

        val envService = mockk<EnvironmentService> {
            every { findInfrastructureServiceForRepository(context) } returns null
            coEvery { setUpEnvironment(context, envConfig, null) } returns mockk()
        }

        val worker = AnalyzerWorker(mockk(), downloader, AnalyzerRunner(), dao, contextFactory, envService)

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.Success

            verify(exactly = 1) {
                dao.storeAnalyzerRun(withArg { it.analyzerJobId shouldBe JOB_ID })
            }

            coVerify {
                envService.setUpEnvironment(context, envConfig, null)
            }
        }
    }

    "A failure result should be returned in case of an error" {
        val testException = IllegalStateException("Test exception")
        val dao = mockk<AnalyzerWorkerDao> {
            every { getAnalyzerJob(any()) } throws testException
        }

        val worker = AnalyzerWorker(mockk(), mockk(), AnalyzerRunner(), dao, mockk(), mockk())

        mockkTransaction {
            when (val result = worker.testRun()) {
                is RunResult.Failed -> result.error shouldBe testException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = analyzerJob.copy(status = JobStatus.FINISHED)
        val dao = mockk<AnalyzerWorkerDao> {
            every { getAnalyzerJob(any()) } returns invalidJob
        }

        val worker = AnalyzerWorker(mockk(), mockk(), AnalyzerRunner(), dao, mockk(), mockk())

        mockkTransaction {
            val result = worker.testRun()

            result shouldBe RunResult.Ignored
        }
    }
})
