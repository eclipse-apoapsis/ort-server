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

package org.eclipse.apoapsis.ortserver.orchestrator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeSingleton
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

import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.LostSchedule
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint

@Suppress("LargeClass")
class OrchestratorTest : WordSpec() {
    private val msgHeader = MessageHeader(
        traceId = "traceId",
        ortRunId = RUN_ID,
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

    private val notifierJob = NotifierJob(
        id = 654,
        ortRunId = 1,
        createdAt = Clock.System.now(),
        startedAt = null,
        finishedAt = null,
        configuration = NotifierJobConfiguration(),
        status = JobStatus.CREATED
    )

    private val ortRun = OrtRun(
        id = RUN_ID,
        index = 12,
        organizationId = 1L,
        productId = 1L,
        repositoryId = repository.id,
        revision = "main",
        path = null,
        createdAt = Instant.fromEpochSeconds(0),
        finishedAt = null,
        jobConfigs = JobConfigurations(
            analyzerJob.configuration,
            advisorJob.configuration,
            scannerJob.configuration,
            evaluatorJob.configuration,
            reporterJob.configuration,
            notifierJob.configuration
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
        null,
        traceId = msgHeader.traceId
    )

    private val ortRunAnalyzerAndReporter = OrtRun(
        id = RUN_ID,
        index = 12,
        organizationId = 1L,
        productId = 1L,
        repositoryId = repository.id,
        revision = "main",
        path = null,
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
        null,
        traceId = null
    )

    init {
        "handleCreateOrtRun" should {
            "create an ORT run in the database and notify the Config worker" {
                val analyzerJobRepository: AnalyzerJobRepository = createRepository {
                    every { create(any(), any()) } returns analyzerJob
                    every { update(any(), any(), any(), any()) } returns mockk()
                }

                val repositoryRepository = mockk<RepositoryRepository> {
                    every { this@mockk.get(any()) } returns repository
                }

                val publisher = createMessagePublisher()

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
                val analyzerJobRepository: AnalyzerJobRepository = createRepository {
                    every { create(any(), any()) } returns analyzerJob
                    every { get(analyzerJob.id) } returns analyzerJob
                    every { update(any(), any(), any(), any()) } returns mockk()
                }

                val publisher = createMessagePublisher()

                val ortRunRepository = createOrtRunRepository(expectUpdate = false) {
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

            "not create a reporter job before the workers that must run before are finished" {
                val configWorkerResult = ConfigWorkerResult(ortRun.id)
                val analyzerJobRepository: AnalyzerJobRepository = createRepository {
                    every { create(any(), any()) } returns analyzerJob
                    every { get(analyzerJob.id) } returns analyzerJob
                    every { update(any(), any(), any(), any()) } returns mockk()
                }

                val reporterJobRepository: ReporterJobRepository = createRepository()

                val ortRunRepository = createOrtRunRepository(expectUpdate = false) {
                    every { get(ortRun.id) } returns ortRunAnalyzerAndReporter
                    every { update(any(), any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = createMessagePublisher()
                    ).handleConfigWorkerResult(msgHeader, configWorkerResult)
                }

                verify(exactly = 0) {
                    reporterJobRepository.create(any(), any())
                }
            }
        }

        "handleConfigWorkerError" should {
            "update the ORT run in the database" {
                val ortRunRepository = mockk<OrtRunRepository> {
                    every { update(ortRun.id, issues = any()) } returns mockk()
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
                        id = withArg { it shouldBe configWorkerError.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) },
                        issues = withArg {
                            verifyIssues(it) { verifyWorkerErrorIssues(Severity.ERROR, ConfigEndpoint.configPrefix) }
                        }
                    )
                }
            }
        }

        "handleAnalyzerWorkerResult" should {
            "update the job in the database and create an advisor and scanner job" {
                val analyzerWorkerResult = AnalyzerWorkerResult(123)

                val scannerJobRepository: ScannerJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns scannerJob
                    every { get(scannerJob.id) } returns scannerJob
                    every { update(scannerJob.id, any(), any(), any()) } returns mockk()
                }

                val advisorJobRepository: AdvisorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns advisorJob
                    every { get(advisorJob.id) } returns advisorJob
                    every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED) {
                    every { get(analyzerWorkerResult.jobId) } returns analyzerJob
                    every { complete(analyzerJob.id, any(), any()) } returns mockk()
                }

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

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
                    analyzerJobRepository.complete(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
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

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED) {
                    every { get(analyzerWorkerResult.jobId) } returns analyzerJob
                    every { complete(analyzerJob.id, any(), any()) } returns mockk()
                }

                val reporterJobRepository = expectReporterJob()

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAnalyzerWorkerResult(msgHeader, analyzerWorkerResult)
                }

                verifyReporterJobCreated(reporterJobRepository, publisher)
                verify(exactly = 1) {
                    analyzerJobRepository.complete(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                }
            }
        }

        "handleScannerWorkerResult" should {
            "update the job in the database and create an evaluator job" {
                val scannerWorkerResult = ScannerWorkerResult(789)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED) {
                    every { get(scannerWorkerResult.jobId) } returns scannerJob
                    every { complete(scannerJob.id, any(), any()) } returns mockk()
                }

                val evaluatorJobRepository: EvaluatorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { get(evaluatorJob.id) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
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
                    scannerJobRepository.complete(
                        id = withArg { it shouldBe scannerWorkerResult.jobId },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                }
            }

            "create an evaluator job if no advisor job was scheduled" {
                val scannerWorkerResult = ScannerWorkerResult(scannerJob.id)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED, scannerJob.id) {
                    every { get(scannerWorkerResult.jobId) } returns scannerJob
                    every { complete(scannerJob.id, any(), any()) } returns mockk()
                }

                val evaluatorJobRepository: EvaluatorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { get(evaluatorJob.id) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository()

                val ortRunWithoutAdvisor = ortRun.copy(
                    jobConfigs = ortRun.jobConfigs.copy(advisor = null)
                )
                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(RUN_ID) } returns ortRunWithoutAdvisor
                }

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
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
                    scannerJobRepository.complete(
                        id = withArg { it shouldBe scannerWorkerResult.jobId },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                }
            }

            "not create an evaluator job if the advisor job is still running" {
                val scannerWorkerResult = ScannerWorkerResult(789)
                val scannerJobRepository: ScannerJobRepository = createRepository {
                    every { get(scannerWorkerResult.jobId) } returns scannerJob
                    every { update(scannerJob.id, any(), any(), any()) } returns mockk()
                }

                val evaluatorJobRepository = mockk<EvaluatorJobRepository>()

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.RUNNING)

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = mockk<MessagePublisher>()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
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

        "handleScannerWorkerResultWithIssues" should {
            "update the job in the database and create an evaluator job" {
                val scannerWorkerResultWithIssues = ScannerWorkerResult(789, true)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED_WITH_ISSUES) {
                    every { get(scannerWorkerResultWithIssues.jobId) } returns scannerJob
                    every { complete(scannerJob.id, any(), any()) } returns mockk()
                }

                val evaluatorJobRepository: EvaluatorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { get(evaluatorJob.id) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleScannerWorkerResult(msgHeader, scannerWorkerResultWithIssues)
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
                    scannerJobRepository.complete(
                        id = withArg { it shouldBe scannerWorkerResultWithIssues.jobId },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED_WITH_ISSUES }
                    )
                }
            }
        }

        "handleAnalyzerWorkerError" should {
            "update the job and the ORT run in the database" {
                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FAILED) {
                    every { get(analyzerJob.id) } returns analyzerJob
                    every { complete(analyzerJob.id, any(), any()) } returns analyzerJob
                }
                val repositoryRepository = mockk<RepositoryRepository>()
                val ortRunRepository = createOrtRunRepository {
                    every { update(ortRun.id, issues = any()) } returns mockk()
                }
                val reporterJobRepository = expectReporterJob()
                val publisher = createMessagePublisher()

                val analyzerWorkerError = AnalyzerWorkerError(123)

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        repositoryRepository = repositoryRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAnalyzerWorkerError(msgHeader, analyzerWorkerError)
                }

                verify(exactly = 1) {
                    // The job status was updated.
                    analyzerJobRepository.complete(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )

                    ortRunRepository.update(
                        id = withArg { it shouldBe analyzerJob.ortRunId },
                        issues = withArg {
                            verifyIssues(it) { verifyWorkerErrorIssues(Severity.ERROR, AnalyzerEndpoint.configPrefix) }
                        }
                    )
                }
                verify(exactly = 0) {
                    ortRunRepository.update(
                        id = withArg { it shouldBe analyzerJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
                verifyReporterJobCreated(reporterJobRepository, publisher)
            }
        }

        "handleAnalyzerWorkerResultWithIssues" should {
            "update the job in the database and create an advisor and scanner job" {
                val analyzerWorkerResultWithIssues = AnalyzerWorkerResult(123, true)

                val scannerJobRepository: ScannerJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns scannerJob
                    every { get(scannerJob.id) } returns scannerJob
                    every { update(scannerJob.id, any(), any(), any()) } returns mockk()
                }

                val advisorJobRepository: AdvisorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns advisorJob
                    every { get(advisorJob.id) } returns advisorJob
                    every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED_WITH_ISSUES) {
                    every { get(analyzerWorkerResultWithIssues.jobId) } returns analyzerJob
                    every { complete(analyzerJob.id, any(), any()) } returns mockk()
                }

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAnalyzerWorkerResult(msgHeader, analyzerWorkerResultWithIssues)
                }

                verify(exactly = 1) {
                    analyzerJobRepository.complete(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED_WITH_ISSUES }
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

        "handleAdvisorWorkerResult" should {
            "update the job in the database" {
                val advisorWorkerResult = AdvisorWorkerResult(advisorJob.id)

                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED) {
                    every { get(advisorWorkerResult.jobId) } returns advisorJob
                    every { complete(advisorJob.id, any(), any()) } returns mockk()
                }

                val evaluatorJobRepository: EvaluatorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { get(evaluatorJob.id) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val publisher = createMessagePublisher()

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
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

                    advisorJobRepository.complete(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                }
            }

            "create an evaluator job if no scanner job was scheduled" {
                val advisorWorkerResult = AdvisorWorkerResult(advisorJob.id)

                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED, advisorJob.id) {
                    every { get(advisorWorkerResult.jobId) } returns advisorJob
                    every { complete(advisorJob.id, any(), any()) } returns advisorJob
                }

                val evaluatorJobRepository: EvaluatorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { get(evaluatorJob.id) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)

                val publisher = createMessagePublisher()

                val ortRunWithoutScanner = ortRun.copy(
                    jobConfigs = ortRun.jobConfigs.copy(scanner = null)
                )
                val ortRunRepository = mockk<OrtRunRepository> {
                    every { get(advisorJob.ortRunId) } returns ortRunWithoutScanner
                }

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
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

                    advisorJobRepository.complete(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                }
            }

            "not create an evaluator job if the scanner job is still running" {
                val advisorWorkerResult = AdvisorWorkerResult(advisorJob.id)

                val advisorJobRepository: AdvisorJobRepository = createRepository {
                    every { get(advisorWorkerResult.jobId) } returns advisorJob
                    every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository()

                val publisher = createMessagePublisher()

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val scannerJobRepository = mockk<ScannerJobRepository> {
                    every { getForOrtRun(ortRun.id) } returns scannerJob
                }

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
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
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FAILED) {
                    every { get(advisorJob.id) } returns advisorJob
                    every { complete(advisorJob.id, any(), any()) } returns advisorJob
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)

                val reporterJobRepository = expectReporterJob()
                val ortRunRepository = createOrtRunRepository {
                    every { update(ortRun.id, issues = any()) } returns mockk()
                }
                val publisher = createMessagePublisher()

                val advisorWorkerError = AdvisorWorkerError(advisorJob.id)

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAdvisorWorkerError(msgHeader, advisorWorkerError)
                }

                verify(exactly = 1) {
                    advisorJobRepository.complete(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )

                    ortRunRepository.update(
                        id = withArg { it shouldBe advisorJob.ortRunId },
                        issues = withArg {
                            verifyIssues(it) { verifyWorkerErrorIssues(Severity.ERROR, AdvisorEndpoint.configPrefix) }
                        }
                    )
                }
                verify(exactly = 0) {
                    ortRunRepository.update(any(), any())
                }
                verifyReporterJobCreated(reporterJobRepository, publisher)
            }
        }

        "handleAdvisorWorkerResultWithIssues" should {
            "update the job in the database" {
                val advisorWorkerResultWithIssues = AdvisorWorkerResult(advisorJob.id, true)

                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED_WITH_ISSUES) {
                    every { get(advisorWorkerResultWithIssues.jobId) } returns advisorJob
                    every { complete(advisorJob.id, any(), any()) } returns mockk()
                }

                val evaluatorJobRepository: EvaluatorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns evaluatorJob
                    every { get(evaluatorJob.id) } returns evaluatorJob
                    every { update(evaluatorJob.id, any(), any(), any()) } returns mockk()
                }

                val publisher = createMessagePublisher()

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleAdvisorWorkerResult(msgHeader, advisorWorkerResultWithIssues)
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

                    advisorJobRepository.complete(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED_WITH_ISSUES }
                    )
                }
            }
        }

        "handleEvaluatorWorkerResult" should {
            "update the job in the database and create a reporter job" {
                val evaluatorWorkerResult = EvaluatorWorkerResult(evaluatorJob.id)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED) {
                    every { get(evaluatorWorkerResult.jobId) } returns evaluatorJob
                    every { complete(evaluatorJob.id, any(), any()) } returns mockk()
                }

                val reporterJobRepository = expectReporterJob()

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleEvaluatorWorkerResult(msgHeader, evaluatorWorkerResult)
                }

                verifyReporterJobCreated(reporterJobRepository, publisher)
                verify(exactly = 1) {
                    evaluatorJobRepository.complete(
                        id = withArg { it shouldBe evaluatorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                }
            }
        }

        "handleEvaluatorWorkerError" should {
            "update the job and ORT run in the database, never create a reporter job" {
                val evaluatorWorkerError = EvaluatorWorkerError(evaluatorJob.id)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FAILED) {
                    every { get(evaluatorJob.id) } returns evaluatorJob
                    every { complete(evaluatorJob.id, any(), any()) } returns evaluatorJob
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val reporterJobRepository = expectReporterJob()
                val ortRunRepository = createOrtRunRepository {
                    every { update(ortRun.id, issues = any()) } returns mockk()
                }
                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleEvaluatorWorkerError(msgHeader, evaluatorWorkerError)
                }

                verify(exactly = 1) {
                    evaluatorJobRepository.complete(
                        id = withArg { it shouldBe evaluatorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )

                    ortRunRepository.update(
                        id = withArg { it shouldBe evaluatorJob.ortRunId },
                        issues = withArg {
                            verifyIssues(it) { verifyWorkerErrorIssues(Severity.ERROR, EvaluatorEndpoint.configPrefix) }
                        }
                    )
                }

                verify(exactly = 0) {
                    ortRunRepository.update(
                        id = withArg { it shouldBe evaluatorJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }

                verifyReporterJobCreated(reporterJobRepository, publisher)
            }
        }

        "handleEvaluatorWorkerResultWithIssues" should {
            "update the job in the database and create a reporter job" {
                val evaluatorWorkerResultWithIssues = EvaluatorWorkerResult(evaluatorJob.id, true)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED_WITH_ISSUES) {
                    every { get(evaluatorWorkerResultWithIssues.jobId) } returns evaluatorJob
                    every { complete(evaluatorJob.id, any(), any()) } returns mockk()
                }

                val reporterJobRepository = expectReporterJob()

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleEvaluatorWorkerResult(msgHeader, evaluatorWorkerResultWithIssues)
                }

                verifyReporterJobCreated(reporterJobRepository, publisher)
                verify(exactly = 1) {
                    evaluatorJobRepository.complete(
                        id = withArg { it shouldBe evaluatorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED_WITH_ISSUES }
                    )
                }
            }
        }

        "handleReporterWorkerResult" should {
            "update the job in the database and create a notifier job" {
                val reporterWorkerResult = ReporterWorkerResult(reporterJob.id)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED) {
                    every { get(reporterWorkerResult.jobId) } returns reporterJob
                    every { complete(reporterJob.id, any(), any()) } returns reporterJob
                }

                val notifierJobRepository = expectNotifierJob()

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleReporterWorkerResult(
                        msgHeader,
                        reporterWorkerResult
                    )
                }

                verify(exactly = 1) {
                    reporterJobRepository.complete(
                        id = withArg { it shouldBe reporterJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                }

                verifyNotifierJobCreated(notifierJobRepository, publisher)
            }

            "start a notifier job even if the ORT run is in failure state" {
                val reporterWorkerResult = ReporterWorkerResult(reporterJob.id)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED) {
                    every { get(reporterWorkerResult.jobId) } returns reporterJob
                    every { complete(reporterJob.id, any(), any()) } returns reporterJob
                }

                val notifierJobRepository: NotifierJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns notifierJob
                    every { get(notifierJob.id) } returns notifierJob
                    every { update(notifierJob.id, any(), any(), any()) } returns notifierJob
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FAILED)

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleReporterWorkerResult(
                        msgHeader,
                        reporterWorkerResult
                    )
                }

                verify(exactly = 1) {
                    reporterJobRepository.complete(
                        id = withArg { it shouldBe reporterJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                }
            }
        }

        "handleReporterWorkerError" should {
            "update the job and ORT run in the database" {
                val reporterWorkerError = ReporterWorkerError(reporterJob.id)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FAILED) {
                    every { get(reporterJob.id) } returns reporterJob
                    every { complete(reporterWorkerError.jobId, any(), any()) } returns reporterJob
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val notifierJobRepository = expectNotifierJob()

                val ortRunRepository = createOrtRunRepository {
                    every { update(ortRun.id, issues = any()) } returns mockk()
                }

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleReporterWorkerError(msgHeader, reporterWorkerError)
                }

                verify(exactly = 1) {
                    reporterJobRepository.complete(
                        id = withArg { it shouldBe reporterWorkerError.jobId },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )

                    ortRunRepository.update(
                        id = withArg { it shouldBe reporterJob.ortRunId },
                        issues = withArg {
                            verifyIssues(it) { verifyWorkerErrorIssues(Severity.ERROR, ReporterEndpoint.configPrefix) }
                        }
                    )
                }

                verify(exactly = 0) {
                    reporterJobRepository.create(any(), any())
                    ortRunRepository.update(any(), any())
                }

                verifyNotifierJobCreated(notifierJobRepository, publisher)
            }
        }

        "handleReporterWorkerResultWithIssues" should {
            "update the job in the database and create a notifier job" {
                val reporterWorkerResultWithIssues = ReporterWorkerResult(reporterJob.id, true)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED_WITH_ISSUES) {
                    every { get(reporterWorkerResultWithIssues.jobId) } returns reporterJob
                    every { complete(reporterJob.id, any(), any()) } returns reporterJob
                }

                val notifierJobRepository = expectNotifierJob()

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleReporterWorkerResult(
                        msgHeader,
                        reporterWorkerResultWithIssues
                    )
                }

                verify(exactly = 1) {
                    reporterJobRepository.complete(
                        id = withArg { it shouldBe reporterJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED_WITH_ISSUES }
                    )
                }

                verifyNotifierJobCreated(notifierJobRepository, publisher)
            }
        }

        "handleNotifierWorkerResult" should {
            "update the job in the database and mark the ORT run as finished" {
                val notifierWorkerResult = NotifierWorkerResult(notifierJob.id)
                val notifierJobRepository: NotifierJobRepository =
                    createRepository(JobStatus.FINISHED, notifierJob.id) {
                        every { get(notifierJob.id) } returns notifierJob
                        every { complete(notifierJob.id, any(), any()) } returns notifierJob
                        every { deleteMailRecipients(notifierJob.id) } returns notifierJob
                    }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleNotifierWorkerResult(msgHeader, notifierWorkerResult)
                }

                verify(exactly = 1) {
                    notifierJobRepository.complete(
                        id = withArg { it shouldBe notifierJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe notifierJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FINISHED) }
                    )
                }
            }

            "update the job in the database and mark the ORT run as finished with issues if some issues occurred" {
                val notifierWorkerResult = NotifierWorkerResult(notifierJob.id)
                val notifierJobRepository: NotifierJobRepository =
                    createRepository(JobStatus.FINISHED, notifierJob.id) {
                        every { get(notifierJob.id) } returns notifierJob
                        every { complete(notifierJob.id, any(), any()) } returns notifierJob
                        every { deleteMailRecipients(notifierJob.id) } returns notifierJob
                    }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED_WITH_ISSUES)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleNotifierWorkerResult(msgHeader, notifierWorkerResult)
                }

                verify(exactly = 1) {
                    notifierJobRepository.complete(
                        id = withArg { it shouldBe notifierJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FINISHED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe notifierJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FINISHED_WITH_ISSUES) }
                    )
                }
            }

            "delete the recipient email addresses" {
                val notifierWorkerResult = NotifierWorkerResult(notifierJob.id)
                val notifierJobRepository: NotifierJobRepository =
                    createRepository(JobStatus.FINISHED, notifierJob.id) {
                        every { get(notifierJob.id) } returns notifierJob
                        every { complete(notifierJob.id, any(), any()) } returns notifierJob
                        every { deleteMailRecipients(notifierJob.id) } returns notifierJob
                    }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleNotifierWorkerResult(msgHeader, notifierWorkerResult)
                }

                verify(exactly = 1) {
                    notifierJobRepository.deleteMailRecipients(notifierJob.id)
                }
            }
        }

        "handleNotifierWorkerError" should {
            "update the job and ORT run in the database" {
                val notifierWorkerError = NotifierWorkerError(notifierJob.id)
                val notifierJobRepository: NotifierJobRepository = createRepository(JobStatus.FAILED, notifierJob.id) {
                    every { get(notifierJob.id) } returns notifierJob
                    every { complete(notifierJob.id, any(), any()) } returns notifierJob
                    every { deleteMailRecipients(notifierJob.id) } returns notifierJob
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository {
                    every { update(ortRun.id, any(), any(), issues = any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleNotifierWorkerError(msgHeader, notifierWorkerError)
                }

                verify(exactly = 1) {
                    notifierJobRepository.complete(
                        id = withArg { it shouldBe notifierJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe notifierJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                    ortRunRepository.update(
                        id = withArg { it shouldBe notifierJob.ortRunId },
                        issues = withArg {
                            verifyIssues(it) { verifyWorkerErrorIssues(Severity.ERROR, NotifierEndpoint.configPrefix) }
                        }
                    )
                }
            }

            "delete the recipient email addresses" {
                val notifierWorkerError = NotifierWorkerError(notifierJob.id)
                val notifierJobRepository: NotifierJobRepository = createRepository(JobStatus.FAILED, notifierJob.id) {
                    every { get(notifierJob.id) } returns notifierJob
                    every { complete(notifierJob.id, any(), any()) } returns notifierJob
                    every { deleteMailRecipients(notifierJob.id) } returns notifierJob
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository {
                    every { update(ortRun.id, any(), any(), issues = any()) } returns mockk()
                }

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleNotifierWorkerError(msgHeader, notifierWorkerError)
                }

                verify(exactly = 1) {
                    // Verify the deletion of email addresses from the ORT run parameters.
                    ortRunRepository.update(
                        id = withArg { it shouldBe notifierJob.ortRunId },
                        jobConfigs = withArg {
                            it.verifyOptionalValue(
                                ortRun.jobConfigs.copy(
                                    notifier = ortRun.jobConfigs.notifier?.copy(
                                        mail = ortRun.jobConfigs.notifier?.mail?.copy(
                                            recipientAddresses = emptyList()
                                        )
                                    )
                                )
                            )
                        }
                    )

                    // Verify the deletion of the email addresses from the notifier jobs table.
                    notifierJobRepository.deleteMailRecipients(notifierJob.id)

                    ortRunRepository.update(
                        id = withArg { it shouldBe notifierJob.ortRunId },
                        issues = withArg {
                            verifyIssues(it) { verifyWorkerErrorIssues(Severity.ERROR, NotifierEndpoint.configPrefix) }
                        }
                    )
                }
            }
        }

        "handleWorkerError" should {
            "handle a failed analyzer job" {
                val workerError = WorkerError("analyzer")
                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FAILED, analyzerJob.id) {
                    every {
                        tryComplete(analyzerJob.id, any(), any())
                    } returns createJob<AnalyzerJob>(JobStatus.FAILED, analyzerJob.id)
                }

                val reporterJobRepository = expectReporterJob()
                val ortRunRepository = createOrtRunRepository()
                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleWorkerError(msgHeader, workerError)
                }

                verify(exactly = 1) {
                    analyzerJobRepository.tryComplete(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                }
                verify(exactly = 0) {
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
                verifyReporterJobCreated(reporterJobRepository, publisher)
            }

            "handle a failed advisor job" {
                val workerError = WorkerError("advisor")
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FAILED, advisorJob.id) {
                    every {
                        tryComplete(advisorJob.id, any(), any())
                    } returns createJob<AdvisorJob>(JobStatus.FAILED, advisorJob.id)
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)

                val reporterJobRepository = expectReporterJob()
                val ortRunRepository = createOrtRunRepository()
                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleWorkerError(msgHeader, workerError)
                }

                verify(exactly = 1) {
                    advisorJobRepository.tryComplete(
                        id = withArg { it shouldBe advisorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                }
                verifyReporterJobCreated(reporterJobRepository, publisher)
            }

            "handle a failed scanner job" {
                val workerError = WorkerError("scanner")
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FAILED, scannerJob.id) {
                    every {
                        tryComplete(scannerJob.id, any(), any())
                    } returns createJob(JobStatus.FAILED, scannerJob.id)
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)

                val reporterJobRepository = expectReporterJob()
                val ortRunRepository = createOrtRunRepository()
                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleWorkerError(msgHeader, workerError)
                }

                verify(exactly = 1) {
                    scannerJobRepository.tryComplete(
                        id = withArg { it shouldBe scannerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                }
                verifyReporterJobCreated(reporterJobRepository, publisher)
            }

            "handle a failed evaluator job" {
                val workerError = WorkerError("evaluator")
                val evaluatorJobRepository: EvaluatorJobRepository =
                    createRepository(JobStatus.FAILED, evaluatorJob.id) {
                        every {
                            tryComplete(evaluatorJob.id, any(), any())
                        } returns createJob(JobStatus.FAILED, evaluatorJob.id)
                    }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)

                val reporterJobRepository = expectReporterJob()
                val ortRunRepository = createOrtRunRepository()
                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleWorkerError(msgHeader, workerError)
                }

                verify(exactly = 1) {
                    evaluatorJobRepository.tryComplete(
                        id = withArg { it shouldBe evaluatorJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                }
                verifyReporterJobCreated(reporterJobRepository, publisher)
            }

            "handle a failed reporter job" {
                val workerError = WorkerError("reporter")
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FAILED, reporterJob.id) {
                    every {
                        tryComplete(reporterJob.id, any(), any())
                    } returns createJob(JobStatus.FAILED, reporterJob.id)
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val notifierJobRepository = expectNotifierJob()

                val ortRunRepository = createOrtRunRepository()
                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleWorkerError(msgHeader, workerError)
                }

                verify(exactly = 1) {
                    reporterJobRepository.tryComplete(
                        id = withArg { it shouldBe reporterJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                }

                verify(exactly = 0) {
                    ortRunRepository.update(RUN_ID, any())
                }

                verifyNotifierJobCreated(notifierJobRepository, publisher)
            }

            "handle a failed notifier job" {
                val workerError = WorkerError("notifier")
                val notifierJobRepository: NotifierJobRepository = createRepository(JobStatus.FAILED, notifierJob.id) {
                    every {
                        tryComplete(notifierJob.id, any(), any())
                    } returns createJob(JobStatus.FAILED, notifierJob.id)
                    every { deleteMailRecipients(notifierJob.id) } returns notifierJob
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = createOrtRunRepository()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader, workerError)
                }

                verify(exactly = 1) {
                    notifierJobRepository.tryComplete(
                        id = withArg { it shouldBe notifierJob.id },
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
                val reporterJobRepository: ReporterJobRepository =
                    createRepository(JobStatus.FINISHED, reporterJob.id) {
                        every { tryComplete(reporterJob.id, any(), any()) } returns null
                    }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)

                val ortRunRepository = mockk<OrtRunRepository>()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader, workerError)
                }

                verify(exactly = 0) {
                    ortRunRepository.update(any(), any(), any(), any())
                }
            }

            "handle a failed config job" {
                val workerError = WorkerError(ConfigEndpoint.configPrefix)

                val ortRunRepository = createOrtRunRepository()

                mockkTransaction {
                    createOrchestrator(
                        ortRunRepository = ortRunRepository
                    ).handleWorkerError(msgHeader, workerError)
                }

                verify(exactly = 1) {
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
            }

            "use the trace ID from the ORT run if none is available in the error message" {
                val workerError = WorkerError("analyzer")
                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FAILED, analyzerJob.id) {
                    every {
                        tryComplete(analyzerJob.id, any(), any())
                    } returns createJob<AnalyzerJob>(JobStatus.FAILED, analyzerJob.id)
                }

                val reporterJobRepository = expectReporterJob()
                val ortRunRepository = createOrtRunRepository()
                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleWorkerError(msgHeader.copy(traceId = ""), workerError)
                }

                verify(exactly = 1) {
                    analyzerJobRepository.tryComplete(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it shouldBe JobStatus.FAILED }
                    )
                }
                verify(exactly = 0) {
                    ortRunRepository.update(
                        id = withArg { it shouldBe msgHeader.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FAILED) }
                    )
                }
                verifyReporterJobCreated(reporterJobRepository, publisher)
            }
        }

        "handleLostSchedule" should {
            "trigger the Config worker if no worker jobs have been scheduled yet" {
                val ortRunRepository = createOrtRunRepository(expectUpdate = false)
                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleLostSchedule(msgHeader, LostSchedule(RUN_ID))
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
                }
            }

            "schedule the next worker jobs according to the job state of the affected run" {
                val scannerJobRepository: ScannerJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns scannerJob
                    every { get(scannerJob.id) } returns scannerJob
                    every { update(scannerJob.id, any(), any(), any()) } returns mockk()
                }

                val advisorJobRepository: AdvisorJobRepository = createRepository {
                    every { create(ortRun.id, any()) } returns advisorJob
                    every { get(advisorJob.id) } returns advisorJob
                    every { update(advisorJob.id, any(), any(), any()) } returns mockk()
                }

                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED) {
                    every { get(analyzerJob.id) } returns analyzerJob
                    every { complete(analyzerJob.id, any(), any()) } returns mockk()
                }

                val ortRunRepository = createOrtRunRepository(expectUpdate = false)

                val publisher = createMessagePublisher()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        ortRunRepository = ortRunRepository,
                        publisher = publisher
                    ).handleLostSchedule(msgHeader, LostSchedule(RUN_ID))
                }

                verify(exactly = 1) {
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

            "complete the ORT run if all jobs have run" {
                val analyzerJobRepository: AnalyzerJobRepository = createRepository(JobStatus.FINISHED)
                val advisorJobRepository: AdvisorJobRepository = createRepository(JobStatus.FINISHED)
                val scannerJobRepository: ScannerJobRepository = createRepository(JobStatus.FINISHED)
                val evaluatorJobRepository: EvaluatorJobRepository = createRepository(JobStatus.FINISHED)
                val reporterJobRepository: ReporterJobRepository = createRepository(JobStatus.FINISHED)

                val notifierJobRepository: NotifierJobRepository =
                    createRepository(JobStatus.FINISHED, notifierJob.id) {
                        every { deleteMailRecipients(notifierJob.id) } returns notifierJob
                    }

                val ortRunRepository = createOrtRunRepository()

                mockkTransaction {
                    createOrchestrator(
                        analyzerJobRepository = analyzerJobRepository,
                        advisorJobRepository = advisorJobRepository,
                        scannerJobRepository = scannerJobRepository,
                        evaluatorJobRepository = evaluatorJobRepository,
                        reporterJobRepository = reporterJobRepository,
                        notifierJobRepository = notifierJobRepository,
                        ortRunRepository = ortRunRepository
                    ).handleLostSchedule(msgHeader, LostSchedule(RUN_ID))
                }

                verify(exactly = 1) {
                    ortRunRepository.update(
                        id = withArg { it shouldBe notifierJob.ortRunId },
                        status = withArg { it.verifyOptionalValue(OrtRunStatus.FINISHED) }
                    )
                }
            }
        }
    }

    /**
     * Create a mock for a [ReporterJobRepository] that is prepared to expect a schedule of a reporter job.
     */
    private fun expectReporterJob(): ReporterJobRepository {
        val reporterJobRepository: ReporterJobRepository = createRepository {
            every { create(ortRun.id, any()) } returns reporterJob
            every { get(reporterJob.id) } returns reporterJob
            every { update(reporterJob.id, any(), any(), any()) } returns mockk()
        }
        return reporterJobRepository
    }

    /**
     * Verify that a job for the Reporter worker has been created and scheduled using the given
     * [reporterJobRepository] and [publisher].
     */
    private fun verifyReporterJobCreated(reporterJobRepository: ReporterJobRepository, publisher: MessagePublisher) {
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
        }
    }

    /**
     * Create a mock for a [NotifierJobRepository] that is prepared to expect a schedule of a notifier job.
     */
    private fun expectNotifierJob(): NotifierJobRepository {
        val notifierJobRepository: NotifierJobRepository = createRepository {
            every { create(ortRun.id, any()) } returns notifierJob
            every { get(notifierJob.id) } returns notifierJob
            every { update(notifierJob.id, any(), any(), any()) } returns mockk()
        }
        return notifierJobRepository
    }

    /**
     * Verify that a job for the Notifier worker has been created and scheduled using the given
     * [notifierJobRepository] and [publisher].
     */
    private fun verifyNotifierJobCreated(notifierJobRepository: NotifierJobRepository, publisher: MessagePublisher) {
        verify(exactly = 1) {
            notifierJobRepository.create(
                ortRunId = withArg { it shouldBe ortRun.id },
                configuration = withArg { it shouldBe notifierJob.configuration }
            )
            publisher.publish(
                to = withArg<NotifierEndpoint> { it shouldBe NotifierEndpoint },
                message = withArg {
                    it.header shouldBe msgHeaderWithProperties
                    it.payload.notifierJobId shouldBe notifierJob.id
                }
            )
        }
    }

    /**
     * Create a mock for an [OrtRunRepository] and prepare it with some default expectations. The mock returns the
     * default [ortRun] for the test run ID. If [expectUpdate] is *true*, it expects an update of its status
     * (including a configuration update). With the given [block], additional expectations can be defined.
     */
    private fun createOrtRunRepository(
        expectUpdate: Boolean = true,
        block: OrtRunRepository.() -> Unit = {}
    ): OrtRunRepository {
        return mockk<OrtRunRepository> {
            every { get(RUN_ID) } returns ortRun
            if (expectUpdate) {
                every { update(any(), any(), any()) } returns mockk<OrtRun>()
            }
            block()
        }
    }
}

/** The ID of the test ORT run. */
private const val RUN_ID = 1L

/** The default ID of a mock job. */
private const val JOB_ID = 42L

/**
 * Helper function to create an [Orchestrator] instance with default parameters.
 */
@Suppress("LongParameterList")
private fun createOrchestrator(
    analyzerJobRepository: AnalyzerJobRepository = createRepository(),
    advisorJobRepository: AdvisorJobRepository = createRepository(),
    scannerJobRepository: ScannerJobRepository = createRepository(),
    evaluatorJobRepository: EvaluatorJobRepository = createRepository(),
    reporterJobRepository: ReporterJobRepository = createRepository(),
    notifierJobRepository: NotifierJobRepository = createRepository(),
    repositoryRepository: RepositoryRepository = mockk(),
    ortRunRepository: OrtRunRepository = mockk(),
    publisher: MessagePublisher = mockk()
): Orchestrator =
    Orchestrator(
        mockk(),
        WorkerJobRepositories(
            analyzerJobRepository,
            advisorJobRepository,
            scannerJobRepository,
            evaluatorJobRepository,
            reporterJobRepository,
            notifierJobRepository
        ),
        repositoryRepository,
        ortRunRepository,
        publisher
    )

/**
 * Return a mock for a [WorkerJobRepository] of the given type. The mock is prepared to expect a query for a job for
 * the current [OrtRun]. Here, it returns a mock that reports the given [jobId] and [status] or *null* if no status is
 * specified. Further expectations can be defined in the given [block].
 */
private inline fun <reified J : WorkerJob, reified R : WorkerJobRepository<J>> createRepository(
    status: JobStatus? = null,
    jobId: Long = JOB_ID,
    block: R.() -> Unit = {}
): R {
    val job = status?.let { createJob<J>(it, jobId) }
    val repository = mockk<R> {
        every { getForOrtRun(RUN_ID) } returns job
    }
    repository.block()
    return repository
}

/**
 * Return a mock for a [WorkerJob] of the given type that is prepared to report the given [jobId] and [status].
 */
private inline fun <reified J : WorkerJob> createJob(status: JobStatus, jobId: Long): J =
    mockk {
        every { id } returns jobId
        every { this@mockk.status } returns status
        every { ortRunId } returns RUN_ID
    }

/**
 * Create a mock for a [MessagePublisher] and prepare it to expect the publication of messages to all possible
 * endpoints.
 */
private fun createMessagePublisher() = mockk<MessagePublisher> {
    every { publish(any<Endpoint<*>>(), any()) } just runs
}

private fun Instant.verifyTimeRange(allowedDiff: Duration) {
    val now = Clock.System.now()

    this.shouldBeBetween(now - allowedDiff, now)
}

private fun <T> OptionalValue<T>.verifyOptionalValue(expectedValue: T) {
    shouldBeTypeOf<OptionalValue.Present<T>>()

    value shouldBe expectedValue
}

fun verifyIssues(
    issues: OptionalValue<Collection<Issue>>,
    validate: Collection<Issue>.() -> Unit
) {
    val issuesList = issues.shouldBeTypeOf<OptionalValue.Present<Collection<Issue>>>().value
    issuesList.validate()
}

fun Collection<Issue>.verifyWorkerErrorIssues(severity: Severity, source: String) {
    shouldBeSingleton { issue ->
        issue.severity shouldBe severity
        issue.source shouldBe source
    }
}
