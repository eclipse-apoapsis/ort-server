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

import kotlin.time.Duration.Companion.days

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.services.OrtRunService
import org.eclipse.apoapsis.ortserver.tasks.Task

import org.slf4j.LoggerFactory

/**
 * A task class that handles the deletion of old ORT runs according to the configured data retention policy. The task
 * delegates to [OrtRunService] to trigger the deletion of old ORT runs finished before the number of days defined by
 * the retention policy.
 *
 * The task is configured in a section named `dataRetention`. It evaluates the following properties:
 * - `ortRunDays`: The number of days to keep ORT runs. This can be overridden by the `DATA_RETENTION_ORT_RUN_DAYS`
 *   environment variable.
 */
class DeleteOldOrtRunsTask(
    /** The service for deleting old runs. */
    private val ortRunService: OrtRunService,

    /** The threshold for the maximum age of ORT runs to delete. */
    private val ortRunMaxAgeThreshold: Instant
) : Task {
    companion object {
        private val logger = LoggerFactory.getLogger(DeleteOldOrtRunsTask::class.java)

        /**
         * Create a new instance of [DeleteOldOrtRunsTask] with the configuration obtained from the given
         * [configManager] that uses the given [ortRunService].
         */
        fun create(configManager: ConfigManager, ortRunService: OrtRunService): DeleteOldOrtRunsTask {
            val ortRunMaxAgeThreshold = Clock.System.now() - configManager.getInt("dataRetention.ortRunDays").days
            return DeleteOldOrtRunsTask(ortRunService, ortRunMaxAgeThreshold)
        }
    }

    override suspend fun execute() {
        logger.info("Deleting ORT runs that finished before $ortRunMaxAgeThreshold.")

        ortRunService.deleteRunsCreatedBefore(ortRunMaxAgeThreshold)
    }
}
