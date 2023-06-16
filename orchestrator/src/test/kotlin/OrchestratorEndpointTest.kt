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

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.system.withEnvironment

import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.runs
import io.mockk.verify

import kotlinx.datetime.Instant

import org.jetbrains.exposed.sql.Database

import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock

import org.ossreviewtoolkit.server.dao.test.verifyDatabaseModuleIncluded
import org.ossreviewtoolkit.server.dao.test.withMockDatabaseModule
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.testing.MessageReceiverFactoryForTesting
import org.ossreviewtoolkit.server.transport.testing.TEST_TRANSPORT_NAME

class OrchestratorEndpointTest : KoinTest, StringSpec() {
    private val msgHeader = MessageHeader(
        token = "token",
        traceId = "traceId"
    )

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        stopKoin()
        MessageReceiverFactoryForTesting.reset()
    }

    init {
        "The database module should be added" {
            runEndpointTest {
                verifyDatabaseModuleIncluded()
            }
        }

        "The DI configuration is correct" {
            runEndpointTest {
                declareMock<Database>()
                val orchestrator: Orchestrator by inject()
                orchestrator.toString()
            }
        }

        "CreateOrtRun messages should be handled" {
            val createOrtRun = CreateOrtRun(
                OrtRun(
                    id = 1,
                    index = 12,
                    repositoryId = 120,
                    revision = "main",
                    createdAt = Instant.fromEpochSeconds(0),
                    jobs = JobConfigurations(),
                    status = OrtRunStatus.CREATED,
                    labels = mapOf("label key" to "label value"),
                    null,
                    null,
                    emptyMap()
                )
            )
            val message = Message(msgHeader, createOrtRun)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleCreateOrtRun(any(), any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleCreateOrtRun(message.header, createOrtRun)
                }
            }
        }

        "AnalyzerWorkerResult messages should be handled" {
            val analyzerWorkerResult = AnalyzerWorkerResult(17)
            val message = Message(msgHeader, analyzerWorkerResult)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleAnalyzerWorkerResult(any(), any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleAnalyzerWorkerResult(message.header, analyzerWorkerResult)
                }
            }
        }

        "AnalyzerWorkerError messages should be handled" {
            val analyzerWorkerError = AnalyzerWorkerError(99)
            val message = Message(msgHeader, analyzerWorkerError)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleAnalyzerWorkerError(any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleAnalyzerWorkerError(analyzerWorkerError)
                }
            }
        }

        "AdvisorWorkerResult messages should be handled" {
            val advisorWorkerResult = AdvisorWorkerResult(11)
            val message = Message(msgHeader, advisorWorkerResult)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleAdvisorWorkerResult(any(), any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleAdvisorWorkerResult(message.header, advisorWorkerResult)
                }
            }
        }

        "AdvisorWorkerError messages should be handled" {
            val advisorWorkerError = AdvisorWorkerError(22)
            val message = Message(msgHeader, advisorWorkerError)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleAdvisorWorkerError(any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleAdvisorWorkerError(advisorWorkerError)
                }
            }
        }
    }

    /**
     * Run [block] as a test for the Orchestrator endpoint. Start the endpoint with a configuration that selects the
     * testing transport. Then execute the given [block].
     */
    private fun runEndpointTest(block: () -> Unit) {
        withMockDatabaseModule {
            val environment = mapOf(
                "ORCHESTRATOR_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ANALYZER_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME
            )

            withEnvironment(environment) {
                main()

                MockProvider.register { mockkClass(it) }

                block()
            }
        }
    }
}
