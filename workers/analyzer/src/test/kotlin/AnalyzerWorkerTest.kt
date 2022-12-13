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

package org.ossreviewtoolkit.server.workers.analyzer

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.kotlinx.datetime.shouldBeBetween

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify

import java.io.File

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.MessageSender
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.json.JsonSerializer
import org.ossreviewtoolkit.utils.ort.Environment as OrtEnvironment

private const val JOB_ID = 1L
private const val TOKEN = "token"
private const val TRACE_ID = "42"

private val projectDir = File("src/test/resources/mavenProject/").absoluteFile

class AnalyzerWorkerTest : WordSpec({
    "AnalyzerWorker" should {
        "analyze a project and send the result to the transport SPI" {
            val serializer = JsonSerializer.forClass(AnalyzerRequest::class)

            val msgSenderMock = mockk<MessageSender<OrchestratorMessage>>()
            val analyzerJobRepository = mockk<AnalyzerJobRepository>()
            val analyzerRunRepository = mockk<AnalyzerRunRepository>()
            mockkObject(MessageSenderFactory)
            every { MessageSenderFactory.createSender(any<OrchestratorEndpoint>(), any()) } returns msgSenderMock
            every { msgSenderMock.send(any()) } just runs
            every { analyzerJobRepository.get(analyzerJob.id) } returns analyzerJob
            every {
                analyzerRunRepository.create(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns analyzerRun

            val worker = spyk(
                AnalyzerWorker(
                    ConfigFactory.parseMap(
                        mapOf(
                            "${AnalyzerEndpoint.configPrefix}.${MessageReceiverFactory.RECEIVER_TYPE_PROPERTY}" to
                                    "testMessageReceiverFactory",
                            TEST_RECEIVER_PAYLOAD_CONFIG_KEY to serializer.toJson(analyzerRequest)
                        )
                    ),
                    analyzerJobRepository,
                    analyzerRunRepository
                )
            )

            // To speed up the test and to not rely on a network connection, a minimal pom file is analyzed and the
            // repository is not cloned.
            with(worker) {
                every { any<AnalyzerJob>().download() } returns projectDir
            }

            worker.start()

            verify(exactly = 1) {
                msgSenderMock.send(
                    Message(MessageHeader(TOKEN, TRACE_ID), AnalyzerWorkerResult(JOB_ID))
                )

                analyzerRunRepository.create(
                    analyzerJobId = analyzerJob.id,
                    // As an actual analysis is performed, the startTime might be some time ago.
                    startTime = withArg { it.validateTimeRange(30.seconds) },
                    endTime = withArg { it.validateTimeRange(10.seconds) },
                    environment = analyzerRun.environment,
                    config = analyzerConfig,
                    projects = analyzerRun.projects,
                    packages = analyzerRun.packages,
                    issues = analyzerRun.issues,
                    dependencyGraphs = analyzerRun.dependencyGraphs
                )
            }
        }
    }
})

/**
 * Use [OrtEnvironment] to create the environment for the machine which runs these tests.
 */
private val environment = with(OrtEnvironment()) {
    Environment(ortVersion, javaVersion, os, processors, maxMemory, variables, toolVersions)
}

private val analyzerConfig = AnalyzerConfiguration(allowDynamicVersions = false)

private val analyzerRun = AnalyzerRun(
    id = 0,
    analyzerJobId = 0,
    startTime = Instant.fromEpochSeconds(0),
    endTime = Clock.System.now(),
    environment = environment,
    config = analyzerConfig,
    projects = emptySet(),
    packages = emptySet(),
    issues = emptyMap(),
    dependencyGraphs = emptyMap()
)

private val analyzerJob = AnalyzerJob(
    id = JOB_ID,
    ortRunId = 12,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = AnalyzerJobConfiguration(),
    status = JobStatus.CREATED,
    repositoryUrl = "https://example.com/git/repository.git",
    repositoryRevision = "main"
)

private val analyzerRequest = AnalyzerRequest(
    analyzerJobId = analyzerJob.id
)

/** The name reported by the test receiver factory. */
private const val TEST_RECEIVER_FACTORY_NAME = "testMessageReceiverFactory"

/** The config key for the mock message's payload which is received by this test receiver. */
private const val TEST_RECEIVER_PAYLOAD_CONFIG_KEY = "test.receiver.payload"

/**
 * A MessageReceiverFactory intended to be used for unit testing parts of the code that relies on the SPI receiver
 * implementations.
 */
class MessageReceiverFactoryForTesting : MessageReceiverFactory {
    override val name: String = TEST_RECEIVER_FACTORY_NAME

    /**
     * A mock receiver implementation which immediately simulates the retrieval of a message. The message payload can be
     * set by setting the [config] key [TEST_RECEIVER_PAYLOAD_CONFIG_KEY].
     */
    override fun <T : Any> createReceiver(endpoint: Endpoint<T>, config: Config, handler: EndpointHandler<T>) {
        val serializer = JsonSerializer.forClass(endpoint.messageClass)
        val payload = config.getString(TEST_RECEIVER_PAYLOAD_CONFIG_KEY)

        handler(
            Message(
                MessageHeader(TOKEN, TRACE_ID),
                serializer.fromJson(payload)
            )
        )
    }
}

fun Instant.validateTimeRange(allowedDiff: Duration) {
    val now = Clock.System.now()

    shouldBeBetween(now - allowedDiff, now)
}
