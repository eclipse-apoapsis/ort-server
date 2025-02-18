/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.notifier

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
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
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

private const val NOTIFIER_JOB_ID = 1L
private const val TRACE_ID = "42"

private val messageHeader = MessageHeader(TRACE_ID, 25)

private val notifierRequest = NotifierRequest(NOTIFIER_JOB_ID)

class NotifierEndpointTest : KoinTest, StringSpec() {
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

        "A notify message should be processed" {
            runEndpointTest {
                declareMock<NotifierWorker> {
                    coEvery { run(NOTIFIER_JOB_ID, TRACE_ID) } returns RunResult.Success
                }

                sendNotifierRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe NotifierWorkerResult(NOTIFIER_JOB_ID)
            }
        }

        "An error message should be send back in case of a processing error" {
            runEndpointTest {
                declareMock<NotifierWorker> {
                    coEvery { run(NOTIFIER_JOB_ID, TRACE_ID) } returns
                            RunResult.Failed(IllegalStateException("Test Exception"))
                }

                sendNotifierRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe NotifierWorkerError(NOTIFIER_JOB_ID, "Test Exception")
            }
        }

        "No response should be sent if the request is ignored" {
            runEndpointTest {
                declareMock<NotifierWorker> {
                    coEvery { run(NOTIFIER_JOB_ID, TRACE_ID) } returns RunResult.Ignored
                }

                sendNotifierRequest()

                MessageSenderFactoryForTesting.expectNoMessage(OrchestratorEndpoint)
            }
        }

        "Dependency injection is correctly set up" {
            runEndpointTest {
                val worker by inject<NotifierWorker>()

                worker shouldNot beNull()
            }
        }
    }

    private suspend fun sendNotifierRequest() {
        mockkTransaction {
            val message = Message(messageHeader, notifierRequest)
            MessageReceiverFactoryForTesting.receive(NotifierEndpoint, message)
        }
    }

    private suspend fun runEndpointTest(block: suspend () -> Unit) {
        withMockDatabaseModule {
            val environment = mapOf(
                "NOTIFIER_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
                "NOTIFIER_SECRET_PROVIDER" to ConfigSecretProviderFactoryForTesting.NAME
            )

            withEnvironment(environment) {
                main()

                MockProvider.register { mockkClass(it) }

                block()
            }
        }
    }
}
