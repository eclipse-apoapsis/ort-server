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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.util.concurrent.atomic.AtomicInteger

import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

import org.eclipse.apoapsis.ortserver.utils.logging.JobStatusLogging.runWithStatusLogging

import org.slf4j.MDC

class JobStatusLoggingTest : WordSpec({
    val logExtension = extension(StatusLoggingTestExtension())

    "runWithStatusLogging" should {
        "set the provided MDC elements" {
            val executed = AtomicInteger()

            val result = runWithStatusLogging(
                "mdcJob",
                StandardMdcKeys.TRACE_ID to "tx-1", CustomMdcKey("key2") to "val2"
            ) {
                MDC.get(StandardMdcKeys.TRACE_ID.key) shouldBe "tx-1"
                MDC.get("key2") shouldBe "val2"
                executed.incrementAndGet()
            }

            result shouldBe true
            executed.get() shouldBe 1
        }

        "set the job name as component in the MDC context" {
            val jobName = "testJobComponent"

            runWithStatusLogging(jobName) {
                MDC.get(StandardMdcKeys.COMPONENT.key) shouldBe jobName
            }
        }

        "log status information for a successful job execution" {
            runWithStatusLogging("statusLoggingTest") {}

            val status = logExtension.statusLog()
            status[JobStatusLogging.STATUS_KEY]?.jsonPrimitive?.content shouldBe JobStatusLogging.STATUS_SUCCESS
        }

        "handle and log status information for a failed job execution" {
            val exception = RuntimeException("Test exception")

            val result = runWithStatusLogging("statusLoggingFailureTest") {
                throw exception
            }

            result shouldBe false

            val status = logExtension.statusLog()
            status[JobStatusLogging.STATUS_KEY]?.jsonPrimitive?.content shouldBe JobStatusLogging.STATUS_FAILURE
            val error = status[JobStatusLogging.ERROR_KEY]?.jsonPrimitive?.content.orEmpty()
            error shouldContain exception::class.java.simpleName
            error shouldContain exception.message!!
        }

        "support adding additional fields to the job status log" {
            runWithStatusLogging("statusLoggingAdditionalFieldsTest") { builder ->
                builder.put("field1", JsonPrimitive("value1"))
                builder.put("field2", JsonPrimitive(42))
            }

            val status = logExtension.statusLog()
            status[JobStatusLogging.STATUS_KEY]?.jsonPrimitive?.content shouldBe JobStatusLogging.STATUS_SUCCESS
            status["field1"]?.jsonPrimitive?.content shouldBe "value1"
            status["field2"]?.jsonPrimitive?.int shouldBe 42
        }

        "provide a field for the job execution time" {
            runWithStatusLogging("statusLoggingExecutionTimeTest") {
                delay(10.milliseconds)
            }

            val status = logExtension.statusLog()
            val executionTime = status[JobStatusLogging.EXECUTION_TIME_KEY]?.jsonPrimitive?.int.shouldNotBeNull()
            executionTime shouldBeGreaterThanOrEqual 10
        }

        "provide a field for the completion time of the job" {
            runWithStatusLogging("statusLoggingCompletionTimeTest") {}

            val status = logExtension.statusLog()
            val completionTime = Instant.parse(
                status[JobStatusLogging.TIMESTAMP_KEY]?.jsonPrimitive?.content.shouldNotBeNull()
            )

            val deltaT = Clock.System.now() - completionTime
            abs(deltaT.inWholeSeconds) shouldBeLessThan 5
        }

        "fill the field for the job execution times even on failure" {
            val exception = IllegalStateException("Failure for execution time test")

            runWithStatusLogging("statusLoggingExecutionTimeFailureTest") {
                throw exception
            }

            val status = logExtension.statusLog()
            status[JobStatusLogging.TIMESTAMP_KEY].shouldNotBeNull()
            val executionTime = status[JobStatusLogging.EXECUTION_TIME_KEY]?.jsonPrimitive?.int.shouldNotBeNull()
            executionTime shouldBeGreaterThanOrEqual 0
        }
    }
})
