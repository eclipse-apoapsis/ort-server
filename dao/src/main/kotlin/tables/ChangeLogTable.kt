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

package org.eclipse.apoapsis.ortserver.dao.tables

import org.eclipse.apoapsis.ortserver.dao.repositories.userDisplayName.UserDisplayNameDao
import org.eclipse.apoapsis.ortserver.model.ChangeEvent
import org.eclipse.apoapsis.ortserver.model.ChangeEventAction
import org.eclipse.apoapsis.ortserver.model.ChangeEventEntityType
import org.eclipse.apoapsis.ortserver.model.UserDisplayName

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/*
 * A table to store change log entries representing user-performed change events.
 */
object ChangeLogTable : Table("change_log") {
    val entityType = text("entity_type")
    val entityId = text("entity_id")
    val userId = text("user_id")
    val occurredAt = timestamp("occurred_at")
    val action = text("action")

    fun insert(
        entityTypeInput: ChangeEventEntityType,
        entityIdInput: String,
        userIdInput: String,
        actionInput: ChangeEventAction
    ) {
        insert {
            it[entityType] = entityTypeInput.name
            it[entityId] = entityIdInput
            it[userId] = userIdInput
            it[action] = actionInput.name
        }
    }

    fun getAllByEntityTypeAndId(
        entityTypeSearch: ChangeEventEntityType,
        entityIdSearch: String
    ): List<ChangeEvent> {
        return select(columns)
            .where { (entityType eq entityTypeSearch.name) and (entityId eq entityIdSearch) }
            .orderBy(occurredAt, SortOrder.ASC)
            .map { row ->
                ChangeEvent(
                    user = UserDisplayNameDao.findById(row[userId])?.mapToModel()
                        ?: UserDisplayName(row[userId], "Unknown"),
                    occurredAt = row[occurredAt],
                    action = ChangeEventAction.valueOf(row[action])
                )
            }
    }
}
