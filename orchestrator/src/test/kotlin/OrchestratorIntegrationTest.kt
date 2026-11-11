/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.LostSchedule
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository
import org.eclipse.apoapsis.ortserver.model.util.asPresent
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
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME
import org.eclipse.apoapsis.ortserver.utils.test.Integration

/**
 * Integration tests for the [Orchestrator] that use a real database and the testing transport. They pass messages
 * to the [Orchestrator] and verify the resulting state in the database and the messages sent to the workers.
 */
class OrchestratorIntegrationTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    lateinit var fixtures: Fixtures
    lateinit var orchestrator: Orchestrator

    beforeEach {
        fixtures = dbExtension.fixtures
        orchestrator = createOrchestrator(dbExtension, fixtures)
        MessageSenderFactoryForTesting.reset()
    }

    "CreateOrtRun messages" should {
        "set the run to ACTIVE and trigger the Config worker" {
            val run = fixtures.createOrtRun()

            orchestrator.handle(run, CreateOrtRun(run))

            fixtures.runStatus(run) shouldBe OrtRunStatus.ACTIVE
            sentPayloads().shouldContainExactlyInAnyOrder(ConfigRequest(run.id))
        }

        "be ignored if the repository does not exist" {
            val run = fixtures.createOrtRun()

            orchestrator.handle(run, CreateOrtRun(run.copy(repositoryId = run.repositoryId + 1)))

            fixtures.runStatus(run) shouldBe OrtRunStatus.CREATED
            sentPayloads().shouldBeEmpty()
        }
    }

    "ConfigWorkerResult messages" should {
        "create and schedule an analyzer job" {
            val run = fixtures.createOrtRun()

            orchestrator.handle(run, ConfigWorkerResult(run.id))

            val analyzerJob = fixtures.analyzerJobRepository.getForOrtRun(run.id).shouldNotBeNull()
            analyzerJob.status shouldBe JobStatus.SCHEDULED
            analyzerJob.configuration shouldBe fixtures.jobConfigurations.analyzer
            sentPayloads().shouldContainExactlyInAnyOrder(AnalyzerRequest(analyzerJob.id))
        }

        "be ignored if the run does not exist" {
            val run = fixtures.createOrtRun()

            orchestrator.handle(run, ConfigWorkerResult(run.id + 1))

            fixtures.analyzerJobRepository.getForOrtRun(run.id).shouldBeNull()
            sentPayloads().shouldBeEmpty()
        }
    }

    "ConfigWorkerError messages" should {
        "fail the run with an issue for the Config worker" {
            val run = fixtures.createOrtRun()

            orchestrator.handle(run, ConfigWorkerError(run.id, "Config error"))

            val updatedRun = fixtures.ortRunRepository.get(run.id).shouldNotBeNull()
            updatedRun.status shouldBe OrtRunStatus.FAILED
            updatedRun.issues.shouldBeSingleton { issue ->
                issue.source shouldBe ConfigEndpoint.configPrefix
                issue.message shouldBe "Config error"
                issue.severity shouldBe Severity.ERROR
            }
            sentPayloads().shouldBeEmpty()
        }
    }

    "Worker result messages" should {
        jobEndpoints.forEach { endpoint ->
            "complete the ${endpoint.configPrefix} job" {
                val run = fixtures.createOrtRun()
                val job = fixtures.createJobsUntil(run, endpoint, JobStatus.RUNNING)

                orchestrator.handle(run, workerResult(endpoint, job.id, hasIssues = false))

                fixtures.jobStatus(run, endpoint) shouldBe JobStatus.FINISHED
            }
        }

        (jobEndpoints - NotifierEndpoint).forEach { endpoint ->
            "complete the ${endpoint.configPrefix} job with issues if the result has issues" {
                val run = fixtures.createOrtRun()
                val job = fixtures.createJobsUntil(run, endpoint, JobStatus.RUNNING)

                orchestrator.handle(run, workerResult(endpoint, job.id, hasIssues = true))

                fixtures.jobStatus(run, endpoint) shouldBe JobStatus.FINISHED_WITH_ISSUES
            }
        }

        "complete the job and schedule the advisor and scanner after the analyzer" {
            val run = fixtures.createOrtRun()
            val analyzerJob = fixtures.createJob(run, AnalyzerEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, AnalyzerWorkerResult(analyzerJob.id))

            fixtures.jobStatus(run, AnalyzerEndpoint) shouldBe JobStatus.FINISHED
            fixtures.jobStatus(run, AdvisorEndpoint) shouldBe JobStatus.SCHEDULED
            fixtures.jobStatus(run, ScannerEndpoint) shouldBe JobStatus.SCHEDULED
            sentPayloads().shouldContainExactlyInAnyOrder(
                AdvisorRequest(fixtures.jobId(run, AdvisorEndpoint)),
                ScannerRequest(fixtures.jobId(run, ScannerEndpoint))
            )
        }

        "not schedule the evaluator while the advisor is still running" {
            val run = fixtures.createOrtRun()
            fixtures.createJob(run, AnalyzerEndpoint, JobStatus.FINISHED)
            fixtures.createJob(run, AdvisorEndpoint, JobStatus.RUNNING)
            val scannerJob = fixtures.createJob(run, ScannerEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, ScannerWorkerResult(scannerJob.id))

            fixtures.jobStatus(run, ScannerEndpoint) shouldBe JobStatus.FINISHED
            fixtures.jobStatus(run, EvaluatorEndpoint).shouldBeNull()
            fixtures.runStatus(run) shouldBe OrtRunStatus.CREATED
            sentPayloads().shouldBeEmpty()
        }

        "schedule the evaluator after the scanner if no advisor is configured" {
            val run = fixtures.createOrtRun(jobConfigurations = fixtures.jobConfigurations.copy(advisor = null))
            fixtures.createJob(run, AnalyzerEndpoint, JobStatus.FINISHED)
            val scannerJob = fixtures.createJob(run, ScannerEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, ScannerWorkerResult(scannerJob.id))

            sentPayloads().shouldContainExactlyInAnyOrder(EvaluatorRequest(fixtures.jobId(run, EvaluatorEndpoint)))
        }

        "schedule the reporter after the analyzer if only these workers are configured" {
            val run = fixtures.createOrtRun(
                jobConfigurations = JobConfigurations(
                    analyzer = fixtures.jobConfigurations.analyzer,
                    reporter = fixtures.jobConfigurations.reporter
                )
            )
            val analyzerJob = fixtures.createJob(run, AnalyzerEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, AnalyzerWorkerResult(analyzerJob.id))

            sentPayloads().shouldContainExactlyInAnyOrder(ReporterRequest(fixtures.jobId(run, ReporterEndpoint)))
        }

        "schedule the notifier after the reporter" {
            val run = fixtures.createOrtRun()
            val reporterJob = fixtures.createJobsUntil(run, ReporterEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, ReporterWorkerResult(reporterJob.id))

            sentPayloads().shouldContainExactlyInAnyOrder(NotifierRequest(fixtures.jobId(run, NotifierEndpoint)))
        }

        "finish the run and delete the mail recipients after the last job" {
            val run = fixtures.createOrtRun()
            val notifierJob = fixtures.createJobsUntil(run, NotifierEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, NotifierWorkerResult(notifierJob.id))

            fixtures.runStatus(run) shouldBe OrtRunStatus.FINISHED
            fixtures.notifierJobRepository.get(notifierJob.id).shouldNotBeNull {
                status shouldBe JobStatus.FINISHED
                configuration.recipientAddresses.shouldBeEmpty()
            }
            sentPayloads().shouldBeEmpty()
        }

        "finish the run with issues if a job finished with issues" {
            val run = fixtures.createOrtRun()
            fixtures.createJob(run, ScannerEndpoint, JobStatus.FINISHED_WITH_ISSUES)
            val notifierJob = fixtures.createJobsUntil(run, NotifierEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, NotifierWorkerResult(notifierJob.id))

            fixtures.runStatus(run) shouldBe OrtRunStatus.FINISHED_WITH_ISSUES
        }
    }

    "Worker error messages" should {
        jobEndpoints.forEach { endpoint ->
            "fail the ${endpoint.configPrefix} job and add an issue to the run" {
                val run = fixtures.createOrtRun()
                val job = fixtures.createJobsUntil(run, endpoint, JobStatus.RUNNING)

                orchestrator.handle(run, workerError(endpoint, job.id, "Worker error"))

                fixtures.repository(endpoint).get(job.id).shouldNotBeNull {
                    status shouldBe JobStatus.FAILED
                    errorMessage shouldBe "Worker error"
                }
                fixtures.ortRunRepository.get(run.id).shouldNotBeNull().issues.shouldBeSingleton { issue ->
                    issue.source shouldBe endpoint.configPrefix
                    issue.message shouldBe "Worker error"
                }
            }
        }

        "fail the job, add an issue to the run, and schedule the reporter" {
            val run = fixtures.createOrtRun()
            val evaluatorJob = fixtures.createJobsUntil(run, EvaluatorEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, EvaluatorWorkerError(evaluatorJob.id, "Evaluator error"))

            fixtures.evaluatorJobRepository.get(evaluatorJob.id).shouldNotBeNull {
                status shouldBe JobStatus.FAILED
                errorMessage shouldBe "Evaluator error"
            }
            fixtures.ortRunRepository.get(run.id).shouldNotBeNull().issues.shouldBeSingleton { issue ->
                issue.source shouldBe EvaluatorEndpoint.configPrefix
                issue.message shouldBe "Evaluator error"
            }
            fixtures.runStatus(run) shouldBe OrtRunStatus.CREATED
            sentPayloads().shouldContainExactlyInAnyOrder(ReporterRequest(fixtures.jobId(run, ReporterEndpoint)))
        }

        "not schedule the reporter before the scanner finished if the advisor failed" {
            val run = fixtures.createOrtRun()
            fixtures.createJob(run, AnalyzerEndpoint, JobStatus.FINISHED)
            val advisorJob = fixtures.createJob(run, AdvisorEndpoint, JobStatus.RUNNING)
            fixtures.createJob(run, ScannerEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, AdvisorWorkerError(advisorJob.id))

            fixtures.jobStatus(run, AdvisorEndpoint) shouldBe JobStatus.FAILED
            sentPayloads().shouldBeEmpty()
        }

        "schedule the notifier even if the reporter failed" {
            val run = fixtures.createOrtRun()
            val reporterJob = fixtures.createJobsUntil(run, ReporterEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, ReporterWorkerError(reporterJob.id))

            sentPayloads().shouldContainExactlyInAnyOrder(NotifierRequest(fixtures.jobId(run, NotifierEndpoint)))
        }

        "fail the run and delete the mail recipients after the last job" {
            val run = fixtures.createOrtRun()
            val notifierJob = fixtures.createJobsUntil(run, NotifierEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, NotifierWorkerError(notifierJob.id))

            fixtures.runStatus(run) shouldBe OrtRunStatus.FAILED
            fixtures.notifierJobRepository.get(notifierJob.id).shouldNotBeNull()
                .configuration.recipientAddresses.shouldBeEmpty()
        }
    }

    "WorkerError messages" should {
        jobEndpoints.forEach { endpoint ->
            "fail the ${endpoint.configPrefix} job" {
                val run = fixtures.createOrtRun()
                fixtures.createJobsUntil(run, endpoint, JobStatus.RUNNING)

                orchestrator.handle(run, WorkerError(endpoint.configPrefix))

                fixtures.jobStatus(run, endpoint) shouldBe JobStatus.FAILED
            }
        }

        "fail the job of the worker and schedule the next jobs" {
            val run = fixtures.createOrtRun()
            fixtures.createJob(run, AnalyzerEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, WorkerError(AnalyzerEndpoint.configPrefix))

            fixtures.jobStatus(run, AnalyzerEndpoint) shouldBe JobStatus.FAILED
            sentPayloads().shouldContainExactlyInAnyOrder(ReporterRequest(fixtures.jobId(run, ReporterEndpoint)))
        }

        "not change a completed job while other jobs are still running" {
            val run = fixtures.createOrtRun()
            fixtures.createJobsUntil(run, NotifierEndpoint, JobStatus.RUNNING)

            orchestrator.handle(run, WorkerError(ReporterEndpoint.configPrefix))

            fixtures.jobStatus(run, ReporterEndpoint) shouldBe JobStatus.FINISHED
            fixtures.jobStatus(run, NotifierEndpoint) shouldBe JobStatus.RUNNING
            fixtures.ortRunRepository.get(run.id).shouldNotBeNull {
                status shouldBe OrtRunStatus.CREATED
                issues.shouldBeEmpty()
            }
            sentPayloads().shouldBeEmpty()
        }

        "not change a completed job and the finished run if no other jobs are running" {
            val run = fixtures.createOrtRun()
            fixtures.createJobsUntil(run, NotifierEndpoint, JobStatus.FINISHED)
            fixtures.ortRunRepository.update(run.id, status = OrtRunStatus.FINISHED.asPresent())

            orchestrator.handle(run, WorkerError(ReporterEndpoint.configPrefix))

            fixtures.jobStatus(run, ReporterEndpoint) shouldBe JobStatus.FINISHED
            fixtures.runStatus(run) shouldBe OrtRunStatus.FINISHED
            sentPayloads().shouldBeEmpty()
        }

        "fail the run if the Config worker failed" {
            val run = fixtures.createOrtRun()

            orchestrator.handle(run, WorkerError(ConfigEndpoint.configPrefix))

            fixtures.runStatus(run) shouldBe OrtRunStatus.FAILED
            sentPayloads().shouldBeEmpty()
        }
    }

    "LostSchedule messages" should {
        "trigger the Config worker if no jobs have been created yet" {
            val run = fixtures.createOrtRun()

            orchestrator.handle(run, LostSchedule(run.id))

            sentPayloads().shouldContainExactlyInAnyOrder(ConfigRequest(run.id))
            fixtures.runStatus(run) shouldBe OrtRunStatus.CREATED
        }

        "schedule the next jobs according to the state of the run" {
            val run = fixtures.createOrtRun()
            fixtures.createJob(run, AnalyzerEndpoint, JobStatus.FINISHED)

            orchestrator.handle(run, LostSchedule(run.id))

            sentPayloads().shouldContainExactlyInAnyOrder(
                AdvisorRequest(fixtures.jobId(run, AdvisorEndpoint)),
                ScannerRequest(fixtures.jobId(run, ScannerEndpoint))
            )
        }

        "finish the run if all jobs have been completed" {
            val run = fixtures.createOrtRun()
            fixtures.createJobsUntil(run, NotifierEndpoint, JobStatus.FINISHED)

            orchestrator.handle(run, LostSchedule(run.id))

            fixtures.runStatus(run) shouldBe OrtRunStatus.FINISHED
            sentPayloads().shouldBeEmpty()
        }
    }

    "Created jobs" should {
        "use the resolved job configurations of the run" {
            val run = fixtures.createOrtRun()
            val resolvedConfigs = with(fixtures.jobConfigurations) {
                JobConfigurations(
                    analyzer = analyzer.copy(keepAliveWorker = true),
                    advisor = advisor?.copy(keepAliveWorker = true),
                    scanner = scanner?.copy(keepAliveWorker = true),
                    evaluator = evaluator?.copy(keepAliveWorker = true),
                    reporter = reporter?.copy(keepAliveWorker = true),
                    notifier = notifier?.copy(keepAliveWorker = true)
                )
            }
            fixtures.ortRunRepository.update(run.id, resolvedJobConfigs = resolvedConfigs.asPresent())

            orchestrator.handle(run, ConfigWorkerResult(run.id))
            orchestrator.handle(run, AnalyzerWorkerResult(fixtures.jobId(run, AnalyzerEndpoint)))
            orchestrator.handle(run, AdvisorWorkerResult(fixtures.jobId(run, AdvisorEndpoint)))
            orchestrator.handle(run, ScannerWorkerResult(fixtures.jobId(run, ScannerEndpoint)))
            orchestrator.handle(run, EvaluatorWorkerResult(fixtures.jobId(run, EvaluatorEndpoint)))
            orchestrator.handle(run, ReporterWorkerResult(fixtures.jobId(run, ReporterEndpoint)))

            with(fixtures) {
                analyzerJobRepository.getForOrtRun(run.id)?.configuration shouldBe resolvedConfigs.analyzer
                advisorJobRepository.getForOrtRun(run.id)?.configuration shouldBe resolvedConfigs.advisor
                scannerJobRepository.getForOrtRun(run.id)?.configuration shouldBe resolvedConfigs.scanner
                evaluatorJobRepository.getForOrtRun(run.id)?.configuration shouldBe resolvedConfigs.evaluator
                reporterJobRepository.getForOrtRun(run.id)?.configuration shouldBe resolvedConfigs.reporter
                notifierJobRepository.getForOrtRun(run.id)?.configuration shouldBe resolvedConfigs.notifier
            }
        }
    }

    "Sent messages" should {
        "have headers derived from the received message and the labels and trace ID of the run" {
            val run = fixtures.createOrtRun(labels = mapOf("transport.kubernetes.kubeprop" to "kubeval"))
            fixtures.createJob(run, AnalyzerEndpoint, JobStatus.FINISHED)

            orchestrator.handleMessage(Message(MessageHeader(traceId = "", ortRunId = run.id), LostSchedule(run.id)))

            val expectedHeader = MessageHeader(
                traceId = run.traceId.shouldNotBeNull(),
                ortRunId = run.id,
                transportProperties = mapOf("kubernetes.kubeprop" to "kubeval")
            )
            MessageSenderFactoryForTesting.takeAllMessages().map { it.header }.toSet()
                .shouldContainExactlyInAnyOrder(expectedHeader)
        }
    }
})

/** The endpoints of the workers that run jobs, in the order in which they are run. */
private val jobEndpoints = listOf(
    AnalyzerEndpoint,
    AdvisorEndpoint,
    ScannerEndpoint,
    EvaluatorEndpoint,
    ReporterEndpoint,
    NotifierEndpoint
)

/** Create an [Orchestrator] that uses the database of the given [dbExtension] and the testing transport. */
private fun createOrchestrator(dbExtension: DatabaseTestExtension, fixtures: Fixtures): Orchestrator {
    val senderConfig = (jobEndpoints + ConfigEndpoint).associate {
        "${it.configPrefix}.sender.type" to TEST_TRANSPORT_NAME
    }

    return Orchestrator(
        db = dbExtension.db,
        workerJobRepositories = WorkerJobRepositories(
            fixtures.analyzerJobRepository,
            fixtures.advisorJobRepository,
            fixtures.scannerJobRepository,
            fixtures.evaluatorJobRepository,
            fixtures.reporterJobRepository,
            fixtures.notifierJobRepository
        ),
        repositoryRepository = fixtures.repositoryRepository,
        ortRunRepository = fixtures.ortRunRepository,
        publisher = MessagePublisher(ConfigManager.create(ConfigFactory.parseMap(senderConfig)))
    )
}

/** Pass the given [payload] for the given [run] to the [Orchestrator] like the endpoint does. */
private suspend fun Orchestrator.handle(run: OrtRun, payload: OrchestratorMessage) {
    handleMessage(Message(MessageHeader(traceId = "trace-id", ortRunId = run.id), payload))
}

/** Create the result message of the worker with the given [endpoint] for the job with the given [jobId]. */
private fun workerResult(endpoint: Endpoint<*>, jobId: Long, hasIssues: Boolean): OrchestratorMessage =
    when (endpoint) {
        AnalyzerEndpoint -> AnalyzerWorkerResult(jobId, hasIssues)
        AdvisorEndpoint -> AdvisorWorkerResult(jobId, hasIssues)
        ScannerEndpoint -> ScannerWorkerResult(jobId, hasIssues)
        EvaluatorEndpoint -> EvaluatorWorkerResult(jobId, hasIssues)
        ReporterEndpoint -> ReporterWorkerResult(jobId, hasIssues)
        NotifierEndpoint -> NotifierWorkerResult(jobId)
        else -> error("No jobs exist for endpoint '${endpoint.configPrefix}'.")
    }

/** Create the error message of the worker with the given [endpoint] for the job with the given [jobId]. */
private fun workerError(endpoint: Endpoint<*>, jobId: Long, errorMessage: String): OrchestratorMessage =
    when (endpoint) {
        AnalyzerEndpoint -> AnalyzerWorkerError(jobId, errorMessage)
        AdvisorEndpoint -> AdvisorWorkerError(jobId, errorMessage)
        ScannerEndpoint -> ScannerWorkerError(jobId, errorMessage)
        EvaluatorEndpoint -> EvaluatorWorkerError(jobId, errorMessage)
        ReporterEndpoint -> ReporterWorkerError(jobId, errorMessage)
        NotifierEndpoint -> NotifierWorkerError(jobId, errorMessage)
        else -> error("No jobs exist for endpoint '${endpoint.configPrefix}'.")
    }

/** Return the payloads of all messages sent to the workers since the last call. */
private fun sentPayloads(): List<Any?> = MessageSenderFactoryForTesting.takeAllMessages().map { it.payload }

/** Return the [WorkerJobRepository] for the jobs of the worker with the given [endpoint]. */
private fun Fixtures.repository(endpoint: Endpoint<*>): WorkerJobRepository<*> =
    when (endpoint) {
        AnalyzerEndpoint -> analyzerJobRepository
        AdvisorEndpoint -> advisorJobRepository
        ScannerEndpoint -> scannerJobRepository
        EvaluatorEndpoint -> evaluatorJobRepository
        ReporterEndpoint -> reporterJobRepository
        NotifierEndpoint -> notifierJobRepository
        else -> error("No jobs exist for endpoint '${endpoint.configPrefix}'.")
    }

/** Create a job for the worker with the given [endpoint] for the given [run] and set it to the given [status]. */
private suspend fun Fixtures.createJob(run: OrtRun, endpoint: Endpoint<*>, status: JobStatus): WorkerJob {
    val job = when (endpoint) {
        AnalyzerEndpoint -> createAnalyzerJob(run.id)
        AdvisorEndpoint -> createAdvisorJob(run.id)
        ScannerEndpoint -> createScannerJob(run.id)
        EvaluatorEndpoint -> createEvaluatorJob(run.id)
        ReporterEndpoint -> createReporterJob(run.id)
        NotifierEndpoint -> createNotifierJob(run.id)
        else -> error("No jobs exist for endpoint '${endpoint.configPrefix}'.")
    }

    val repository = repository(endpoint)
    if (status.final) {
        repository.complete(job.id, Clock.System.now(), status)
    } else {
        repository.update(job.id, status = status.asPresent())
    }

    return job
}

/**
 * Create finished jobs for all workers that run before the worker with the given [endpoint] and do not have a job
 * yet. Then create a job in the given [status] for the worker with the given [endpoint] and return it.
 */
private suspend fun Fixtures.createJobsUntil(run: OrtRun, endpoint: Endpoint<*>, status: JobStatus): WorkerJob {
    jobEndpoints.takeWhile { it != endpoint }.filter { jobStatus(run, it) == null }.forEach {
        createJob(run, it, JobStatus.FINISHED)
    }

    return createJob(run, endpoint, status)
}

/** Return the status of the job for the worker with the given [endpoint] of the given [run] if it exists. */
private suspend fun Fixtures.jobStatus(run: OrtRun, endpoint: Endpoint<*>): JobStatus? =
    repository(endpoint).getForOrtRun(run.id)?.status

/** Return the ID of the job for the worker with the given [endpoint] of the given [run]. */
private suspend fun Fixtures.jobId(run: OrtRun, endpoint: Endpoint<*>): Long =
    repository(endpoint).getForOrtRun(run.id).shouldNotBeNull().id

/** Return the current status of the given [run]. */
private suspend fun Fixtures.runStatus(run: OrtRun): OrtRunStatus =
    ortRunRepository.get(run.id).shouldNotBeNull().status
