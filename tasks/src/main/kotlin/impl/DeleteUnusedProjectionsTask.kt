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

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

import kotlinx.serialization.json.JsonPrimitive

import org.eclipse.apoapsis.ortserver.components.projections.ProjectionRegistry
import org.eclipse.apoapsis.ortserver.components.projections.ProjectionService
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.tasks.Task
import org.eclipse.apoapsis.ortserver.utils.logging.JobStatusLogging

import org.slf4j.LoggerFactory

/**
 * A task class that deletes projections that have not been accessed for a configured number of days. Deleted
 * projections are built again when they are requested the next time.
 *
 * The task is configured in a section named `projections`. It evaluates the following properties:
 * - `retentionDays`: The number of days to keep projections after their last access. This can be overridden by the
 *   `PROJECTIONS_RETENTION_DAYS` environment variable. It must be longer than
 *   [ProjectionService.ACCESS_UPDATE_INTERVAL], so that projections are not deleted while they are requested.
 */
class DeleteUnusedProjectionsTask(
    /** The registry for deleting projections. */
    private val projectionRegistry: ProjectionRegistry,

    /** The threshold for the last access of projections to delete. */
    private val lastAccessThreshold: Instant
) : Task {
    companion object {
        const val TASK_NAME = "deleteUnusedProjectionsTask"

        private val logger = LoggerFactory.getLogger(DeleteUnusedProjectionsTask::class.java)

        /**
         * Create a new instance of [DeleteUnusedProjectionsTask] with the configuration obtained from the given
         * [configManager] that uses the given [projectionRegistry].
         */
        fun create(configManager: ConfigManager, projectionRegistry: ProjectionRegistry): DeleteUnusedProjectionsTask {
            val retention = configManager.getInt("projections.retentionDays").days

            require(retention > ProjectionService.ACCESS_UPDATE_INTERVAL) {
                "The retention of projections must be longer than ${ProjectionService.ACCESS_UPDATE_INTERVAL}, but " +
                        "was $retention."
            }

            val lastAccessThreshold = Clock.System.now() - retention
            return DeleteUnusedProjectionsTask(projectionRegistry, lastAccessThreshold)
        }
    }

    override suspend fun execute() {
        JobStatusLogging.runWithStatusLogging(TASK_NAME) { jsonBuilder ->
            logger.info("Deleting projections that were not accessed since $lastAccessThreshold.")

            val deletedCount = projectionRegistry.deleteNotAccessedSince(lastAccessThreshold)

            jsonBuilder.put(JobStatusLogging.PROCESSED_COUNT_KEY, JsonPrimitive(deletedCount))
        }
    }
}
