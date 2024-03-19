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

package org.eclipse.apoapsis.ortserver.transport.kubernetes.jobmonitor

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

import java.time.ZoneOffset

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageReceiverFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock
import org.koin.test.verify.verify

private const val NAMESPACE = "test-namespace"
private const val REAPER_INTERVAL = 654321

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
                    .verify(extraTypes = listOf(Duration::class, Clock::class, ZoneOffset::class))

                val scheduler by inject<Scheduler>()
                scheduler.shouldNotBeNull()
            }
        }

        "The watcher component is started" {
            val jobName = "analyzer-test-job"
            val traceId = "test-trace-id"
            val runId = 27L

            runComponentTest(enableReaper = false) { component ->
                declareMock<BatchV1Api> {
                    every { apiClient } returns mockk(relaxed = true)
                    every {
                        listNamespacedJob(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
                    } returns V1JobList()
                    every {
                        listNamespacedJobCall(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any()
                        )
                    } returns mockk(relaxed = true)
                }

                val coreApi = declareMock<CoreV1Api> {
                    every {
                        listNamespacedPod(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
                    } returns V1PodList()
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
                    coreApi.listNamespacedPod(
                        NAMESPACE,
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                }

                val notification = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                notification.header.traceId shouldBe traceId
                notification.header.ortRunId shouldBe runId
            }
        }

        "The reaper component is started" {
            runComponentTest(enableWatching = false) { component ->
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
    }

    /**
     * Run a test on a test instance of [MonitorComponent] that executes the given [block]. In the configuration, set
     * the flags to enable [watching][enableWatching] and [the reaper][enableReaper].
     */
    private suspend fun runComponentTest(
        enableWatching: Boolean = true,
        enableReaper: Boolean = true,
        block: suspend (MonitorComponent) -> Unit
    ) {
        val environment = mapOf(
            "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
            "MONITOR_NAMESPACE" to NAMESPACE,
            "MONITOR_REAPER_INTERVAL" to REAPER_INTERVAL.toString(),
            "MONITOR_WATCHING_ENABLED" to enableWatching.toString(),
            "MONITOR_REAPER_ENABLED" to enableReaper.toString()
        )

        withEnvironment(environment) {
            ConfigFactory.invalidateCaches()

            val monitoringComponent = MonitorComponent()

            MockProvider.register { mockkClass(it) }

            block(monitoringComponent)
        }
    }
}
