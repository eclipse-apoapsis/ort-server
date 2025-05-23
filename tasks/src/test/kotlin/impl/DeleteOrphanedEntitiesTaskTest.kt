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

import io.kotest.core.spec.style.StringSpec

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.services.ortrun.OrphanRemovalService

class DeleteOrphanedEntitiesTaskTest : StringSpec({
    "DeleteOrphanedEntitiesTask should start orphaned entities deletion service" {
        val orphanRemovalService = mockk<OrphanRemovalService> {
            coEvery { deleteRunsOrphanedEntities(any()) } just runs
        }

        val subConfigManager = mockk<ConfigManager>()
        val configManager = mockk<ConfigManager> {
            every { subConfig(Path("orphanHandlers")) } returns subConfigManager
        }

        val task = DeleteOrphanedEntitiesTask.create(configManager, orphanRemovalService)
        task.execute()

        coVerify {
            orphanRemovalService.deleteRunsOrphanedEntities(subConfigManager)
        }
    }
})
