/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.comparables.shouldBeLessThan

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot

import kotlin.math.abs
import kotlin.time.Duration.Companion.days

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.services.OrtRunService

class DeleteOldOrtRunsTaskTest : StringSpec({
    "Old ORT runs should be deleted according to the configured retention policy" {
        val ortRunRetentionDays = 77
        val configMap = mapOf("dataRetention.ortRunDays" to ortRunRetentionDays.toString())
        val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))
        val ortRunService = createOrtRunService()

        val task = DeleteOldOrtRunsTask.create(configManager, ortRunService)
        task.execute()

        checkDeleteInvocation(ortRunService, ortRunRetentionDays)
    }

    "Old ORT runs should be deleted according to the config overridden by an environment variable" {
        val ortRunRetentionDays = 44
        val envMap = mapOf("DATA_RETENTION_ORT_RUN_DAYS" to ortRunRetentionDays.toString())

        withEnvironment(envMap) {
            ConfigFactory.invalidateCaches()
            val configManager = ConfigManager.create(ConfigFactory.load())
            val ortRunService = createOrtRunService()

            val task = DeleteOldOrtRunsTask.create(configManager, ortRunService)
            task.execute()

            checkDeleteInvocation(ortRunService, ortRunRetentionDays)
        }
    }
})

/**
 * Create a mock for [OrtRunService] and prepare it to expect a call to delete old ORT runs.
 */
private fun createOrtRunService(): OrtRunService =
    mockk<OrtRunService> {
    coEvery { deleteRunsCreatedBefore(any()) } just runs
}

/**
 * Check whether the given [ortRunService] mock was correctly invoked to delete ORT runs older than [expectedDays].
 */
private fun checkDeleteInvocation(ortRunService: OrtRunService, expectedDays: Int) {
    val slot = slot<Instant>()
    coVerify {
        ortRunService.deleteRunsCreatedBefore(capture(slot))
    }

    val delta = abs((Clock.System.now() - expectedDays.days - slot.captured).inWholeSeconds)
    delta shouldBeLessThan 5
}
