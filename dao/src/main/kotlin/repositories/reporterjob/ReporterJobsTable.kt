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

package org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterrun.ReporterRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterrun.ReporterRunsTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.JobSummary
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent a reporter job.
 */
object ReporterJobsTable : LongIdTable("reporter_jobs") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)

    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
    val configuration = jsonb<ReporterJobConfiguration>("configuration")
    val status = enumerationByName<JobStatus>("status", 128)
}

class ReporterJobDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ReporterJobDao>(ReporterJobsTable)

    var ortRunId by ReporterJobsTable.ortRunId.transformToEntityId()
    var ortRun by OrtRunDao referencedOn ReporterJobsTable.ortRunId
    val reporterRun by ReporterRunDao optionalBackReferencedOn ReporterRunsTable.reporterJobId

    var createdAt by ReporterJobsTable.createdAt.transformToDatabasePrecision()
    var startedAt by ReporterJobsTable.startedAt.transformToDatabasePrecision()
    var finishedAt by ReporterJobsTable.finishedAt.transformToDatabasePrecision()
    var configuration by ReporterJobsTable.configuration
    var status by ReporterJobsTable.status

    fun mapToModel() = ReporterJob(
        id = id.value,
        ortRunId = ortRun.id.value,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration,
        status = status,
        filenames = reporterRun?.reports?.map { it.filename }?.sorted().orEmpty()
    )

    fun mapToJobSummaryModel() = JobSummary(
        id = id.value,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status
    )
}
