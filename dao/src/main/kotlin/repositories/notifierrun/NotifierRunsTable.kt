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

package org.eclipse.apoapsis.ortserver.dao.repositories.notifierrun

import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.NotifierJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.NotifierJobsTable
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.runs.notifier.NotifierRun

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object NotifierRunsTable : LongIdTable("notifier_runs") {
    val notifierJobId = reference("notifier_job_id", NotifierJobsTable)
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
}

class NotifierRunDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<NotifierRunDao>(NotifierRunsTable)

    var notifierJobId by NotifierRunsTable.notifierJobId.transformToEntityId()
    var notifierJob by NotifierJobDao referencedOn NotifierRunsTable.notifierJobId
    var startTime by NotifierRunsTable.startTime.transformToDatabasePrecision()
    var endTime by NotifierRunsTable.endTime.transformToDatabasePrecision()

    fun mapToModel() = NotifierRun(
        id = id.value,
        notifierJobId = notifierJob.id.value,
        startTime = startTime,
        endTime = endTime
    )
}
