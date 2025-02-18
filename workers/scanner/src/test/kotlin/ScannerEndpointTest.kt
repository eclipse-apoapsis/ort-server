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

package org.eclipse.apoapsis.ortserver.workers.scanner

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.coEvery
import io.mockk.mockkClass

import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.dao.test.verifyDatabaseModuleIncluded
import org.eclipse.apoapsis.ortserver.dao.test.withMockDatabaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageReceiverFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME
import org.eclipse.apoapsis.ortserver.workers.common.RunResult

import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock

private const val SCANNER_JOB_ID = 1L
private const val TRACE_ID = "42"

private val messageHeader = MessageHeader(TRACE_ID, 25)

private val scannerRequest = ScannerRequest(
    scannerJobId = SCANNER_JOB_ID
)

class ScannerEndpointTest : KoinTest, StringSpec() {
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

        "A message to scan a project should be processed" {
            runEndpointTest {
                declareMock<ScannerWorker> {
                    coEvery { run(SCANNER_JOB_ID, TRACE_ID) } returns RunResult.Success
                }

                sendScannerRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe ScannerWorkerResult(SCANNER_JOB_ID)
            }
        }

        "An error message should be sent back in case of a processing error" {
            runEndpointTest {
                declareMock<ScannerWorker> {
                    coEvery { run(SCANNER_JOB_ID, TRACE_ID) } returns
                            RunResult.Failed(IllegalStateException("Test Exception"))
                }

                sendScannerRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe ScannerWorkerError(SCANNER_JOB_ID, "Test Exception")
            }
        }

        "A 'run finished with issues' message should be sent when ORT issues are over the threshold" {
            runEndpointTest {
                declareMock<ScannerWorker> {
                    coEvery { run(SCANNER_JOB_ID, TRACE_ID) } returns RunResult.FinishedWithIssues
                }

                sendScannerRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe ScannerWorkerResult(SCANNER_JOB_ID, true)
            }
        }

        "No response should be sent if the request is ignored" {
            runEndpointTest {
                declareMock<ScannerWorker> {
                    coEvery { run(SCANNER_JOB_ID, TRACE_ID) } returns RunResult.Ignored
                }

                sendScannerRequest()

                MessageSenderFactoryForTesting.expectNoMessage(OrchestratorEndpoint)
            }
        }

        "Dependency injection is correctly set up" {
            runEndpointTest {
                val worker by inject<ScannerWorker>()

                worker shouldNot beNull()
            }
        }
    }

    /**
     * Simulate an incoming request to scan a project.
     */
    private suspend fun sendScannerRequest() {
        mockkTransaction {
            val message = Message(messageHeader, scannerRequest)
            MessageReceiverFactoryForTesting.receive(ScannerEndpoint, message)
        }
    }

    /**
     * Run [block] as a test for the Scanner endpoint. Start the endpoint with a configuration that selects the
     * testing transport. Then execute the given [block].
     */
    private suspend fun runEndpointTest(block: suspend () -> Unit) {
        withMockDatabaseModule {
            val environment = mapOf(
                "SCANNER_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "SCANNER_SECRET_PROVIDER" to ConfigSecretProviderFactoryForTesting.NAME
            )

            withEnvironment(environment) {
                main()

                MockProvider.register { mockkClass(it) }

                block()
            }
        }
    }
}
