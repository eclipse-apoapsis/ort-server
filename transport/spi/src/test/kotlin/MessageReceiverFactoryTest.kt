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

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain

import io.mockk.mockk

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.transport.testing.MessageReceiverFactoryForTesting
import org.ossreviewtoolkit.server.transport.testing.TEST_TRANSPORT_NAME

class MessageReceiverFactoryTest : StringSpec({
    val typePropertyPath = "${MessageReceiverFactory.CONFIG_PREFIX}.${MessageReceiverFactory.TYPE_PROPERTY}"

    afterAny {
        MessageReceiverFactoryForTesting.reset()
    }

    "The correct factory should be invoked" {
        val handler = mockk<EndpointHandler<OrchestratorMessage>>()
        val config = ConfigFactory.parseMap(
            mapOf(
                "${OrchestratorEndpoint.configPrefix}.$typePropertyPath" to TEST_TRANSPORT_NAME
            )
        )
        val configManager = ConfigManager.create(config)

        MessageReceiverFactory.createReceiver(OrchestratorEndpoint, configManager, handler)

        MessageReceiverFactoryForTesting.createdEndpoint shouldBe OrchestratorEndpoint
        MessageReceiverFactoryForTesting.createdHandler shouldBe handler

        val expectedConfig = config.getConfig("orchestrator.receiver")
        MessageReceiverFactoryForTesting.createdConfig?.entrySet() shouldBe expectedConfig.entrySet()
    }

    "An exception should be thrown for a non-existing MessageReceiverFactory" {
        val invalidFactoryName = "a non existing message receiver factory"
        val config = ConfigFactory.parseMap(
            mapOf(
                "${AnalyzerEndpoint.configPrefix}.$typePropertyPath" to invalidFactoryName
            )
        )
        val configManager = ConfigManager.create(config)

        val exception = shouldThrow<IllegalStateException> {
            MessageReceiverFactory.createReceiver(AnalyzerEndpoint, configManager, mockk())
        }

        exception.message should contain(invalidFactoryName)
    }
})
