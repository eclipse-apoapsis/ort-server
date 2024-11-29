/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.JobSummary
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent a scanner job.
 */
object ScannerJobsTable : LongIdTable("scanner_jobs") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)

    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
    val configuration = jsonb<ScannerJobConfiguration>("configuration")
    val status = enumerationByName<JobStatus>("status", 128)
}

class ScannerJobDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScannerJobDao>(ScannerJobsTable)

    var ortRunId by ScannerJobsTable.ortRunId.transformToEntityId()
    var ortRun by OrtRunDao referencedOn ScannerJobsTable.ortRunId

    var createdAt by ScannerJobsTable.createdAt.transformToDatabasePrecision()
    var startedAt by ScannerJobsTable.startedAt.transformToDatabasePrecision()
    var finishedAt by ScannerJobsTable.finishedAt.transformToDatabasePrecision()
    var configuration by ScannerJobsTable.configuration
    var status by ScannerJobsTable.status

    // TODO: Add `scannerRun` property when implementing scanner run model and worker

    fun mapToModel() = ScannerJob(
        id = id.value,
        ortRunId = ortRun.id.value,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration,
        status = status,
    )

    fun mapToJobSummaryModel() = JobSummary(
        id = id.value,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status
    )
}
