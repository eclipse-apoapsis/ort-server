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

package org.eclipse.apoapsis.ortserver.workers.config

import io.kotest.core.spec.style.StringSpec
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
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
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

class EndpointTest : KoinTest, StringSpec() {
    init {
        afterEach {
            stopKoin()
            MessageReceiverFactoryForTesting.reset()
        }

        "The database module should be added" {
            runEndpointTest {
                verifyDatabaseModuleIncluded()
            }
        }

        "The worker should be correctly configured" {
            runEndpointTest {
                val worker by inject<ConfigWorker>()

                worker shouldNot beNull()
            }
        }

        "A successful execution of the Config worker should be handled" {
            runEndpointTest {
                declareMock<ConfigWorker> {
                    coEvery { run(RUN_ID) } returns RunResult.Success
                }

                sendConfigRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe ConfigWorkerResult(RUN_ID)
            }
        }

        "A failure result should be sent back if the worker execution is not successful" {
            runEndpointTest {
                declareMock<ConfigWorker> {
                    coEvery { run(RUN_ID) } returns RunResult.Failed(IllegalStateException("Test exception"))
                }

                sendConfigRequest()

                val resultMessage = MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint)
                resultMessage.header shouldBe messageHeader
                resultMessage.payload shouldBe ConfigWorkerError(RUN_ID, "Test exception")
            }
        }
    }
}

private const val RUN_ID = 20230803092449L
private const val TRACE_ID = "trace-id"

private val messageHeader = MessageHeader(TRACE_ID, 24)

private val configRequest = ConfigRequest(RUN_ID)

/**
 * Simulate an incoming request to check the configuration of an ORT run.
 */
private suspend fun sendConfigRequest() {
    mockkTransaction {
        val message = Message(messageHeader, configRequest)
        MessageReceiverFactoryForTesting.receive(ConfigEndpoint, message)
    }
}

/**
 * Run [block] as a test for the Analyzer endpoint. Start the endpoint with a configuration that selects the
 * testing transport. Then execute the given [block].
 */
private suspend fun runEndpointTest(block: suspend () -> Unit) {
    withMockDatabaseModule {
        val environment = mapOf(
            "CONFIG_RECEIVER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
            "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
            "CONFIG_SECRET_PROVIDER" to ConfigSecretProviderFactoryForTesting.NAME
        )

        withEnvironment(environment) {
            main()

            MockProvider.register { mockkClass(it) }

            block()
        }
    }
}
