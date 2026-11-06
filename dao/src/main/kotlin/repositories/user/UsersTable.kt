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

package org.eclipse.apoapsis.ortserver.dao.repositories.user

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.User

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.isDistinctFrom
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * A table to store the users of ORT Server, so that runs and other data can show who created them. This is **not**
 * used for any authentication or authorization. Rows are never deleted, even if the user is deleted in Keycloak, so
 * that the information who created a run is kept.
 */
object UsersTable : IdTable<String>("users") {
    /**
     * Unique user identifier, for example, derived from JWT `sub` claim. This is stable over time.
     */
    override val id: Column<EntityID<String>> = varchar("user_id", 40).entityId()

    /**
     * Preferred username. This may change over time.
     */
    val username = text("username")

    /**
     * UX-friendly display name. This may change over time.
     */
    val fullName = text("full_name").nullable()

    val createdAt = timestamp("created_at")

    /** The time when the user was last written. */
    val updatedAt = timestamp("updated_at")

    /** The time when the user was found to be deleted in Keycloak, or `null` if the user exists. */
    val deletedAt = timestamp("deleted_at").nullable()

    /** The time when the user was last synchronized with Keycloak, or `null` if this never happened. */
    val lastSyncedAt = timestamp("last_synced_at").nullable()
}

class UserDao(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, UserDao>(UsersTable) {
        /**
         * Insert a new entry, if it does not already exist, else update the entry. The creation time of an existing
         * entry is kept. As the user exists, a deletion time stored for them is cleared. The update time is set only if
         * the names have changed or the deletion time is cleared.
         */
        fun insertOrUpdate(userDisplayName: User?): UserDao? {
            // Tests may pass `null` as `userDisplayName`
            if (userDisplayName == null) {
                return null
            }

            val now = Clock.System.now()

            UsersTable.upsert(UsersTable.id, onUpdate = { statement ->
                val hasChanged = (UsersTable.username isDistinctFrom insertValue(UsersTable.username)) or
                    (UsersTable.fullName isDistinctFrom insertValue(UsersTable.fullName)) or
                    UsersTable.deletedAt.isNotNull()

                statement[UsersTable.username] = insertValue(UsersTable.username)
                statement[UsersTable.fullName] = insertValue(UsersTable.fullName)
                statement[UsersTable.deletedAt] = null
                statement[UsersTable.updatedAt] = case()
                    .When(hasChanged, insertValue(UsersTable.updatedAt))
                    .Else(UsersTable.updatedAt)
            }) {
                it[this.id] = userDisplayName.userId
                it[this.username] = userDisplayName.username
                it[this.fullName] = userDisplayName.fullName
                it[this.createdAt] = now
                it[this.updatedAt] = now
                it[this.deletedAt] = null
            }

            return UserDao[userDisplayName.userId]
        }
    }

    var username by UsersTable.username
    var fullName by UsersTable.fullName
    var createdAt by UsersTable.createdAt.transformToDatabasePrecision()
    var updatedAt by UsersTable.updatedAt.transformToDatabasePrecision()
    var deletedAt by UsersTable.deletedAt.transformToDatabasePrecision()
    var lastSyncedAt by UsersTable.lastSyncedAt.transformToDatabasePrecision()

    fun mapToModel() = User(
        userId = id.value,
        username = username,
        fullName = fullName,
        deletedAt = deletedAt,
        lastSyncedAt = lastSyncedAt
    )
}
