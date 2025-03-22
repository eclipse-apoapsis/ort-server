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

package org.eclipse.apoapsis.ortserver.transport

import com.typesafe.config.Config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.transport.testing.MessageReceiverFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting

import org.koin.core.component.inject
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

class EndpointComponentTest : StringSpec({
    afterAny {
        MessageReceiverFactoryForTesting.reset()

        stopKoin()
    }

    "Incoming messages are passed to the handler function" {
        val message = Message(HEADER, AnalyzerWorkerResult(1))

        val component = EndpointForTesting()
        component.start()

        MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

        component.receivedMessages shouldContainExactly listOf(message)
    }

    "A MessagePublisher is available via dependency injection" {
        val message = Message(HEADER, AnalyzerWorkerResult(1))

        val component = EndpointForTesting()
        component.start()

        MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, message)

        val publishedMessage = MessageSenderFactoryForTesting.expectMessage(AnalyzerEndpoint)
        publishedMessage.header shouldBe HEADER
        publishedMessage.payload shouldBe AnalyzerRequest(42)
    }

    "Custom services are available via dependency injection" {
        val component = EndpointForTesting()
        component.start()

        MessageReceiverFactoryForTesting.receive(OrchestratorEndpoint, Message(HEADER, AnalyzerWorkerError(123)))

        MessageSenderFactoryForTesting.expectMessage(AnalyzerEndpoint) // Skip response message.
        val serviceMessage = MessageSenderFactoryForTesting.expectMessage(AnalyzerEndpoint)

        serviceMessage.header shouldBe HEADER
        serviceMessage.payload shouldBe AnalyzerRequest(127)
    }
})

/**
 * A test [EndpointComponent] implementation that uses functionality provided by the base class. This is executed by
 * tests to check whether the base class works as expected.
 */
private class EndpointForTesting : EndpointComponent<OrchestratorMessage>(OrchestratorEndpoint) {
    private val publisher by inject<MessagePublisher>()

    private val service by inject<CustomProcessingService>()

    /** Stores the messages passed to the endpoint handler function. */
    private val receivedMessagesList = mutableListOf<Message<OrchestratorMessage>>()

    /** Allows querying the messages that have been passed to the endpoint handler function. */
    val receivedMessages: List<Message<OrchestratorMessage>>
        get() = receivedMessagesList

    /**
     * A handler function that records the received message and executes further functionality of the base class.
     */
    override val endpointHandler: EndpointHandler<OrchestratorMessage> = { message ->
        receivedMessagesList += message

        val response = Message(message.header, AnalyzerRequest(42))
        publisher.publish(AnalyzerEndpoint, response)

        service.process()

        EndpointHandlerResult.CONTINUE
    }

    override fun customModules(): List<Module> {
        val customModule = module {
            singleOf(::CustomProcessingService)
        }

        return listOf(customModule)
    }
}

/**
 * A test service implementation that is injected into the test component. This is used to test whether dependency
 * injection works correctly and supports custom modules.
 */
private class CustomProcessingService(
    val publisher: MessagePublisher,

    val config: Config
) {
    fun process() {
        val responseStatus = config.getInt("responseCode")

        val message = Message(HEADER, AnalyzerRequest(responseStatus.toLong()))
        publisher.publish(AnalyzerEndpoint, message)
    }
}

/** A test message header. */
private val HEADER = MessageHeader("testTraceId", 42)
