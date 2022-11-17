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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain
import io.kotest.matchers.types.shouldBeInstanceOf

import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.server.model.orchestrator.AnalyzeRequest
import org.ossreviewtoolkit.server.transport.testing.MessageSenderForTesting
import org.ossreviewtoolkit.server.transport.testing.TEST_TRANSPORT_NAME

class MessageSenderFactoryTest : StringSpec({
    "A correct MessageSender should be created" {
        val config = mockk<Config>()
        every {
            config.getString("analyzer.${MessageSenderFactory.SENDER_TYPE_PROPERTY}")
        } returns TEST_TRANSPORT_NAME

        val sender = MessageSenderFactory.createSender(AnalyzerEndpoint, config)

        sender.shouldBeInstanceOf<MessageSenderForTesting<AnalyzeRequest>>()
        sender.endpoint shouldBe AnalyzerEndpoint
        sender.config shouldBe config
    }

    "An exception should be thrown for a non-existing MessageSenderFactory" {
        val invalidFactoryName = "a non existing message sender factory"
        val config = mockk<Config>()
        every {
            config.getString("orchestrator.${MessageSenderFactory.SENDER_TYPE_PROPERTY}")
        } returns invalidFactoryName

        val exception = shouldThrow<IllegalStateException> {
            MessageSenderFactory.createSender(OrchestratorEndpoint, config)
        }

        exception.message should contain(invalidFactoryName)
    }
})
