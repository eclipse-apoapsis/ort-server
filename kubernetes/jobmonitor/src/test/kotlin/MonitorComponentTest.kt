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

package org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobCondition
import io.kubernetes.client.openapi.models.V1JobList
import io.kubernetes.client.openapi.models.V1JobStatus
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.util.Watch

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify

import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import okhttp3.OkHttpClient

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
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageReceiverFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME

import org.jetbrains.exposed.sql.Database

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock
import org.koin.test.verify.verify

private const val NAMESPACE = "test-namespace"
private const val REAPER_INTERVAL = 654321
private const val LOST_JOBS_INTERVAL = 123456
private const val LOST_JOBS_MIN_AGE = 33
private const val LONG_RUNNING_JOBS_INTERVAL = 33471

@OptIn(KoinExperimentalAPI::class)
class MonitorComponentTest : KoinTest, StringSpec() {
    init {
        beforeSpec {
            mockkStatic(Watch::class)
        }

        afterEach {
            stopKoin()
            MessageReceiverFactoryForTesting.reset()
        }

        afterSpec {
            unmockkAll()
        }

        "The DI configuration is correct" {
            runComponentTest { component ->
                component.monitoringModule()
                    .verify(
                        extraTypes = listOf(
                            Boolean::class,
                            Clock::class,
                            Config::class,
                            Duration::class,
                            Function0::class,
                            Function1::class,
			    OkHttpClient::class,
                            TimeoutConfig::class,
                            ZoneOffset::class,
                        )
                    )

                val scheduler by inject<Scheduler>()
                scheduler.shouldNotBeNull()
            }
        }

        "The watcher component is started" {
            val jobName = "analyzer-test-job"
            val traceId = "test-trace-id"
            val runId = 27L

            runComponentTest(enableWatching = true) { component ->
                val jobRequest = mockk<BatchV1Api.APIlistNamespacedJobRequest> {
                    every { buildCall(any()) } returns mockk(relaxed = true)
                    every { execute() } returns V1JobList()
                }

                declareMock<BatchV1Api> {
                    every { apiClient } returns mockk(relaxed = true)
                    every { listNamespacedJob(any()) } returns jobRequest
                    every { jobRequest.allowWatchBookmarks(any()) } returns jobRequest
                    every { jobRequest.limit(any()) } returns jobRequest
                    every { jobRequest.resourceVersion(any()) } returns jobRequest
                    every { jobRequest.watch(any()) } returns jobRequest
                }

                val podRequest = mockk<CoreV1Api.APIlistNamespacedPodRequest> {
                    every { execute() } returns V1PodList()
                }

                val coreApi = declareMock<CoreV1Api> {
                    every { listNamespacedPod(any()) } returns podRequest
                    every { podRequest.labelSelector(any()) } returns podRequest
                    every { podRequest.watch(any()) } returns podRequest
                }

                val job = V1Job().apply {
                    status = V1JobStatus().apply {
                        addConditionsItem(V1JobCondition().apply { type("Failed") })
                    }
                    metadata = V1ObjectMeta().apply {
                        name = jobName
                        labels = mapOf("trace-id-0" to traceId, "run-id" to runId.toString())
                    }
                }

                val watch = mockk<Watch<V1Job>>().apply {
                    every { iterator() } returns mutableListOf(Watch.Response("MODIFIED", job)).iterator()
                }
                every { Watch.createWatch<V1Job>(any(), any(), any()) } returns watch

                CoroutineScope(Dispatchers.IO).launch {
                    component.start()
                }

                verify(timeout = 3000) {
                    coreApi.listNamespacedPod(NAMESPACE)
                    podRequest.execute()
                }

                val notification = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                notification.header.traceId shouldBe traceId
                notification.header.ortRunId shouldBe runId
            }
        }

        "The reaper component is started" {
            runComponentTest(enableReaper = true) { component ->
                val handler = declareMock<JobHandler> {
                    every { findJobsCompletedBefore(any()) } returns emptyList()
                }

                val scheduler = declareMock<Scheduler> {
                    initSchedulerMock(this)
                }

                component.start()

                val reaperAction = fetchScheduledAction(scheduler, REAPER_INTERVAL.seconds)
                reaperAction()

                verify {
                    handler.findJobsCompletedBefore(any())
                }
            }
        }

        "The lost jobs finder component is started" {
            runComponentTest(enableLostJobs = true) { component ->
                val handler = declareMock<JobHandler> {
                    every { findJobsForWorker(any()) } returns emptyList()
                }

                val analyzerJobRepo = declareRepositoryMock<AnalyzerJob, AnalyzerJobRepository>()
                declareRepositoryMock<AdvisorJob, AdvisorJobRepository>()
                declareRepositoryMock<ScannerJob, ScannerJobRepository>()
                declareRepositoryMock<EvaluatorJob, EvaluatorJobRepository>()
                declareRepositoryMock<ReporterJob, ReporterJobRepository>()
                declareRepositoryMock<NotifierJob, NotifierJobRepository>()

                declareMock<OrtRunRepository> {
                    every { listActiveRuns() } returns emptyList()
                }

                val scheduler = declareMock<Scheduler> {
                    initSchedulerMock(this)
                }

                val referenceTime = Instant.parse("2024-03-18T10:04:42Z")
                declareMock<TimeHelper> {
                    every { now() } returns referenceTime
                }

                component.start()

                val lostJobsFinderAction = fetchScheduledAction(scheduler, LOST_JOBS_INTERVAL.seconds)
                lostJobsFinderAction()

                verify {
                    handler.findJobsForWorker(AnalyzerEndpoint)
                    analyzerJobRepo.listActive(referenceTime.minus(LOST_JOBS_MIN_AGE.seconds))
                }
            }
        }

        "The long-running jobs finder component is started" {
            runComponentTest(enableLongRunning = true) { component ->
                val handler = declareMock<JobHandler> {
                    every { findJobsForWorker(any()) } returns emptyList()
                }

                val scheduler = declareMock<Scheduler> {
                    initSchedulerMock(this)
                }

                val referenceTime = OffsetDateTime.now()
                declareMock<TimeHelper> {
                    every { before(any()) } returns referenceTime
                }

                component.start()

                val longRunningJobsFinderAction = fetchScheduledAction(scheduler, LONG_RUNNING_JOBS_INTERVAL.seconds)
                longRunningJobsFinderAction()

                verify {
                    handler.findJobsForWorker(ConfigEndpoint)
                }
            }
        }
    }

    /**
     * Run a test on a test instance of [MonitorComponent] that executes the given [block]. In the configuration, set
     * the flags to enable [watching][enableWatching], [the reaper][enableReaper], the
     * [lost job detection][enableLostJobs], and [long-running job detection][enableLongRunning].
     */
    private suspend fun runComponentTest(
        enableWatching: Boolean = false,
        enableReaper: Boolean = false,
        enableLostJobs: Boolean = false,
        enableLongRunning: Boolean = false,
        block: suspend (MonitorComponent) -> Unit
    ) {
        val environment = mapOf(
            "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
            "MONITOR_NAMESPACE" to NAMESPACE,
            "MONITOR_REAPER_INTERVAL" to REAPER_INTERVAL.toString(),
            "MONITOR_LOST_JOBS_INTERVAL" to LOST_JOBS_INTERVAL.toString(),
            "MONITOR_LONG_RUNNING_JOBS_INTERVAL" to LONG_RUNNING_JOBS_INTERVAL.toString(),
            "MONITOR_LOST_JOBS_MIN_AGE" to LOST_JOBS_MIN_AGE.toString(),
            "MONITOR_WATCHING_ENABLED" to enableWatching.toString(),
            "MONITOR_REAPER_ENABLED" to enableReaper.toString(),
            "MONITOR_LOST_JOBS_ENABLED" to enableLostJobs.toString(),
            "MONITOR_LONG_RUNNING_JOBS_ENABLED" to enableLongRunning.toString()
        )

        withEnvironment(environment) {
            ConfigFactory.invalidateCaches()

            val monitoringComponent = MonitorComponent()

            MockProvider.register { mockkClass(it) }

            declareMock<Database>()
            block(monitoringComponent)
        }
    }

    /**
     * Create a mock for a [WorkerJobRepository] of a given type that is prepared to expect a query for active jobs.
     */
    private inline fun <J : WorkerJob, reified R : WorkerJobRepository<J>> declareRepositoryMock(): R =
        declareMock<R> {
            every { listActive(any()) } returns emptyList()
        }
}
