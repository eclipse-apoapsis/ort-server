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

package org.ossreviewtoolkit.server.transport

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.model.orchestrator.AnalyzeRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult

class MessagePublisherTest : StringSpec({
    "Messages to the Orchestrator can be published" {
        val config = createTestSenderConfig(OrchestratorEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, AnalyzerWorkerResult(42))
        publisher.publish(OrchestratorEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(OrchestratorEndpoint) shouldBe message
    }

    "Messages to the Analyzer worker can be published" {
        val config = createTestSenderConfig(AnalyzerEndpoint)

        val publisher = MessagePublisher(config)

        val message = Message(HEADER, AnalyzeRequest(1))
        publisher.publish(AnalyzerEndpoint, message)

        MessageSenderFactoryForTesting.expectMessage(AnalyzerEndpoint) shouldBe message
    }
})

/** A test message header. */
private val HEADER = MessageHeader("testToken", "testTraceId")

/**
 * Create a [Config] that selects the test transport for the sender to the given [endpoint].
 */
private fun createTestSenderConfig(endpoint: Endpoint<*>): Config {
    val properties = mapOf("${endpoint.configPrefix}.sender.type" to TEST_TRANSPORT_NAME)
    return ConfigFactory.parseMap(properties)
}
