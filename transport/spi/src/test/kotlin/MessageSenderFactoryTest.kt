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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain
import io.kotest.matchers.types.shouldBeInstanceOf

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.transport.testing.MessageSenderForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME

class MessageSenderFactoryTest : StringSpec({
    val typePropertyPath = "${MessageSenderFactory.CONFIG_PREFIX}.${MessageSenderFactory.TYPE_PROPERTY}"

    "A correct MessageSender should be created" {
        val config = ConfigFactory.parseMap(
            mapOf(
                "${AnalyzerEndpoint.configPrefix}.$typePropertyPath" to TEST_TRANSPORT_NAME
            )
        )
        val configManager = ConfigManager.create(config)

        val sender = MessageSenderFactory.createSender(AnalyzerEndpoint, configManager)

        sender.shouldBeInstanceOf<MessageSenderForTesting<AnalyzerRequest>>()
        sender.endpoint shouldBe AnalyzerEndpoint

        val expectedConfig = config.getConfig("analyzer.sender")
        sender.config.entrySet() shouldBe expectedConfig.entrySet()
    }

    "An exception should be thrown for a non-existing MessageSenderFactory" {
        val invalidFactoryName = "a non existing message sender factory"
        val config = ConfigFactory.parseMap(
            mapOf(
                "${OrchestratorEndpoint.configPrefix}.$typePropertyPath" to invalidFactoryName
            )
        )
        val configManager = ConfigManager.create(config)

        val exception = shouldThrow<IllegalStateException> {
            MessageSenderFactory.createSender(OrchestratorEndpoint, configManager)
        }

        exception.message should contain(invalidFactoryName)
    }
})
