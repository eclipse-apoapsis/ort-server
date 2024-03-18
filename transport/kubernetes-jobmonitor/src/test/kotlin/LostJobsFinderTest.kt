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

package org.eclipse.apoapsis.ortserver.transport.kubernetes.jobmonitor

import io.kotest.core.spec.style.StringSpec

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1ObjectMeta

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

class LostJobsFinderTest : StringSpec({
    "Notifications for lost jobs should be sent" {
        val runId = 20240315145248L

        val analyzerKubeJob = createKubernetesJob(runId + 1, "analyzer-plus-some-suffix")
        val advisorKubeJob = createKubernetesJob(runId + 2, "advisor-plus-some-suffix")
        val scannerKubeJob = createKubernetesJob(runId + 3, "scanner-plus-some-suffix")
        val evaluatorKubeJob = createKubernetesJob(runId + 4, "evaluator-plus-some-suffix")
        val reporterKubeJob = createKubernetesJob(runId + 5, "reporter-plus-some-suffix")

        val analyzerJobs = listOf(workerJobMock<AnalyzerJob>(runId), workerJobMock<AnalyzerJob>(runId + 1))
        val advisorJobs = listOf(workerJobMock<AdvisorJob>(runId), workerJobMock<AdvisorJob>(runId + 2))
        val scannerJobs = listOf(workerJobMock<ScannerJob>(runId), workerJobMock<ScannerJob>(runId + 3))
        val evaluatorJobs = listOf(workerJobMock<EvaluatorJob>(runId), workerJobMock<EvaluatorJob>(runId + 4))
        val reporterJobs = listOf(workerJobMock<ReporterJob>(runId), workerJobMock<ReporterJob>(runId + 5))

        val analyzerJobRepo = repositoryMock<AnalyzerJob, AnalyzerJobRepository>(analyzerJobs)
        val advisorJobRepo = repositoryMock<AdvisorJob, AdvisorJobRepository>(advisorJobs)
        val scannerJobRepo = repositoryMock<ScannerJob, ScannerJobRepository>(scannerJobs)
        val evaluatorJobRepo = repositoryMock<EvaluatorJob, EvaluatorJobRepository>(evaluatorJobs)
        val reporterJobRepo = repositoryMock<ReporterJob, ReporterJobRepository>(reporterJobs)

        val jobHandler = mockk<JobHandler> {
            every { findJobsForWorker(AnalyzerEndpoint) } returns listOf(analyzerKubeJob)
            every { findJobsForWorker(AdvisorEndpoint) } returns listOf(advisorKubeJob)
            every { findJobsForWorker(ScannerEndpoint) } returns listOf(scannerKubeJob)
            every { findJobsForWorker(EvaluatorEndpoint) } returns listOf(evaluatorKubeJob)
            every { findJobsForWorker(ReporterEndpoint) } returns listOf(reporterKubeJob)
        }

        val notifier = mockk<FailedJobNotifier> {
            every { sendLostJobNotification(any(), any()) } just runs
        }

        val finder = LostJobsFinder(
            jobHandler,
            notifier,
            minJobAge,
            analyzerJobRepo,
            advisorJobRepo,
            scannerJobRepo,
            evaluatorJobRepo,
            reporterJobRepo,
            testClock
        )

        val interval = 3.minutes
        val helper = SchedulerTestHelper()
        finder.run(helper.scheduler, interval)
        helper.expectSchedule(interval).triggerAction()

        verify {
            notifier.sendLostJobNotification(runId, AnalyzerEndpoint)
            notifier.sendLostJobNotification(runId, AdvisorEndpoint)
            notifier.sendLostJobNotification(runId, ScannerEndpoint)
            notifier.sendLostJobNotification(runId, EvaluatorEndpoint)
            notifier.sendLostJobNotification(runId, ReporterEndpoint)
        }
    }
})

/** The current time returned by the mock clock passed to the finder. */
private val currentTime = Instant.parse("2024-03-15T12:29:17Z")

/** The expected time passed to the repository when querying for active jobs. */
private val jobCreationTime = Instant.parse("2024-03-15T12:28:17Z")

/** The configuration setting for the minimum job age. */
private val minJobAge = 1.minutes

/** The clock used by the component under test. It always returns a constant time. */
private val testClock = createClock()

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
 * Return a mock clock that returns the [currentTime].
 */
private fun createClock(): Clock = mockk {
    every { now() } returns currentTime
}
