/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes

import io.kotest.core.spec.style.StringSpec

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1ObjectMeta

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.ActiveOrtRun
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

class LostJobsFinderTaskTest : StringSpec({
    "Notifications for lost jobs should be sent" {
        val jobHandler = mockk<JobHandler>().apply {
            every { findJobsForWorker(ConfigEndpoint) } returns emptyList()
            prepareJobsQuery(AnalyzerEndpoint)
            prepareJobsQuery(AdvisorEndpoint)
            prepareJobsQuery(ScannerEndpoint)
            prepareJobsQuery(EvaluatorEndpoint)
            prepareJobsQuery(ReporterEndpoint)
            prepareJobsQuery(NotifierEndpoint)
        }

        val analyzerJobs = listOf(workerJobMock<AnalyzerJob>(RUN_ID), workerJobMock<AnalyzerJob>(RUN_ID + 1))
        val advisorJobs = listOf(workerJobMock<AdvisorJob>(RUN_ID), workerJobMock<AdvisorJob>(RUN_ID + 2))
        val scannerJobs = listOf(workerJobMock<ScannerJob>(RUN_ID), workerJobMock<ScannerJob>(RUN_ID + 3))
        val evaluatorJobs = listOf(workerJobMock<EvaluatorJob>(RUN_ID), workerJobMock<EvaluatorJob>(RUN_ID + 4))
        val reporterJobs = listOf(workerJobMock<ReporterJob>(RUN_ID), workerJobMock<ReporterJob>(RUN_ID + 5))
        val notifierJobs = listOf(workerJobMock<NotifierJob>(RUN_ID), workerJobMock<NotifierJob>(RUN_ID + 6))

        val analyzerJobRepo = repositoryMock<AnalyzerJob, AnalyzerJobRepository>(analyzerJobs)
        val advisorJobRepo = repositoryMock<AdvisorJob, AdvisorJobRepository>(advisorJobs)
        val scannerJobRepo = repositoryMock<ScannerJob, ScannerJobRepository>(scannerJobs)
        val evaluatorJobRepo = repositoryMock<EvaluatorJob, EvaluatorJobRepository>(evaluatorJobs)
        val reporterJobRepo = repositoryMock<ReporterJob, ReporterJobRepository>(reporterJobs)
        val notifierJobRepo = repositoryMock<NotifierJob, NotifierJobRepository>(notifierJobs)

        val ortRunRepo = mockk<OrtRunRepository> {
            every { get(any<Long>()) } returns mockk {
                every { id } answers { arg(0) }
                every { traceId } returns ""
            }
            every { listActiveRuns() } returns emptyList()
        }

        val notifier = mockk<FailedJobNotifier> {
            every { sendLostJobNotification(any(), any()) } just runs
        }

        val config = mockk<MonitorConfig> {
            every { lostJobsMinAge } returns minJobAge
        }

        val finder = LostJobsFinderTask(
            jobHandler,
            notifier,
            config,
            analyzerJobRepo,
            advisorJobRepo,
            scannerJobRepo,
            evaluatorJobRepo,
            reporterJobRepo,
            notifierJobRepo,
            ortRunRepo,
            testTimeHelper
        )

        finder.execute()

        verify {
            notifier.sendLostJobNotification(RUN_ID, AnalyzerEndpoint)
            notifier.sendLostJobNotification(RUN_ID, AdvisorEndpoint)
            notifier.sendLostJobNotification(RUN_ID, ScannerEndpoint)
            notifier.sendLostJobNotification(RUN_ID, EvaluatorEndpoint)
            notifier.sendLostJobNotification(RUN_ID, ReporterEndpoint)
            notifier.sendLostJobNotification(RUN_ID, NotifierEndpoint)
        }
    }

    "Notifications for lost schedules should be sent" {
        val configJob = createKubernetesJob(RUN_ID + 7, "config-plus-some-suffix")
        val jobHandler = mockk<JobHandler>().apply {
            prepareJobsQuery(AnalyzerEndpoint)
            prepareJobsQuery(AdvisorEndpoint)
            prepareJobsQuery(ScannerEndpoint)
            prepareJobsQuery(EvaluatorEndpoint)
            prepareJobsQuery(ReporterEndpoint)
            prepareJobsQuery(NotifierEndpoint)
            every { findJobsForWorker(ConfigEndpoint) }.returns(listOf(configJob))
        }
        val analyzerJobs = listOf(workerJobMock<AnalyzerJob>(RUN_ID), workerJobMock<AnalyzerJob>(RUN_ID + 1))
        val advisorJobs = listOf(workerJobMock<AdvisorJob>(RUN_ID + 2))
        val scannerJobs = listOf(workerJobMock<ScannerJob>(RUN_ID + 3))
        val evaluatorJobs = listOf(workerJobMock<EvaluatorJob>(RUN_ID + 4))
        val reporterJobs = listOf(workerJobMock<ReporterJob>(RUN_ID + 5))
        val notifierJobs = listOf(workerJobMock<NotifierJob>(RUN_ID + 6))

        val analyzerJobRepo = repositoryMock<AnalyzerJob, AnalyzerJobRepository>(analyzerJobs)
        val advisorJobRepo = repositoryMock<AdvisorJob, AdvisorJobRepository>(advisorJobs)
        val scannerJobRepo = repositoryMock<ScannerJob, ScannerJobRepository>(scannerJobs)
        val evaluatorJobRepo = repositoryMock<EvaluatorJob, EvaluatorJobRepository>(evaluatorJobs)
        val reporterJobRepo = repositoryMock<ReporterJob, ReporterJobRepository>(reporterJobs)
        val notifierJobRepo = repositoryMock<NotifierJob, NotifierJobRepository>(notifierJobs)

        val runWithLostSchedules = ActiveOrtRun(RUN_ID + 42, jobCreationTime, "traceLost")
        val ortRunRepo = mockk<OrtRunRepository> {
            every { get(any<Long>()) } returns mockk {
                every { id } answers { arg(0) }
                every { traceId } returns ""
            }
            every { listActiveRuns() } returns listOf(
                runWithLostSchedules,
                ActiveOrtRun(RUN_ID, jobCreationTime, "trace1"),
                ActiveOrtRun(RUN_ID + 1, jobCreationTime, "trace2"),
                ActiveOrtRun(RUN_ID + 43, jobCreationTime + 1.seconds, "trace3"),
                ActiveOrtRun(RUN_ID + 7, jobCreationTime, "trace4")
            )
        }

        val notifier = mockk<FailedJobNotifier> {
            every { sendLostJobNotification(any(), any()) } just runs
            every { sendLostScheduleNotification(runWithLostSchedules) } just runs
        }

        val config = mockk<MonitorConfig> {
            every { lostJobsMinAge } returns minJobAge
        }

        val finder = LostJobsFinderTask(
            jobHandler,
            notifier,
            config,
            analyzerJobRepo,
            advisorJobRepo,
            scannerJobRepo,
            evaluatorJobRepo,
            reporterJobRepo,
            notifierJobRepo,
            ortRunRepo,
            testTimeHelper
        )

        finder.execute()

        verify {
            notifier.sendLostScheduleNotification(runWithLostSchedules)
        }
    }
})

/** ID of a test ORT run. */
private const val RUN_ID = 20240315145248L

/** The current time returned by the mock clock passed to the finder. */
private val currentTime = Instant.parse("2024-03-15T12:29:17Z")

/** The expected time passed to the repository when querying for active jobs. */
private val jobCreationTime = Instant.parse("2024-03-15T12:28:17Z")

/** The configuration setting for the minimum job age. */
private val minJobAge = 1.minutes

/** The clock used by the component under test. It always returns a constant time. */
private val testTimeHelper = createTimeHelper()

/**
 * Prepare this [JobHandler] mock to expect a query for the active jobs of the given [endpoint]. For each endpoint,
 * the query returns a job with a run ID different from the one that is used for testing.
 */
private fun <T : Any> JobHandler.prepareJobsQuery(endpoint: Endpoint<T>) {
    // Note that this is by intention an exhaustive when statement to ensure that the compiler complains if another
    // endpoint is added which may need to be handled by this component.
    val job = when (endpoint) {
        AnalyzerEndpoint -> createKubernetesJob(RUN_ID + 1, "analyzer-plus-some-suffix")
        AdvisorEndpoint -> createKubernetesJob(RUN_ID + 2, "advisor-plus-some-suffix")
        ScannerEndpoint -> createKubernetesJob(RUN_ID + 3, "scanner-plus-some-suffix")
        EvaluatorEndpoint -> createKubernetesJob(RUN_ID + 4, "evaluator-plus-some-suffix")
        ReporterEndpoint -> createKubernetesJob(RUN_ID + 5, "reporter-plus-some-suffix")
        NotifierEndpoint -> createKubernetesJob(RUN_ID + 6, "notifier-plus-some-suffix")
        ConfigEndpoint -> fail("There are no jobs for the Config endpoint.")
        OrchestratorEndpoint -> fail("Orchestrator is not a worker.")
    }

    every { findJobsForWorker(endpoint) }.returns(listOf(job))
}

/**
 * Create a mock repository for a concrete worker type that returns the given [activeJobs].
 */
private inline fun <J : WorkerJob, reified R : WorkerJobRepository<J>> repositoryMock(activeJobs: List<J>): R =
    mockk {
        every { listActive(jobCreationTime) } returns activeJobs
    }

/**
 * Create a mock worker job of the given type with the given [runId].
 */
private inline fun <reified T : WorkerJob> workerJobMock(runId: Long): T =
    mockk {
        every { ortRunId } returns runId
    }

/**
 * Create a Kubernetes job with a label referencing the given [runId] and the given [name].
 */
private fun createKubernetesJob(runId: Long, name: String): V1Job =
    V1Job().apply {
        metadata = V1ObjectMeta().apply {
            name(name)
            labels(mapOf("run-id" to runId.toString()))
        }
    }

/**
 * Return a mock [TimeHelper] that returns the [currentTime].
 */
private fun createTimeHelper(): TimeHelper = mockk {
    every { now() } returns currentTime
}
