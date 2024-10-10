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

import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.dao.test.verifyDatabaseModuleIncluded
import org.eclipse.apoapsis.ortserver.dao.test.withMockDatabaseModule
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageReceiverFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME

import org.jetbrains.exposed.sql.Database

import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock

class OrchestratorEndpointTest : KoinTest, StringSpec() {
    private val msgHeader = MessageHeader(
        traceId = "traceId",
        ortRunId = 17
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
                    organizationId = 1,
                    productId = 1,
                    repositoryId = 120,
                    revision = "main",
                    path = null,
                    createdAt = Instant.fromEpochSeconds(0),
                    finishedAt = null,
                    jobConfigs = JobConfigurations(),
                    resolvedJobConfigs = JobConfigurations(),
                    status = OrtRunStatus.CREATED,
                    labels = mapOf("label key" to "label value"),
                    null,
                    null,
                    emptyMap(),
                    null,
                    emptyList(),
                    null,
                    null,
                    environmentConfigPath = null,
                    "trace-id"
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

        "ConfigWorkerResult messages should be handled" {
            val configWorkerResult = ConfigWorkerResult(49)
            val message = Message(msgHeader, configWorkerResult)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleConfigWorkerResult(any(), any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleConfigWorkerResult(message.header, configWorkerResult)
                }
            }
        }

        "ConfigWorkerError messages should be handled" {
            val configWorkerError = ConfigWorkerError(49)
            val message = Message(msgHeader, configWorkerError)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleConfigWorkerError(any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleConfigWorkerError(configWorkerError)
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
                    every { handleAnalyzerWorkerError(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleAnalyzerWorkerError(msgHeader, analyzerWorkerError)
                }
            }
        }

        "AnalyzerWorkerResultWithIssues messages should be handled" {
            val analyzerWorkerResultWithIssues = AnalyzerWorkerResult(99, true)
            val message = Message(msgHeader, analyzerWorkerResultWithIssues)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleAnalyzerWorkerResult(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleAnalyzerWorkerResult(msgHeader, analyzerWorkerResultWithIssues)
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
                    every { handleAdvisorWorkerError(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleAdvisorWorkerError(msgHeader, advisorWorkerError)
                }
            }
        }

        "AdvisorWorkerResultWithIssues messages should be handled" {
            val advisorWorkerResultWithIssues = AdvisorWorkerResult(99, true)
            val message = Message(msgHeader, advisorWorkerResultWithIssues)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleAdvisorWorkerResult(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleAdvisorWorkerResult(msgHeader, advisorWorkerResultWithIssues)
                }
            }
        }

        "ScannerWorkerResult messages should be handled" {
            val scannerWorkerResult = ScannerWorkerResult(11)
            val message = Message(msgHeader, scannerWorkerResult)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleScannerWorkerResult(any(), any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleScannerWorkerResult(message.header, scannerWorkerResult)
                }
            }
        }

        "ScannerWorkerError messages should be handled" {
            val scannerWorkerError = ScannerWorkerError(22)
            val message = Message(msgHeader, scannerWorkerError)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleScannerWorkerError(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleScannerWorkerError(msgHeader, scannerWorkerError)
                }
            }
        }

        "ScannerWorkerResultWithIssues messages should be handled" {
            val scannerWorkerResultWithIssues = ScannerWorkerResult(99, true)
            val message = Message(msgHeader, scannerWorkerResultWithIssues)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleScannerWorkerResult(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleScannerWorkerResult(msgHeader, scannerWorkerResultWithIssues)
                }
            }
        }

        "EvaluatorWorkerResult messages should be handled" {
            val evaluatorWorkerResult = EvaluatorWorkerResult(11)
            val message = Message(msgHeader, evaluatorWorkerResult)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleEvaluatorWorkerResult(any(), any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleEvaluatorWorkerResult(message.header, evaluatorWorkerResult)
                }
            }
        }

        "EvaluatorWorkerError messages should be handled" {
            val evaluatorWorkerError = EvaluatorWorkerError(22)
            val message = Message(msgHeader, evaluatorWorkerError)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleEvaluatorWorkerError(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleEvaluatorWorkerError(msgHeader, evaluatorWorkerError)
                }
            }
        }

        "EvaluatorWorkerResultWithIssues messages should be handled" {
            val evaluatorWorkerResultWithIssues = EvaluatorWorkerResult(99, true)
            val message = Message(msgHeader, evaluatorWorkerResultWithIssues)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleEvaluatorWorkerResult(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleEvaluatorWorkerResult(msgHeader, evaluatorWorkerResultWithIssues)
                }
            }
        }

        "ReporterWorkerResult messages should be handled" {
            val reporterWorkerResult = ReporterWorkerResult(11)
            val message = Message(msgHeader, reporterWorkerResult)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleReporterWorkerResult(any(), any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleReporterWorkerResult(message.header, reporterWorkerResult)
                }
            }
        }

        "ReporterWorkerError messages should be handled" {
            val reporterWorkerError = ReporterWorkerError(22)
            val message = Message(msgHeader, reporterWorkerError)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleReporterWorkerError(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleReporterWorkerError(msgHeader, reporterWorkerError)
                }
            }
        }

        "ReporterWorkerResultWithIssues messages should be handled" {
            val reporterWorkerResultWithIssues = ReporterWorkerResult(99, true)
            val message = Message(msgHeader, reporterWorkerResultWithIssues)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleReporterWorkerResult(msgHeader, any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleReporterWorkerResult(msgHeader, reporterWorkerResultWithIssues)
                }
            }
        }

        "WorkerError message should be handled" {
            val workerError = WorkerError("analyzer")
            val message = Message(msgHeader, workerError)

            runEndpointTest {
                val orchestrator = declareMock<Orchestrator> {
                    every { handleWorkerError(any(), any()) } just runs
                }

                MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

                verify {
                    orchestrator.handleWorkerError(msgHeader, workerError)
                }
            }
        }
    }

    /**
     * Run [block] as a test for the Orchestrator endpoint. Start the endpoint with a configuration that selects the
     * testing transport. Then execute the given [block].
     */
    private suspend fun runEndpointTest(block: suspend () -> Unit) {
        withMockDatabaseModule {
            val environment = mapOf(
                "ORCHESTRATOR_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ANALYZER_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ORCHESTRATOR_SECRET_PROVIDER" to ConfigSecretProviderFactoryForTesting.NAME
            )

            withEnvironment(environment) {
                main()

                MockProvider.register { mockkClass(it) }

                block()
            }
        }
    }
}
