/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.utils.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.util.LogbackMDCAdapter

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OrtServerJsonEncoderTest : WordSpec({
    "encode" should {
        "render a throwable as a single 'throwable' string field" {
            val cause = IllegalStateException("the root cause")
            val throwable = RuntimeException("something went wrong", cause)

            val json = encodeEvent("Processing failed for {}", throwable, "item-42")

            json.jsonObject["throwable"]?.jsonPrimitive.shouldNotBeNull {
                content shouldStartWith "java.lang.RuntimeException: something went wrong"
                content shouldContain "Caused by: java.lang.IllegalStateException: the root cause"
                content shouldContain "\tat "
            }
        }

        "produce valid JSON even though the exception spans multiple lines" {
            // Parsing the output without an exception already proves the multi-line stack trace is escaped correctly.
            val json = encodeEvent(message = "boom", throwable = RuntimeException("boom"))

            json.jsonObject["throwable"]?.jsonPrimitive?.content.shouldNotBeNull {
                this shouldContain "\n"
            }
        }

        "not add a 'throwable' field if the event has no throwable" {
            val json = encodeEvent(message = "no error here", throwable = null)

            json.jsonObject["throwable"] should beNull()
        }

        "not corrupt the output when the message contains JSON special characters" {
            val json = encodeEvent("value is \"{}\"", RuntimeException("boom"), "a\\b")

            json.jsonObject["formattedMessage"]?.jsonPrimitive?.content shouldBe "value is \"a\\b\""
            json.jsonObject["throwable"]?.jsonPrimitive?.content.orEmpty() shouldNotContain "\u0000"
        }
    }
})

private fun encodeEvent(message: String, throwable: Throwable?, vararg arguments: Any?): JsonElement {
    val loggerContext = LoggerContext().apply {
        mdcAdapter = LogbackMDCAdapter()
    }

    val logger = loggerContext.getLogger("org.eclipse.apoapsis.ortserver.TestLogger")

    val event = LoggingEvent(
        OrtServerJsonEncoderTest::class.java.name,
        logger,
        Level.ERROR,
        message,
        throwable,
        if (arguments.isEmpty()) null else arrayOf(*arguments)
    )

    val encoder = OrtServerJsonEncoder().apply {
        setWithArguments(false)
        setWithContext(false)
        setWithFormattedMessage(true)
        setWithMessage(false)
        start()
    }

    val line = String(encoder.encode(event), Charsets.UTF_8)

    return Json.parseToJsonElement(line)
}
