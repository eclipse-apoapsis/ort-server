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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME

class MessagePublisherTest : StringSpec({
    "Messages to the Orchestrator can be published" {
        val config = createTestSenderConfig(OrchestratorEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, AnalyzerWorkerResult(42))
        publisher.publish(OrchestratorEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint) shouldBe message
    }

    "Messages to the Config worker can be published" {
        val config = createTestSenderConfig(ConfigEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, ConfigRequest(1))
        publisher.publish(ConfigEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(ConfigEndpoint) shouldBe message
    }

    "Messages to the Analyzer worker can be published" {
        val config = createTestSenderConfig(AnalyzerEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, AnalyzerRequest(1))
        publisher.publish(AnalyzerEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(AnalyzerEndpoint) shouldBe message
    }

    "Messages to the Advisor worker can be published" {
        val config = createTestSenderConfig(AdvisorEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, AdvisorRequest(1))
        publisher.publish(AdvisorEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(AdvisorEndpoint) shouldBe message
    }

    "Messages to the Scanner worker can be published" {
        val config = createTestSenderConfig(ScannerEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, ScannerRequest(1))
        publisher.publish(ScannerEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(ScannerEndpoint) shouldBe message
    }

    "Messages to the Evaluator worker can be published" {
        val config = createTestSenderConfig(EvaluatorEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, EvaluatorRequest(1))
        publisher.publish(EvaluatorEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(EvaluatorEndpoint) shouldBe message
    }

    "Messages to the Reporter worker can be published" {
        val config = createTestSenderConfig(ReporterEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, ReporterRequest(1))
        publisher.publish(ReporterEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(ReporterEndpoint) shouldBe message
    }
})

/** A test message header. */
private val HEADER = MessageHeader("testTraceId", 17)

/**
 * Create a [ConfigManager] that selects the test transport for the sender to the given [endpoint].
 */
private fun createTestSenderConfig(endpoint: Endpoint<*>): ConfigManager {
    val properties = mapOf("${endpoint.configPrefix}.sender.type" to TEST_TRANSPORT_NAME)
    return ConfigManager.create(ConfigFactory.parseMap(properties))
}
