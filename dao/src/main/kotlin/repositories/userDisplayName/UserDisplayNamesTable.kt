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

package org.eclipse.apoapsis.ortserver.dao.repositories.userDisplayName

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.UserDisplayName

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert

/**
 * A table to represent names to identify a user. This is **not** used for any authentication or authorization purposes,
 * it is only used to store users' names in a central table, where it is easier to keep them up to date or remove them
 * after a time of inactivity.
 */
object UserDisplayNamesTable : IdTable<String>("user_display_names") {

    /**
     * Unique user identifier, for example derived from JWT `sub` claim. This is stable over time.
     */
    override val id: Column<EntityID<String>> = varchar("user_id", 40).entityId()

    /**
     * Preferred username. This may change over time.
     */
    val username = text("username")

    /**
     * UX friendly display name. This may change over time.
     */
    val fullName = text("full_name").nullable()

    val createdAt = timestamp("created_at")
}

class UserDisplayNameDAO(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, UserDisplayNameDAO>(UserDisplayNamesTable) {
        /**
         * Insert a new entry, if it does not already exist, else update the entry.
         */
        fun insertOrUpdate(userDisplayName: UserDisplayName?): UserDisplayNameDAO? {
            // Tests may pass `null` as `userDisplayName`
            if (userDisplayName == null) {
                return null
            }

            UserDisplayNamesTable.upsert(UserDisplayNamesTable.id) {
                it[this.id] = userDisplayName.userId
                it[this.username] = userDisplayName.username
                it[this.fullName] = userDisplayName.fullName
                it[this.createdAt] = Clock.System.now()
            }

            return UserDisplayNameDAO[userDisplayName.userId]
        }
    }

    var username by UserDisplayNamesTable.username
    var fullName by UserDisplayNamesTable.fullName
    var createdAt by UserDisplayNamesTable.createdAt.transformToDatabasePrecision()

    fun mapToModel() = UserDisplayName(userId = id.value, username = username, fullName = fullName)
}
