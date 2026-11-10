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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

import org.eclipse.apoapsis.ortserver.clients.keycloak.DefaultKeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientConfiguration
import org.eclipse.apoapsis.ortserver.components.authorization.service.KeycloakUserSyncService
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.tasks.Task
import org.eclipse.apoapsis.ortserver.utils.logging.JobStatusLogging

import org.jetbrains.exposed.v1.jdbc.Database

/**
 * A task that syncs the stored users and the role assignments with the users in Keycloak, using
 * [KeycloakUserSyncService].
 *
 * The task reads the connection to Keycloak from the `keycloak` section, in the same way as the core module. Its own
 * settings are read from the `userSync` section:
 * - `pageSize`: The number of users to request from Keycloak at once. This can be overridden by the
 *   `USER_SYNC_PAGE_SIZE` environment variable.
 * - `maxDeletedUsers`: The number of users that a single sync may always remove. This can be overridden by the
 *   `USER_SYNC_MAX_DELETED_USERS` environment variable.
 * - `maxDeletedShare`: The share of users that a single sync may remove, if this is more than `maxDeletedUsers`. This
 *   can be overridden by the `USER_SYNC_MAX_DELETED_SHARE` environment variable.
 */
class SyncKeycloakUsersTask(
    /** The service that syncs the users. */
    private val syncService: KeycloakUserSyncService
) : Task {
    companion object {
        const val TASK_NAME = "syncKeycloakUsersTask"

        /** The key for the number of users stored in the job status log. */
        const val STORED_USERS_KEY = "storedUsers"

        /** The key for the number of newly stored users in the job status log. */
        const val NEW_USERS_KEY = "newUsers"

        /** The key for the number of users marked deleted in the job status log. */
        const val DELETED_USERS_KEY = "deletedUsers"

        /** The key for the number of removed role assignments in the job status log. */
        const val REMOVED_ROLE_ASSIGNMENTS_KEY = "removedRoleAssignments"

        /** Create a new instance of [SyncKeycloakUsersTask] with the configuration from [configManager] for [db]. */
        fun create(configManager: ConfigManager, db: Database): SyncKeycloakUsersTask {
            val keycloakClient = DefaultKeycloakClient.create(
                KeycloakClientConfiguration.create(configManager),
                Json { ignoreUnknownKeys = true }
            )

            val config = UserSyncConfig.create(configManager)

            return SyncKeycloakUsersTask(
                KeycloakUserSyncService(
                    keycloakClient,
                    db,
                    config.maxDeletedUsers,
                    config.maxDeletedShare,
                    config.pageSize
                )
            )
        }
    }

    override suspend fun execute() {
        JobStatusLogging.runWithStatusLogging(TASK_NAME) { jsonBuilder ->
            val result = syncService.sync()

            jsonBuilder.put(STORED_USERS_KEY, JsonPrimitive(result.storedUsers))
            jsonBuilder.put(NEW_USERS_KEY, JsonPrimitive(result.newUsers))
            jsonBuilder.put(DELETED_USERS_KEY, JsonPrimitive(result.deletedUsers))
            jsonBuilder.put(REMOVED_ROLE_ASSIGNMENTS_KEY, JsonPrimitive(result.removedRoleAssignments))
        }
    }
}

/** The settings of a single sync, see [KeycloakUserSyncService]. */
internal data class UserSyncConfig(
    /** The number of users that a single sync may always remove. */
    val maxDeletedUsers: Int,

    /** The share of users that a single sync may remove, if this is more than [maxDeletedUsers]. */
    val maxDeletedShare: Double,

    /** The number of users to request from Keycloak at once. */
    val pageSize: Int
) {
    companion object {
        /** Read the settings from the `userSync` section of the given [configManager]. */
        fun create(configManager: ConfigManager) =
            UserSyncConfig(
                maxDeletedUsers = configManager.getInt("userSync.maxDeletedUsers"),
                maxDeletedShare = configManager.getDouble("userSync.maxDeletedShare"),
                pageSize = configManager.getInt("userSync.pageSize")
            )
    }
}
