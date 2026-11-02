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
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME

class JobDispatcherTest : WordSpec({
    val header = MessageHeader(traceId = "message-trace-id", ortRunId = RUN_ID)

    val expectedHeader = header.copy(
        transportProperties = mapOf("kubernetes.kubeprop" to "kubeval", "rabbitmq.rabbitprop" to "rabbitval")
    )

    lateinit var dispatcher: JobDispatcher

    beforeEach {
        dispatcher = JobDispatcher(createPublisher())
        MessageSenderFactoryForTesting.reset()
    }

    "dispatchJob()" should {
        forAll(
            row(WorkerScheduleInfo.ANALYZER, AnalyzerEndpoint, AnalyzerRequest(JOB_ID)),
            row(WorkerScheduleInfo.ADVISOR, AdvisorEndpoint, AdvisorRequest(JOB_ID)),
            row(WorkerScheduleInfo.SCANNER, ScannerEndpoint, ScannerRequest(JOB_ID)),
            row(WorkerScheduleInfo.EVALUATOR, EvaluatorEndpoint, EvaluatorRequest(JOB_ID)),
            row(WorkerScheduleInfo.REPORTER, ReporterEndpoint, ReporterRequest(JOB_ID)),
            row(WorkerScheduleInfo.NOTIFIER, NotifierEndpoint, NotifierRequest(JOB_ID))
        ) { worker, endpoint, payload ->
            "send a request for a job of the $worker to the ${endpoint.configPrefix} endpoint" {
                dispatcher.dispatchJob(worker, JOB_ID, ortRun, header)

                MessageSenderFactoryForTesting.expectMessage(endpoint) shouldBe Message(expectedHeader, payload)
                MessageSenderFactoryForTesting.takeAllMessages() should beEmpty()
            }
        }
    }

    "dispatchConfigJob()" should {
        "send a request for the run to the Config endpoint" {
            dispatcher.dispatchConfigJob(ortRun, header)

            MessageSenderFactoryForTesting.expectMessage(ConfigEndpoint) shouldBe
                Message(expectedHeader, ConfigRequest(RUN_ID))
        }

        "use the trace ID of the run if the header does not contain one" {
            dispatcher.dispatchConfigJob(ortRun, header.copy(traceId = ""))

            MessageSenderFactoryForTesting.expectMessage(ConfigEndpoint).header.traceId shouldBe ortRun.traceId
        }
    }
})

private const val RUN_ID = 1L
private const val JOB_ID = 42L

private val ortRun = OrtRun(
    id = RUN_ID,
    index = 12,
    organizationId = 1L,
    productId = 1L,
    repositoryId = 1L,
    revision = "main",
    createdAt = Instant.fromEpochSeconds(0),
    finishedAt = null,
    jobConfigs = JobConfigurations(),
    resolvedJobConfigs = null,
    status = OrtRunStatus.ACTIVE,
    labels = mapOf(
        "label key" to "label value",
        "transport.kubernetes.kubeprop" to "kubeval",
        "transport.rabbitmq.rabbitprop" to "rabbitval"
    ),
    vcsId = null,
    vcsProcessedId = null,
    nestedRepositoryIds = emptyMap(),
    repositoryConfigId = null,
    issues = emptyList(),
    jobConfigContext = null,
    resolvedJobConfigContext = null,
    traceId = "run-trace-id"
)

/** Create a [MessagePublisher] that sends messages to all endpoints via the testing transport. */
private fun createPublisher(): MessagePublisher {
    val endpoints = listOf(
        ConfigEndpoint,
        AnalyzerEndpoint,
        AdvisorEndpoint,
        ScannerEndpoint,
        EvaluatorEndpoint,
        ReporterEndpoint,
        NotifierEndpoint
    )
    val config = endpoints.associate { "${it.configPrefix}.sender.type" to TEST_TRANSPORT_NAME }

    return MessagePublisher(ConfigManager.create(ConfigFactory.parseMap(config)))
}
