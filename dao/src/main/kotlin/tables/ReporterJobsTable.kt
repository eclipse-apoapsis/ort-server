/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

import org.ossreviewtoolkit.server.dao.utils.jsonb
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration

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

    var ortRun by OrtRunDao referencedOn ReporterJobsTable.ortRunId

    var createdAt by ReporterJobsTable.createdAt.transform({ it.toDatabasePrecision() }, { it })
    var startedAt by ReporterJobsTable.startedAt.transform({ it?.toDatabasePrecision() }, { it })
    var finishedAt by ReporterJobsTable.finishedAt.transform({ it?.toDatabasePrecision() }, { it })
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
    )
}
