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

package org.eclipse.apoapsis.ortserver.workers.advisor

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
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.testing.MessageReceiverFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME
import org.eclipse.apoapsis.ortserver.workers.common.RunResult

import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock

private const val ADVISOR_JOB_ID = 1L
private const val TRACE_ID = "42"
private const val VULNERABLE_CODE_API_KEY = "vulnerable_code_api_key"

private val messageHeader = MessageHeader(TRACE_ID, 23)

private val advisorRequest = AdvisorRequest(
    advisorJobId = ADVISOR_JOB_ID
)

class AdvisorEndpointTest : KoinTest, StringSpec() {
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

        "The worker should be correctly configured" {
            runEndpointTest {
                val worker by inject<AdvisorWorker>()

                worker shouldNot beNull()
            }
        }

        "A message to advice a project should be processed" {
            runEndpointTest {
                declareMock<AdvisorWorker> {
                    coEvery { run(ADVISOR_JOB_ID, TRACE_ID) } returns RunResult.Success
                }
            }

            sendAdvisorRequest()

            val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
            resultMessage.header shouldBe messageHeader
            resultMessage.payload shouldBe AdvisorWorkerResult(ADVISOR_JOB_ID)
        }

        "An error message should be sent back in case of a processing error" {
            runEndpointTest {
                declareMock<AdvisorWorker> {
                    coEvery { run(ADVISOR_JOB_ID, TRACE_ID) } returns
                            RunResult.Failed(IllegalStateException("Test exception"))
                }

                sendAdvisorRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe AdvisorWorkerError(ADVISOR_JOB_ID, "Test exception")
            }
        }

        "A 'run finished with issues' message should be sent when ORT issues are over the threshold" {
            runEndpointTest {
                declareMock<AdvisorWorker> {
                    coEvery { run(ADVISOR_JOB_ID, TRACE_ID) } returns RunResult.FinishedWithIssues
                }

                sendAdvisorRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe AdvisorWorkerResult(ADVISOR_JOB_ID, true)
            }
        }

        "No response should be sent if the request is ignored" {
            runEndpointTest {
                declareMock<AdvisorWorker> {
                    coEvery { run(ADVISOR_JOB_ID, TRACE_ID) } returns RunResult.Ignored
                }

                sendAdvisorRequest()

                MessageSenderFactoryForTesting.expectNoMessage(OrchestratorEndpoint)
            }
        }
    }

    /**
     * Simulate an incoming request to advice a project.
     */
    private suspend fun sendAdvisorRequest() {
        mockkTransaction {
            val message = Message(messageHeader, advisorRequest)
            MessageReceiverFactoryForTesting.receive(AdvisorEndpoint, message)
        }
    }

    /**
     * Run [block] as a test for the Analyzer endpoint. Start the endpoint with a configuration that selects the
     * testing transport. Then execute the given [block].
     */
    private suspend fun runEndpointTest(block: suspend () -> Unit) {
        withMockDatabaseModule {
            val environment = mapOf(
                "ADVISOR_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ADVISOR_SECRET_PROVIDER" to ConfigSecretProviderFactoryForTesting.NAME,
                "VULNERABLE_CODE_API_KEY" to VULNERABLE_CODE_API_KEY
            )

            withEnvironment(environment) {
                main()

                MockProvider.register { mockkClass(it) }

                block()
            }
        }
    }
}
