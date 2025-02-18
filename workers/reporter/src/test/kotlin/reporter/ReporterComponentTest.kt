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

package org.eclipse.apoapsis.ortserver.workers.reporter

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.coEvery
import io.mockk.mockkClass

import kotlin.time.Duration.Companion.days

import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.dao.test.verifyDatabaseModuleIncluded
import org.eclipse.apoapsis.ortserver.dao.test.withMockDatabaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageReceiverFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME
import org.eclipse.apoapsis.ortserver.workers.common.RunResult

import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock

private const val REPORTER_JOB_ID = 1L
private const val TRACE_ID = "42"
private const val DOWNLOAD_LINK_PREFIX = "https://report.example.org/download/"
private const val TOKEN_LENGTH = 77
private const val TOKEN_VALIDITY = 101

private val messageHeader = MessageHeader(TRACE_ID, 26)

private val reporterRequest = ReporterRequest(REPORTER_JOB_ID)

class ReporterComponentTest : KoinTest, StringSpec() {
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

        "Dependency injection is correctly set up" {
            runEndpointTest {
                val reporterWorker by inject<ReporterWorker>()

                reporterWorker shouldNot beNull()
            }
        }

        "The download link generator is correctly configured" {
            runEndpointTest {
                val linkGenerator by inject<ReportDownloadLinkGenerator>()

                linkGenerator.linkPrefix shouldBe DOWNLOAD_LINK_PREFIX
                linkGenerator.tokenLength shouldBe TOKEN_LENGTH
                linkGenerator.validityTime shouldBe TOKEN_VALIDITY.days
            }
        }

        "A message to create a project report should be processed" {
            runEndpointTest {
                declareMock<ReporterWorker> {
                    coEvery { run(REPORTER_JOB_ID, TRACE_ID) } returns RunResult.Success
                }

                sendReporterRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe ReporterWorkerResult(REPORTER_JOB_ID)
            }
        }

        "An error message should be sent back in case of a processing error" {
            runEndpointTest {
                declareMock<ReporterWorker> {
                    coEvery { run(REPORTER_JOB_ID, TRACE_ID) } returns
                            RunResult.Failed(IllegalStateException("Test Exception"))
                }

                sendReporterRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe ReporterWorkerError(REPORTER_JOB_ID, "Test Exception")
            }
        }

        "No response should be sent if the request is ignored" {
            runEndpointTest {
                declareMock<ReporterWorker> {
                    coEvery { run(REPORTER_JOB_ID, TRACE_ID) } returns RunResult.Ignored
                }

                sendReporterRequest()

                MessageSenderFactoryForTesting.expectNoMessage(OrchestratorEndpoint)
            }
        }
    }

    /**
     * Simulate an incoming request to create a project report.
     */
    private suspend fun sendReporterRequest() {
        val message = Message(messageHeader, reporterRequest)
        MessageReceiverFactoryForTesting.receive(ReporterEndpoint, message)
    }

    /**
     * Run [block] as a test for the Reporter endpoint. Start the endpoint with a configuration that selects the
     * testing transport. Then execute the [block].
     */
    private suspend fun runEndpointTest(block: suspend () -> Unit) {
        withMockDatabaseModule {
            val environment = mapOf(
                "REPORTER_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "REPORTER_SECRET_PROVIDER" to ConfigSecretProviderFactoryForTesting.NAME,
                "REPORT_DOWNLOAD_LINK_PREFIX" to DOWNLOAD_LINK_PREFIX,
                "REPORT_TOKEN_LENGTH" to TOKEN_LENGTH.toString(),
                "REPORT_TOKEN_VALIDITY_DAYS" to TOKEN_VALIDITY.toString()
            )

            withEnvironment(environment) {
                main()

                MockProvider.register { mockkClass(it) }

                block()
            }
        }
    }
}
