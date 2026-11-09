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

package org.eclipse.apoapsis.ortserver.tasks.impl

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

import org.eclipse.apoapsis.ortserver.components.projections.ProjectionRegistry
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.utils.logging.JobStatusLogging
import org.eclipse.apoapsis.ortserver.utils.logging.StatusLoggingTestExtension

class DeleteUnusedProjectionsTaskTest : StringSpec({
    val logExtension = extension(StatusLoggingTestExtension())

    "Projections that were not accessed within the retention period should be deleted" {
        val configManager = ConfigManager.create(ConfigFactory.parseMap(mapOf("projections.retentionDays" to "12")))
        val thresholdSlot = slot<Instant>()
        val registry = mockk<ProjectionRegistry> {
            coEvery { deleteNotAccessedSince(capture(thresholdSlot)) } returns 3
        }

        DeleteUnusedProjectionsTask.create(configManager, registry).execute()

        coVerify(exactly = 1) { registry.deleteNotAccessedSince(any()) }
        val expectedThreshold = Clock.System.now() - 12.days
        (expectedThreshold - thresholdSlot.captured).absoluteValue shouldBeLessThan 1.minutes

        val status = logExtension.statusLog()
        status[JobStatusLogging.STATUS_KEY]?.jsonPrimitive?.content shouldBe JobStatusLogging.STATUS_SUCCESS
        status[JobStatusLogging.PROCESSED_COUNT_KEY]?.jsonPrimitive?.int shouldBe 3
    }

    "The retention period must be longer than the interval of access updates" {
        val configManager = ConfigManager.create(ConfigFactory.parseMap(mapOf("projections.retentionDays" to "1")))

        shouldThrow<IllegalArgumentException> {
            DeleteUnusedProjectionsTask.create(configManager, mockk())
        }
    }
})
