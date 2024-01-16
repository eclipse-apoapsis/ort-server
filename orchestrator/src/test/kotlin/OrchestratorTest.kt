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

import org.ossreviewtoolkit.server.dao.test.mockkTransaction
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
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.ScannerJob
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.ConfigRequest
import org.ossreviewtoolkit.server.model.orchestrator.ConfigWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.ConfigWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.ReporterWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.ReporterWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.ScannerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.WorkerError
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
import org.ossreviewtoolkit.server.transport.ConfigEndpoint
import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.EvaluatorEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.ReporterEndpoint

@Suppress("LargeClass")
class OrchestratorTest : WordSpec() {
    private val msgHeader = MessageHeader(
        token = "token",
        traceId = "traceId",
        ortRunId = 18,
    )

    private val msgHeaderWithProperties = msgHeader.copy(
        transportProperties = mapOf("kubernetes.kubeprop" to "kubeval", "rabbitmq.rabbitprop" to "rabbitval")
    )

    private val repository = Repository(
        id = 42,
        organizationId = 1L,
        productId = 1L,
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
        status = JobStatus.CREATED
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

    private val reporterJob = ReporterJob(
        id = 987,
        ortRunId = 1,
        createdAt = Clock.System.now(),
        startedAt = null,
        finishedAt = null,
        configuration = ReporterJobConfiguration(),
        status = JobStatus.CREATED
    )

    private val ortRun = OrtRun(
        id = 1,
        index = 12,
        repositoryId = repository.id,
        revision = "main",
        createdAt = Instant.fromEpochSeconds(0),
        finishedAt = null,
        jobConfigs = JobConfigurations(
            analyzerJob.configuration,
            advisorJob.configuration,
            scannerJob.configuration,
            evaluatorJob.configuration,
            reporterJob.configuration
        ),
        resolvedJobConfigs = null,
        status = OrtRunStatus.CREATED,
        labels = mapOf(
            "label key" to "label value",
            "transport.kubernetes.kubeprop" to "kubeval",
            "transport.rabbitmq.rabbitprop" to "rabbitval"
        ),
        null,
        null,
        emptyMap(),
        null,
        emptyList(),
        null,
        null
    )

    private val ortRunAnalyzerAndReporter = OrtRun(
        id = 1,
        index = 12,
        repositoryId = repository.id,
        revision = "main",
        createdAt = Instant.fromEpochSeconds(0),
        finishedAt = null,
        jobConfigs = JobConfigurations(
            analyzerJob.configuration,
            null,
            null,
            null,
            reporterJob.configuration
        ),
        resolvedJobConfigs = null,
        status = OrtRunStatus.CREATED,
        labels = ortRun.labels,
        null,
        null,
        emptyMap(),
        null,
        emptyList(),
        null,
        null
    )

    init {
        "handleCreateOrtRun" should {
            "create an ORT run in the database and notify the Config worker" {
                val analyzerJobRepository = mockk<AnalyzerJobRepository> {
                    every { create(any(), any()) } returns analyzerJob
                    every { update(any(), any(), any(), any()) } returns mockk()
                }

                val repositoryRepository = mockk<RepositoryRepository> {
                    every { this@mockk.get(any()) } returns repository
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(ConfigEndpoint, any()) } just runs
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                val createOrtRun = CreateOrtRun(ortRun)

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        repositoryRepository = repositoryRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleCreateOrtRun(msgHeader, createOrtRun)
                }

                verify(exactly = 1) {
                    // The message was sent.
                    publisher.publish(
                        to = withArg { it shouldBe ConfigEndpoint },
                        message = withArg<Message<ConfigRequest>> {
                            it.header shouldBe msgHeaderWithProperties
                            it.payload shouldBe ConfigRequest(ortRun.id)
                        }
                    )

                    // The Ort run status was set to ACTIVE.
                    ortRunRepository.update(
                        id = withArg { it shouldBe analyzerJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.ACTIVE) }
                    )
                }
            }
        }

        "handleConfigWorkerResult" should {
            "create an analyzer job" {
                val configWorkerResult = ConfigWorkerResult(ortRun.id)
                val analyzerJobRepository = mockk<AnalyzerJobRepository> {
                    every { create(any(), any()) } returns analyzerJob
                    every { update(any(), any(), any(), any()) } returns mockk()
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(AnalyzerEndpoint, any()) } just runs
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(configWorkerResult.ortRunId) } returns ortRun
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleConfigWorkerResult(msgHeader, configWorkerResult)
                }

                verify(exactly = 1) {
                    // The job was created in the database
                    analyzerJobRepository.create(
                        ortRunId = withArg { it shouldBe configWorkerResult.ortRunId },
                        configuration = withArg { it shouldBe analyzerJob.configuration }
                    )

                    // The message was sent.
                    publisher.publish(
                        to = withArg { it shouldBe AnalyzerEndpoint },
                        message = withArg<Message<AnalyzerRequest>> {
                            it.header shouldBe msgHeaderWithProperties
                            it.payload shouldBe AnalyzerRequest(analyzerJob.id)
                        }
                    )

                    // The database entry was set to SCHEDULED.
                    analyzerJobRepository.update(
                        id = withArg { it shouldBe analyzerJob.id },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                }
            }
        }

        "handleConfigWorkerError" should {
            "update the ORT run in the database" {
                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(ortRun.id, any()) } returns mockk()
                }

                val publisher = mockk<MessagePublisher>()

                val configWorkerError = ConfigWorkerError(ortRun.id)

                mockkTransaction {
                    createOrchestrator(
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleConfigWorkerError(configWorkerError)
                }

                verify(exactly = 1) {
                    ortRunRepository.update(
                        id = withArg { it shouldBe advisorJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
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

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAnalyzerWorkerResult(msgHeader, analyzerWorkerResult)
                }

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
                            it.header shouldBe msgHeaderWithProperties
                            it.payload.advisorJobId shouldBe advisorJob.id
                        }
                    )
                    advisorJobRepository.update(
                        id = withArg { it shouldBe advisorJob.id },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                    scannerJobRepository.update(
                        id = withArg { it shouldBe scannerJob.id },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                }
            }
        }

        "handleAnalyzerWorkerResultWithOnlyReporterEnabled" should {
            "update the job in the database and create a reporter job" {
                val analyzerWorkerResult = AnalyzerWorkerResult(123)

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(analyzerJob.ortRunId) } returns ortRunAnalyzerAndReporter
                }

                val analyzerJobRepository = mockk<AnalyzerJobRepository> {
                    every { get(analyzerWorkerResult.jobId) } returns analyzerJob
                    every { update(analyzerJob.id, any(), any(), any()) } returns mockk()
                }

                val reporterJobRepository = mockk<ReporterJobRepository> {
                    every { create(ortRun.id, any()) } returns reporterJob
                    every { update(reporterJob.id, any(), any(), any()) } returns mockk()
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAnalyzerWorkerResult(msgHeader, analyzerWorkerResult)
                }

                verify(exactly = 1) {
                    analyzerJobRepository.update(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                    publisher.publish(
                        to = withArg<ReporterEndpoint> { it shouldBe ReporterEndpoint },
                        message = withArg {
                            it.header shouldBe msgHeaderWithProperties
                            it.payload.reporterJobId shouldBe reporterJob.id
                        }
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

                mockkTransaction {
                    createOrchestrator(
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleScannerWorkerResult(msgHeader, scannerWorkerResult)
                }

                verify(exactly = 1) {
                    evaluatorJobRepository.create(
                        ortRunId = withArg { it shouldBe ortRun.id },
                        configuration = withArg { it shouldBe evaluatorJob.configuration }
                    )
                    publisher.publish(
                        to = withArg<EvaluatorEndpoint> { it shouldBe EvaluatorEndpoint },
                        message = withArg {
                            it.header shouldBe msgHeaderWithProperties
                            it.payload.evaluatorJobId shouldBe evaluatorJob.id
                        }
                    )
                    evaluatorJobRepository.update(
                        id = withArg { it shouldBe evaluatorJob.id },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                    scannerJobRepository.update(
                        id = withArg { it shouldBe scannerWorkerResult.jobId },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                }
            }

            "create an evaluator job if no advisor job was scheduled" {
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
                    every { getForOrtRun(ortRun.id) } returns null
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(advisorJob.ortRunId) } returns ortRun
                    every { get(scannerJob.ortRunId) } returns ortRun
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                mockkTransaction {
                    createOrchestrator(
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleScannerWorkerResult(msgHeader, scannerWorkerResult)
                }

                verify(exactly = 1) {
                    evaluatorJobRepository.create(
                        ortRunId = withArg { it shouldBe ortRun.id },
                        configuration = withArg { it shouldBe evaluatorJob.configuration }
                    )
                    publisher.publish(
                        to = withArg<EvaluatorEndpoint> { it shouldBe EvaluatorEndpoint },
                        message = withArg {
                            it.header shouldBe msgHeaderWithProperties
                            it.payload.evaluatorJobId shouldBe evaluatorJob.id
                        }
                    )
                    evaluatorJobRepository.update(
                        id = withArg { it shouldBe evaluatorJob.id },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )
                    scannerJobRepository.update(
                        id = withArg { it shouldBe scannerWorkerResult.jobId },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                }
            }

            "not create an evaluator job if the advisor job is still running" {
                val scannerWorkerResult = ScannerWorkerResult(789)
                val scannerJobRepository = mockk<ScannerJobRepository> {
                    every { get(scannerWorkerResult.jobId) } returns scannerJob
                    every { update(scannerJob.id, any(), any(), any()) } returns mockk()
                }

                val evaluatorJobRepository = mockk<EvaluatorJobRepository>()

                val advisorJobRepository = mockk<AdvisorJobRepository> {
                    every { getForOrtRun(ortRun.id) } returns advisorJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(advisorJob.ortRunId) } returns ortRun
                    every { get(scannerJob.ortRunId) } returns ortRun
                }

                val publisher = mockk<MessagePublisher>()

                mockkTransaction {
                    createOrchestrator(
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleScannerWorkerResult(msgHeader, scannerWorkerResult)
                }

                verify(exactly = 0) {
                    evaluatorJobRepository.create(any(), any())

                    ortRunRepository.update(any(), any())
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

                every { analyzerJobRepository.complete(analyzerJob.id, any(), any()) } returns analyzerJob
                every { ortRunRepository.update(any(), any()) } returns mockk()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        repositoryRepository = repositoryRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAnalyzerWorkerError(analyzerWorkerError)
                }

                verify(exactly = 1) {
                    // The job status was updated.
                    analyzerJobRepository.complete(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
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

                val evaluatorJobRepository = mockk<EvaluatorJobRepository> {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(advisorJob.ortRunId) } returns ortRun
                }

                val scannerJobRepository = mockk<ScannerJobRepository> {
                    every { getForOrtRun(ortRun.id) } returns scannerJob.copy(status = JobStatus.FINISHED)
                }

                mockkTransaction {
                    createOrchestrator(
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAdvisorWorkerResult(msgHeader, advisorWorkerResult)
                }

                verify(exactly = 1) {
                    evaluatorJobRepository.create(
                        ortRunId = withArg { it shouldBe ortRun.id },
                        configuration = withArg { it shouldBe evaluatorJob.configuration }
                    )
                    publisher.publish(
                        to = withArg<EvaluatorEndpoint> { it shouldBe EvaluatorEndpoint },
                        message = withArg {
                            it.header shouldBe msgHeaderWithProperties
                            it.payload.evaluatorJobId shouldBe evaluatorJob.id
                        }
                    )
                    evaluatorJobRepository.update(
                        id = withArg { it shouldBe evaluatorJob.id },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )

                    advisorJobRepository.update(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                }
            }

            "create an evaluator job if no scanner job was scheduled" {
                val advisorWorkerResult = AdvisorWorkerResult(advisorJob.id)

                val advisorJobRepository = mockk<AdvisorJobRepository> {
                    every { get(advisorWorkerResult.jobId) } returns advisorJob
                    every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                }

                val evaluatorJobRepository = mockk<EvaluatorJobRepository> {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(advisorJob.ortRunId) } returns ortRun
                }

                val scannerJobRepository = mockk<ScannerJobRepository> {
                    every { getForOrtRun(ortRun.id) } returns null
                }

                mockkTransaction {
                    createOrchestrator(
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAdvisorWorkerResult(msgHeader, advisorWorkerResult)
                }

                verify(exactly = 1) {
                    evaluatorJobRepository.create(
                        ortRunId = withArg { it shouldBe ortRun.id },
                        configuration = withArg { it shouldBe evaluatorJob.configuration }
                    )
                    publisher.publish(
                        to = withArg<EvaluatorEndpoint> { it shouldBe EvaluatorEndpoint },
                        message = withArg {
                            it.header shouldBe msgHeaderWithProperties
                            it.payload.evaluatorJobId shouldBe evaluatorJob.id
                        }
                    )
                    evaluatorJobRepository.update(
                        id = withArg { it shouldBe evaluatorJob.id },
                        status = withArg { it.verifyOptionalValue(JobStatus.SCHEDULED) }
                    )

                    advisorJobRepository.update(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                }
            }

            "not create an evaluator job if the scanner job is still running" {
                val advisorWorkerResult = AdvisorWorkerResult(advisorJob.id)

                val advisorJobRepository = mockk<AdvisorJobRepository> {
                    every { get(advisorWorkerResult.jobId) } returns advisorJob
                    every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                }

                val evaluatorJobRepository = mockk<EvaluatorJobRepository>()

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(advisorJob.ortRunId) } returns ortRun
                }

                val scannerJobRepository = mockk<ScannerJobRepository> {
                    every { getForOrtRun(ortRun.id) } returns scannerJob
                }

                mockkTransaction {
                    createOrchestrator(
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAdvisorWorkerResult(msgHeader, advisorWorkerResult)
                }

                verify(exactly = 0) {
                    evaluatorJobRepository.create(any(), any())

                    ortRunRepository.update(any(), any())
                }
            }
        }

        "handleAdvisorWorkerError" should {
            "update the job and the ORT run in the database " {
                val advisorJobRepository = mockk<AdvisorJobRepository> {
                    every { complete(advisorJob.id, any(), any()) } returns advisorJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(advisorJob.ortRunId, any()) } returns mockk()
                }

                val publisher = mockk<MessagePublisher>()

                val advisorWorkerError = AdvisorWorkerError(advisorJob.id)

                mockkTransaction {
                    createOrchestrator(
                        advisorJobRepository = advisorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAdvisorWorkerError(advisorWorkerError)
                }

                verify(exactly = 1) {
                    advisorJobRepository.complete(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )

                    ortRunRepository.update(
                        id = withArg { it shouldBe advisorJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }
        }

        "handleEvaluatorWorkerResult" should {
            "update the job in the database and create a reporter job" {
                val evaluatorWorkerResult = EvaluatorWorkerResult(987)
                val evaluatorJobRepository = mockk<EvaluatorJobRepository> {
                    every { get(evaluatorWorkerResult.jobId) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val reporterJobRepository = mockk<ReporterJobRepository> {
                    every { create(ortRun.id, any()) } returns reporterJob
                    every { update(reporterJob.id, any(), any(), any()) } returns mockk()
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(evaluatorJob.ortRunId) } returns ortRun
                }

                val publisher = mockk<MessagePublisher> {
                    every { publish(any<Endpoint<*>>(), any()) } just runs
                }

                mockkTransaction {
                    createOrchestrator(
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleEvaluatorWorkerResult(msgHeader, evaluatorWorkerResult)
                }

                verify(exactly = 1) {
                    reporterJobRepository.create(
                        ortRunId = withArg { it shouldBe ortRun.id },
                        configuration = withArg { it shouldBe reporterJob.configuration }
                    )
                    publisher.publish(
                        to = withArg<ReporterEndpoint> { it shouldBe ReporterEndpoint },
                        message = withArg {
                            it.header shouldBe msgHeaderWithProperties
                            it.payload.reporterJobId shouldBe reporterJob.id
                        }
                    )
                    evaluatorJobRepository.update(
                        id = withArg { it shouldBe evaluatorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                }
            }
        }

        "handleEvaluatorWorkerError" should {
            "update the job and ORT run in the database, never create a reporter job" {
                val evaluatorWorkerError = EvaluatorWorkerError(evaluatorJob.id)
                val evaluatorJobRepository = mockk<EvaluatorJobRepository> {
                    every { complete(evaluatorJob.id, any(), any()) } returns evaluatorJob
                }

                val reporterJobRepository = mockk<ReporterJobRepository> {}

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                    ).handleEvaluatorWorkerError(evaluatorWorkerError)
                }

                verify(exactly = 1) {
                    evaluatorJobRepository.complete(
                        id = withArg { it shouldBe evaluatorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe evaluatorJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }

                verify(exactly = 0) {
                    reporterJobRepository.create(any(), any())
                }
            }
        }

        "handleReporterWorkerResult" should {
            "update the job in the database and mark the ORT run as finished" {
                val reporterWorkerResult = ReporterWorkerResult(20230727143725L)
                val reporterJobRepository = mockk<ReporterJobRepository> {
                    every { get(reporterWorkerResult.jobId) } returns reporterJob
                    every { update(reporterJob.id, any(), any(), any()) } returns reporterJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(evaluatorJob.ortRunId) } returns ortRun
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                    ).handleReporterWorkerResult(
                        reporterWorkerResult
                    )
                }

                verify(exactly = 1) {
                    reporterJobRepository.update(
                        id = withArg { it shouldBe reporterJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(JobStatus.FINISHED) }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe reporterJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FINISHED) }
                    )
                }
            }
        }

        "handleReporterWorkerError" should {
            "update the job and ORT run in the database" {
                val reporterWorkerError = ReporterWorkerError(20230727145120L)
                val reporterJobRepository = mockk<ReporterJobRepository> {
                    every { complete(reporterWorkerError.jobId, any(), any()) } returns reporterJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                    ).handleReporterWorkerError(reporterWorkerError)
                }

                verify(exactly = 1) {
                    reporterJobRepository.complete(
                        id = withArg { it shouldBe reporterWorkerError.jobId },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe reporterJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }

                verify(exactly = 0) {
                    reporterJobRepository.create(any(), any())
                }
            }
        }

        "handleWorkerError" should {
            "handle a failed analyzer job" {
                val workerError = WorkerError("analyzer")
                val analyzerJobRepository = mockk<AnalyzerJobRepository> {
                    every { getForOrtRun(msgHeader.ortRunId) } returns analyzerJob
                    every { tryComplete(analyzerJob.id, any(), any()) } returns analyzerJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        ortRunRepository = ortRunRepository,
                    ).handleWorkerError(msgHeader.ortRunId, workerError)
                }

                verify(exactly = 1) {
                    analyzerJobRepository.tryComplete(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }

            "handle a failed advisor job" {
                val workerError = WorkerError("advisor")
                val advisorJobRepository = mockk<AdvisorJobRepository> {
                    every { getForOrtRun(msgHeader.ortRunId) } returns advisorJob
                    every { tryComplete(advisorJob.id, any(), any()) } returns advisorJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        advisorJobRepository = advisorJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader.ortRunId, workerError)
                }

                verify(exactly = 1) {
                    advisorJobRepository.tryComplete(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }

            "handle a failed scanner job" {
                val workerError = WorkerError("scanner")
                val scannerJobRepository = mockk<ScannerJobRepository> {
                    every { getForOrtRun(msgHeader.ortRunId) } returns scannerJob
                    every { tryComplete(scannerJob.id, any(), any()) } returns scannerJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        scannerJobRepository = scannerJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader.ortRunId, workerError)
                }

                verify(exactly = 1) {
                    scannerJobRepository.tryComplete(
                        id = withArg { it shouldBe scannerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }

            "handle a failed evaluator job" {
                val workerError = WorkerError("evaluator")
                val evaluatorJobRepository = mockk<EvaluatorJobRepository> {
                    every { getForOrtRun(msgHeader.ortRunId) } returns evaluatorJob
                    every { tryComplete(evaluatorJob.id, any(), any()) } returns evaluatorJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader.ortRunId, workerError)
                }

                verify(exactly = 1) {
                    evaluatorJobRepository.tryComplete(
                        id = withArg { it shouldBe evaluatorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }

            "handle a failed reporter job" {
                val workerError = WorkerError("reporter")
                val reporterJobRepository = mockk<ReporterJobRepository> {
                    every { getForOrtRun(msgHeader.ortRunId) } returns reporterJob
                    every { tryComplete(reporterJob.id, any(), any()) } returns reporterJob
                }

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader.ortRunId, workerError)
                }

                verify(exactly = 1) {
                    reporterJobRepository.tryComplete(
                        id = withArg { it shouldBe reporterJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }

            "deal with a job that has already been completed" {
                val workerError = WorkerError("reporter")
                val reporterJobRepository = mockk<ReporterJobRepository> {
                    every { getForOrtRun(msgHeader.ortRunId) } returns reporterJob
                    every { tryComplete(reporterJob.id, any(), any()) } returns null
                }

                val ortRunRepository = mockk<OrtRunRepository>()

                mockkTransaction {
                    createOrchestrator(
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader.ortRunId, workerError)
                }

                verify(exactly = 0) {
                    ortRunRepository.update(any(), any(), any(), any())
                }
            }

            "handle a failed config job" {
                val workerError = WorkerError(ConfigEndpoint.configPrefix)

                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader.ortRunId, workerError)
                }

                verify(exactly = 1) {
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }
        }
    }
}

/**
 * Helper function to create an [Orchestrator] instance with default parameters.
 */
private fun createOrchestrator(
    analyzerJobRepository: AnalyzerJobRepository = mockk(),
    advisorJobRepository: AdvisorJobRepository = mockk(),
    scannerJobRepository: ScannerJobRepository = mockk(),
    evaluatorJobRepository: EvaluatorJobRepository = mockk(),
    reporterJobRepository: ReporterJobRepository = mockk(),
    repositoryRepository: RepositoryRepository = mockk(),
    ortRunRepository: OrtRunRepository = mockk(),
    publisher: MessagePublisher = mockk()
): Orchestrator =
    Orchestrator(
        mockk(),
        analyzerJobRepository,
        advisorJobRepository,
        scannerJobRepository,
        evaluatorJobRepository,
        reporterJobRepository,
        repositoryRepository,
        ortRunRepository,
        publisher
    )

private fun OptionalValue<Instant?>.verifyTimeRange(allowedDiff: Duration) {
    shouldBeTypeOf<OptionalValue.Present<Instant>>()
    val now = Clock.System.now()

    value.shouldBeBetween(now - allowedDiff, now)
}

private fun Instant.verifyTimeRange(allowedDiff: Duration) {
    val now = Clock.System.now()

    this.shouldBeBetween(now - allowedDiff, now)
}

private fun <T> OptionalValue<T>.verifyOptionalValue(expectedValue: T) {
    shouldBeTypeOf<OptionalValue.Present<T>>()

    value shouldBe expectedValue
}
