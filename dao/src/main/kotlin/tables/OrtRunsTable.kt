/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

import org.ossreviewtoolkit.server.dao.utils.jsonb
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus

/**
 * A table to represent an ORT run.
 */
object OrtRunsTable : LongIdTable("ort_runs") {
    val index = long("index")
    val repositoryId = reference("repository_id", RepositoriesTable.id, ReferenceOption.CASCADE)
    val revision = text("revision")
    val createdAt = timestamp("created_at")

    // TODO: Create a proper database representation for configurations, JSON is only used because of the expected
    //       frequent changes during early development.
    val jobConfigurations = jsonb("job_configurations", JobConfigurations::class)
    val status = enumerationByName<OrtRunStatus>("status", 128)
}

class OrtRunDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<OrtRunDao>(OrtRunsTable)

    var index by OrtRunsTable.index
    var repository by RepositoryDao referencedOn OrtRunsTable.repositoryId
    var revision by OrtRunsTable.revision
    var createdAt by OrtRunsTable.createdAt.transform({ it.toDatabasePrecision() }, { it })
    var jobConfigurations by OrtRunsTable.jobConfigurations
    var status by OrtRunsTable.status

    val advisorJob by AdvisorJobDao optionalBackReferencedOn AdvisorJobsTable.ortRunId
    val analyzerJob by AnalyzerJobDao optionalBackReferencedOn AnalyzerJobsTable.ortRunId
    val evaluatorJob by EvaluatorJobDao optionalBackReferencedOn EvaluatorJobsTable.ortRunId
    val scannerJob by ScannerJobDao optionalBackReferencedOn ScannerJobsTable.ortRunId
    val reporterJob by ReporterJobDao optionalBackReferencedOn ReporterJobsTable.ortRunId

    fun mapToModel() = OrtRun(
        id = id.value,
        index = index,
        repositoryId = repository.id.value,
        revision = revision,
        createdAt = createdAt,
        jobs = jobConfigurations,
        status = status
    )
}
