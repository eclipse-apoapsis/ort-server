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

package org.ossreviewtoolkit.server.orchestrator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.kotlinx.datetime.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.ScannerJob
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.model.orchestrator.ScannerWorkerResult
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterJobRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.transport.AdvisorEndpoint
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.EvaluatorEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessagePublisher

class OrchestratorTest : WordSpec() {
    private val msgHeader = MessageHeader(
        token = "token",
        traceId = "traceId"
    )

    private val repository = Repository(
        id = 42,
        type = RepositoryType.GIT,
        url = "https://example.com/git/repository.git"
    )

    private val analyzerJob = AnalyzerJob(
        id = 123,
        ortRunId = 1,
        createdAt = Clock.System.now(),
        startedAt = null,
        finishedAt = null,
        configuration = AnalyzerJobConfiguration(),
        status = JobStatus.CREATED,
        repositoryUrl = repository.url,
        repositoryRevision = "main"
    )

    private val advisorJob = AdvisorJob(
        id = 456,
        ortRunId = 1,
        createdAt = Clock.System.now(),
        startedAt = null,
        finishedAt = null,
        configuration = AdvisorJobConfiguration(),
        status = JobStatus.CREATED
    )

    private val advisorJobFinished = AdvisorJob(
        id = 456,
        ortRunId = 1,
        createdAt = Clock.System.now(),
        startedAt = Clock.System.now(),
        finishedAt = Clock.System.now(),
        configuration = AdvisorJobConfiguration(),
        status = JobStatus.FINISHED
    )

    private val scannerJob = ScannerJob(
        id = 789,
        ortRunId = 1,
        createdAt = Clock.System.now(),
        startedAt = null,
        finishedAt = null,
        configuration = ScannerJobConfiguration(),
        status = JobStatus.CREATED
    )

    private val evaluatorJob = EvaluatorJob(
        id = 765,
        ortRunId = 1,
        createdAt = Clock.System.now(),
        startedAt = null,
        finishedAt = null,
        configuration = EvaluatorJobConfiguration(),
        status = JobStatus.CREATED
    )

    private val ortRun = OrtRun(
        id = 1,
        index = 12,
        repositoryId = repository.id,
        revision = "main",
        createdAt = Instant.fromEpochSeconds(0),
        jobs = JobConfigurations(
            analyzerJob.configuration,
            advisorJob.configuration,
            scannerJob.configuration,
            evaluatorJob.configuration
        ),
        status = OrtRunStatus.CREATED
    )

    init {
        "handleCreateOrtRun" should {
            "create an ORT run in the database and notify the analyzer" {
                val analyzerJobRepository = mockk<AnalyzerJobRepository> {
                    every { create(any(), any()) } returns analyzerJob
                    every { update(any(), any(), any(), any()) } returns mockk()
                }

                val repositoryRepository = mockk<RepositoryRepository> {
                    every { this@mockk.get(any()) } returns repository
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(AnalyzerEndpoint, any()) } just runs
                }

                val createOrtRun = CreateOrtRun(ortRun)

                Orchestrator(
                    analyzerJobRepository,
                    mockk(),
                    mockk(),
                    mockk(),
                    mockk(),
                    repositoryRepository,
                    mockk(),
                    publisher
                ).handleCreateOrtRun(msgHeader, createOrtRun)

                verify(exactly = 1) {
                    // The job was created in the database
                    analyzerJobRepository.create(
                        ortRunId = withArg { it shouldBe createOrtRun.ortRun.id },
                        configuration = withArg { it shouldBe analyzerJob.configuration }
                    )

                    // The message was sent.
                    publisher.publish(
                        to = withArg { it shouldBe AnalyzerEndpoint },
                        message = withArg<Message<AnalyzerRequest>> {
                            it.header shouldBe msgHeader
                            it.payload shouldBe AnalyzerRequest(analyzerJob.id)
                        }
                    )

                    // The database entry was set to SCHEDULED.
                    analyzerJobRepository.update(
                        id = withArg { it shouldBe analyzerJob.id },
                        startedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                }
            }
        }

        "handleAnalyzerWorkerResult" should {
            "update the job in the database and create an advisor and scanner job" {
                val analyzerWorkerResult = AnalyzerWorkerResult(123)

                val scannerJobRepository = mockk<ScannerJobRepository> {
                    every { create(ortRun.id, any()) } returns scannerJob
                    every { update(scannerJob.id, any(), any(), any()) } returns mockk()
                }

                val evaluatorJobRepository = mockk<EvaluatorJobRepository> {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val advisorJobRepository = mockk<AdvisorJobRepository> {
                    every { create(ortRun.id, any()) } returns advisorJob
                    every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository = mockk<AnalyzerJobRepository> {
                    every { get(analyzerWorkerResult.jobId) } returns analyzerJob
                    every { update(analyzerJob.id, any(), any(), any()) } returns mockk()
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(analyzerJob.ortRunId) } returns ortRun
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                Orchestrator(
                    analyzerJobRepository,
                    advisorJobRepository,
                    scannerJobRepository,
                    evaluatorJobRepository,
                    mockk(),
                    mockk(),
                    ortRunRepository,
                    publisher
                ).handleAnalyzerWorkerResult(MessageHeader(msgHeader.token, msgHeader.traceId), analyzerWorkerResult)

                verify(exactly = 1) {
                    analyzerJobRepository.update(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                    advisorJobRepository.create(
                        ortRunId = withArg { it shouldBe ortRun.id },
                        configuration = withArg { it shouldBe advisorJob.configuration }
                    )
                    scannerJobRepository.create(
                        ortRunId = withArg { it shouldBe ortRun.id },
                        configuration = withArg { it shouldBe scannerJob.configuration }
                    )
                    publisher.publish(
                        to = withArg<AdvisorEndpoint> { it shouldBe AdvisorEndpoint },
                        message = withArg {
                            it.header shouldBe msgHeader
                            it.payload.advisorJobId shouldBe advisorJob.id
                        }
                    )
                    advisorJobRepository.update(
                        id = withArg { it shouldBe advisorJob.id },
                        startedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                    scannerJobRepository.update(
                        id = withArg { it shouldBe scannerJob.id },
                        startedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                }
            }
        }

        "handleScannerWorkerResult" should {
            "update the job in the database and create an evaluator job" {
                val scannerWorkerResult = ScannerWorkerResult(789)
                val scannerJobRepository = mockk<ScannerJobRepository> {
                    every { get(scannerWorkerResult.jobId) } returns scannerJob
                    every { update(scannerJob.id, any(), any(), any()) } returns mockk()
                }

                val evaluatorJobRepository = mockk<EvaluatorJobRepository> {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val advisorJobRepository = mockk<AdvisorJobRepository> {
                    every { getForOrtRun(ortRun.id) } returns advisorJobFinished
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(advisorJob.ortRunId) } returns ortRun
                    every { get(scannerJob.ortRunId) } returns ortRun
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                Orchestrator(
                    mockk(),
                    advisorJobRepository,
                    scannerJobRepository,
                    evaluatorJobRepository,
                    mockk(),
                    mockk(),
                    ortRunRepository,
                    publisher
                ).handleScannerWorkerResult(MessageHeader(msgHeader.token, msgHeader.traceId), scannerWorkerResult)

                verify(exactly = 1) {
                    evaluatorJobRepository.create(
                        ortRunId = withArg { it shouldBe ortRun.id },
                        configuration = withArg { it shouldBe evaluatorJob.configuration }
                    )
                    publisher.publish(
                        to = withArg<EvaluatorEndpoint> { it shouldBe EvaluatorEndpoint },
                        message = withArg {
                            it.header shouldBe msgHeader
                            it.payload.evaluatorJobId shouldBe evaluatorJob.id
                        }
                    )
                    evaluatorJobRepository.update(
                        id = withArg { it shouldBe evaluatorJob.id },
                        startedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                    scannerJobRepository.update(
                        id = withArg { it shouldBe scannerWorkerResult.jobId },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                }
            }
        }

        "handleAnalyzerWorkerError" should {
            "update the job and the ORT run in the database" {
                val advisorJobRepository = mockk<AdvisorJobRepository>()
                val analyzerJobRepository = mockk<AnalyzerJobRepository>()
                val scannerJobRepository = mockk<ScannerJobRepository>()
                val evaluatorJobRepository = mockk<EvaluatorJobRepository>()
                val reporterJobRepository = mockk<ReporterJobRepository>()
                val repositoryRepository = mockk<RepositoryRepository>()
                val ortRunRepository = mockk<OrtRunRepository>()
                val publisher = mockk<MessagePublisher>()

                val analyzerWorkerError = AnalyzerWorkerError(123)

                every { analyzerJobRepository.get(analyzerWorkerError.jobId) } returns analyzerJob
                every { analyzerJobRepository.update(analyzerJob.id, any(), any(), any()) } returns mockk()
                every { ortRunRepository.update(any(), any()) } returns mockk()

                Orchestrator(
                    analyzerJobRepository,
                    advisorJobRepository,
                    scannerJobRepository,
                    evaluatorJobRepository,
                    reporterJobRepository,
                    repositoryRepository,
                    ortRunRepository,
                    publisher
                ).handleAnalyzerWorkerError(analyzerWorkerError)

                verify(exactly = 1) {
                    // The job status was updated.
                    analyzerJobRepository.update(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FAILED) }
                    )

                    // The ORT run status was updated.
                    ortRunRepository.update(
                        id = withArg { it shouldBe analyzerJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }
        }

        "handleAdvisorWorkerResult" should {
            "update the job in the database" {
                val advisorWorkerResult = AdvisorWorkerResult(advisorJob.id)

                 val advisorJobRepository = mockk<AdvisorJobRepository> {
                     every { get(advisorWorkerResult.jobId) } returns advisorJob
                     every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                 }

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                Orchestrator(mockk(), advisorJobRepository, mockk(), mockk(), mockk(), mockk(), mockk(), publisher)
                    .handleAdvisorWorkerResult(advisorWorkerResult)

                verify(exactly = 1) {
                    advisorJobRepository.update(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                }
            }
        }

        "handleAdvisorWorkerError" should {
            "update the job and the ORT run in the database " {
                val advisorJobRepository = mockk<AdvisorJobRepository> {
                    every { get(advisorJob.id) } returns advisorJob
                    every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(advisorJob.ortRunId, any()) } returns mockk()
                }

                val publisher = mockk<MessagePublisher>()

                val advisorWorkerError = AdvisorWorkerError(advisorJob.id)

                Orchestrator(
                    mockk(),
                    advisorJobRepository,
                    mockk(),
                    mockk(),
                    mockk(),
                    mockk(),
                    ortRunRepository,
                    publisher
                ).handleAdvisorWorkerError(advisorWorkerError)

                verify(exactly = 1) {
                    advisorJobRepository.update(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FAILED) }
                    )

                    ortRunRepository.update(
                        id = withArg { it shouldBe advisorJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }
        }
    }
}

private fun OptionalValue<Instant?>.verifyTimeRange(allowedDiff: Duration) {
    shouldBeTypeOf<OptionalValue.Present<Instant>>()
    val now = Clock.System.now()

    value.shouldBeBetween(now - allowedDiff, now)
}

private fun <T> OptionalValue<T>.verifyOptionalValue(expectedValue: T) {
    shouldBeTypeOf<OptionalValue.Present<T>>()

    value shouldBe expectedValue
}
