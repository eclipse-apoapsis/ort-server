/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.evaluator

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.every
import io.mockk.mockkClass

import org.koin.core.component.inject
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock

import org.ossreviewtoolkit.server.config.ConfigSecretProviderFactoryForTesting
import org.ossreviewtoolkit.server.dao.test.mockkTransaction
import org.ossreviewtoolkit.server.dao.test.verifyDatabaseModuleIncluded
import org.ossreviewtoolkit.server.dao.test.withMockDatabaseModule
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorRequest
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerResult
import org.ossreviewtoolkit.server.transport.EvaluatorEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.testing.MessageReceiverFactoryForTesting
import org.ossreviewtoolkit.server.transport.testing.MessageSenderFactoryForTesting
import org.ossreviewtoolkit.server.transport.testing.TEST_TRANSPORT_NAME
import org.ossreviewtoolkit.server.workers.common.RunResult

private const val EVALUATOR_JOB_ID = 1L
private const val TOKEN = "token"
private const val TRACE_ID = "42"

private val messageHeader = MessageHeader(TOKEN, TRACE_ID)

private val evaluatorRequest = EvaluatorRequest(EVALUATOR_JOB_ID)

class EvaluatorEndpointTest : KoinTest, StringSpec() {
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

        "A message to evaluate a project should be processed" {
            runEndpointTest {
                declareMock<EvaluatorWorker> {
                    every { run(EVALUATOR_JOB_ID, TRACE_ID) } returns RunResult.Success
                }

                sendEvaluatorRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe EvaluatorWorkerResult(EVALUATOR_JOB_ID)
            }
        }

        "An error message should be sent back in case of a processing error" {
            runEndpointTest {
                declareMock<EvaluatorWorker> {
                    every { run(EVALUATOR_JOB_ID, TRACE_ID) } returns
                            RunResult.Failed(IllegalStateException("Test Exception"))
                }

                sendEvaluatorRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe EvaluatorWorkerError(EVALUATOR_JOB_ID)
            }
        }

        "No response should be sent if the request is ignored" {
            runEndpointTest {
                declareMock<EvaluatorWorker> {
                    every { run(EVALUATOR_JOB_ID, TRACE_ID) } returns RunResult.Ignored
                }

                sendEvaluatorRequest()

                MessageSenderFactoryForTesting.expectNoMessage(OrchestratorEndpoint)
            }
        }

        "Dependency injection is correctly set up" {
            runEndpointTest {
                val worker by inject<EvaluatorWorker>()

                worker shouldNot beNull() // Just test whether the object could be constructed.
            }
        }
    }

    /**
     * Simulate an incoming request to evaluate a project.
     */
    private fun sendEvaluatorRequest() {
        mockkTransaction {
            val message = Message(messageHeader, evaluatorRequest)
            MessageReceiverFactoryForTesting.receive(EvaluatorEndpoint, message)
        }
    }

    /**
     * Run [block] as a test for the Evaluator endpoint. Start the endpoint with a configuration that selects the
     * testing transport. Then execute the given [block].
     */
    private fun runEndpointTest(block: () -> Unit) {
        withMockDatabaseModule {
            val environment = mapOf(
                "EVALUATOR_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "EVALUATOR_SECRET_PROVIDER" to ConfigSecretProviderFactoryForTesting.NAME
            )

            withEnvironment(environment) {
                main()

                MockProvider.register { mockkClass(it) }

                block()
            }
        }
    }
}
