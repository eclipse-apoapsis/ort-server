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

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.mockk

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

import org.eclipse.apoapsis.ortserver.components.authorization.service.KeycloakUserSyncService
import org.eclipse.apoapsis.ortserver.components.authorization.service.UserSyncResult
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.utils.logging.JobStatusLogging
import org.eclipse.apoapsis.ortserver.utils.logging.StatusLoggingTestExtension

class SyncKeycloakUsersTaskTest : StringSpec({
    val logExtension = extension(StatusLoggingTestExtension())

    "The settings should have defaults" {
        ConfigFactory.invalidateCaches()
        val configManager = ConfigManager.create(ConfigFactory.load())

        UserSyncConfig.create(configManager) shouldBe
            UserSyncConfig(maxDeletedUsers = 5, maxDeletedShare = 0.1, pageSize = 5000)
    }

    "The settings should be overridden by environment variables" {
        val envMap = mapOf(
            "USER_SYNC_MAX_DELETED_USERS" to "50",
            "USER_SYNC_MAX_DELETED_SHARE" to "1",
            "USER_SYNC_PAGE_SIZE" to "100"
        )

        withEnvironment(envMap) {
            ConfigFactory.invalidateCaches()
            val configManager = ConfigManager.create(ConfigFactory.load())

            UserSyncConfig.create(configManager) shouldBe
                UserSyncConfig(maxDeletedUsers = 50, maxDeletedShare = 1.0, pageSize = 100)
        }

        ConfigFactory.invalidateCaches()
    }

    "A status log entry with the counts should be created for the execution of the task" {
        val syncService = mockk<KeycloakUserSyncService> {
            coEvery { sync() } returns UserSyncResult(
                storedUsers = 20,
                newUsers = 3,
                deletedUsers = 2,
                removedRoleAssignments = 7
            )
        }

        SyncKeycloakUsersTask(syncService).execute()

        val status = logExtension.statusLog()
        status[JobStatusLogging.STATUS_KEY]?.jsonPrimitive?.content shouldBe JobStatusLogging.STATUS_SUCCESS
        status[SyncKeycloakUsersTask.STORED_USERS_KEY]?.jsonPrimitive?.int shouldBe 20
        status[SyncKeycloakUsersTask.NEW_USERS_KEY]?.jsonPrimitive?.int shouldBe 3
        status[SyncKeycloakUsersTask.DELETED_USERS_KEY]?.jsonPrimitive?.int shouldBe 2
        status[SyncKeycloakUsersTask.REMOVED_ROLE_ASSIGNMENTS_KEY]?.jsonPrimitive?.int shouldBe 7
    }
})
