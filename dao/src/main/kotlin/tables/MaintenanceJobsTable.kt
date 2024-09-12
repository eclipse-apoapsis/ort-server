/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import kotlinx.serialization.json.JsonObject

import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.MaintenanceJobData
import org.eclipse.apoapsis.ortserver.model.MaintenanceJobStatus

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table that stores the state of maintenance jobs.
 */
object MaintenanceJobsTable : LongIdTable("maintenance_jobs") {
    val name = text("name")
    val status = enumerationByName<MaintenanceJobStatus>("status", 128)
    val startedAt = timestamp("started_at").nullable()
    val updatedAt = timestamp("updated_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
    val data = jsonb<JsonObject>("data").nullable()
}

class MaintenanceJobDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<MaintenanceJobDao>(MaintenanceJobsTable)

    var name by MaintenanceJobsTable.name
    var status by MaintenanceJobsTable.status
    var startedAt by MaintenanceJobsTable.startedAt.transform({ it?.toDatabasePrecision() }, { it })
    var updatedAt by MaintenanceJobsTable.updatedAt.transform({ it?.toDatabasePrecision() }, { it })
    var finishedAt by MaintenanceJobsTable.finishedAt.transform({ it?.toDatabasePrecision() }, { it })
    var data by MaintenanceJobsTable.data

    fun mapToModel() = MaintenanceJobData(
        id = id.value,
        name = name,
        status = status,
        startedAt = startedAt,
        updatedAt = updatedAt,
        finishedAt = finishedAt,
        data = data
    )
}
