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
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender

import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeEachListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

import org.eclipse.apoapsis.ortserver.utils.logging.JobStatusLogging.JOB_STATUS_LOGGER_NAME

import org.slf4j.LoggerFactory

/**
 * A test extension that supports testing the job status logging functionality. This extension can be used to test
 * job implementations to verify that they produce the expected log output for their execution status. The extension
 * defines a [statusLog] function that can be used to retrieve the structured log output that was generated.
 */
class StatusLoggingTestExtension : BeforeSpecListener, AfterSpecListener, BeforeEachListener {
    private val logger = LoggerFactory.getLogger(JOB_STATUS_LOGGER_NAME) as Logger

    /** An appender to collect log events for the job status logger. */
    private val listAppender = ListAppender<ILoggingEvent>().apply { start() }

    override suspend fun beforeSpec(spec: Spec) {
        logger.addAppender(listAppender)
    }

    override suspend fun afterSpec(spec: Spec) {
        logger.detachAppender(listAppender)
    }

    override suspend fun beforeEach(testCase: TestCase) {
        listAppender.list.clear()
    }

    /**
     * Return the single log event created for the job status as a [JsonObject].
     */
    fun statusLog(): JsonObject {
        val event = listAppender.list.single { it.level == Level.INFO }
        event.loggerName shouldBe JOB_STATUS_LOGGER_NAME

        return Json.parseToJsonElement(event.message).jsonObject
    }
}
