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
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * A table to represent names to identify a user. This is **not** used for any authentication or authorization, it is
 * only used to store users' names in a central table, where it is easier to keep them up to date or remove them after a
 * time of inactivity.
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
}

class UserDao(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, UserDao>(UsersTable) {
        /**
         * Insert a new entry, if it does not already exist, else update the entry.
         */
        fun insertOrUpdate(userDisplayName: User?): UserDao? {
            // Tests may pass `null` as `userDisplayName`
            if (userDisplayName == null) {
                return null
            }

            UsersTable.upsert(UsersTable.id) {
                it[this.id] = userDisplayName.userId
                it[this.username] = userDisplayName.username
                it[this.fullName] = userDisplayName.fullName
                it[this.createdAt] = Clock.System.now()
            }

            return UserDao[userDisplayName.userId]
        }
    }

    var username by UsersTable.username
    var fullName by UsersTable.fullName
    var createdAt by UsersTable.createdAt.transformToDatabasePrecision()

    fun mapToModel() = User(userId = id.value, username = username, fullName = fullName)
}
