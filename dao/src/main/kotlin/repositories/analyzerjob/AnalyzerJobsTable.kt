/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.JobSummary

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent an analyzer job.
 */
object AnalyzerJobsTable : LongIdTable("analyzer_jobs") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)

    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
    val configuration = jsonb<AnalyzerJobConfiguration>("configuration")
    val status = enumerationByName<JobStatus>("status", 128)
}

class AnalyzerJobDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AnalyzerJobDao>(AnalyzerJobsTable)

    var ortRunId by AnalyzerJobsTable.ortRunId.transformToEntityId()
    var ortRun by OrtRunDao referencedOn AnalyzerJobsTable.ortRunId

    var createdAt by AnalyzerJobsTable.createdAt.transformToDatabasePrecision()
    var startedAt by AnalyzerJobsTable.startedAt.transformToDatabasePrecision()
    var finishedAt by AnalyzerJobsTable.finishedAt.transformToDatabasePrecision()
    var configuration by AnalyzerJobsTable.configuration
    var status by AnalyzerJobsTable.status

    val analyzerRun by AnalyzerRunDao optionalBackReferencedOn AnalyzerRunsTable.analyzerJobId

    fun mapToModel() = AnalyzerJob(
        id = id.value,
        ortRunId = ortRun.id.value,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration,
        status = status
    )

    fun mapToJobSummaryModel() = JobSummary(
        id = id.value,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status
    )
}
