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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.services.ortrun.OrphanRemovalService
import org.eclipse.apoapsis.ortserver.tasks.Task

import org.slf4j.LoggerFactory

/**
 * A task class that handles the deletion of database entities orphaned after deletion of ORT run. The task delegates
 * to [OrphanRemovalService] to trigger the deletion of entities that are no longer referencing ORT runs deleted
 * during [DeleteOldOrtRunsTask] run.
 */
class DeleteOrphanedEntitiesTask(
    /** The object for accessing configuration data. */
    private val config: ConfigManager,

    /** Service for deleting entities orphaned after ORT run deletion */
    private var orphanRemovalService: OrphanRemovalService
) : Task {
    companion object {
        private val logger = LoggerFactory.getLogger(DeleteOrphanedEntitiesTask::class.java)

        /** The section containing configuration for orphaned entity deletion. */
        private val ORPHAN_SECTION = Path("orphanHandlers")

        /**
         * Create a new instance of [DeleteOldOrtRunsTask].
         */
        fun create(config: ConfigManager, orphanRemovalService: OrphanRemovalService): DeleteOrphanedEntitiesTask {
            return DeleteOrphanedEntitiesTask(config, orphanRemovalService)
        }
    }

    override suspend fun execute() {
        logger.info("Deleting orphaned ORT run entities.")
        orphanRemovalService.deleteRunsOrphanedEntities(config.subConfig(ORPHAN_SECTION))
    }
}
