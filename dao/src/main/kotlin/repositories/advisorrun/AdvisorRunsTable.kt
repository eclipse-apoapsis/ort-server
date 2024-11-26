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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun

import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunIssueDao
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent a summary of an advisor run.
 */
object AdvisorRunsTable : LongIdTable("advisor_runs") {
    val advisorJobId = reference("advisor_job_id", AdvisorJobsTable)
    val environmentId = reference("environment_id", EnvironmentsTable)

    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
}

class AdvisorRunDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AdvisorRunDao>(AdvisorRunsTable) {
        /**
         * Constant for the _worker_ property value set for issues to mark them as created by the Advisor.
         */
        const val ISSUE_WORKER_TYPE = "advisor"
    }

    var advisorJob by AdvisorJobDao referencedOn AdvisorRunsTable.advisorJobId
    var environment by EnvironmentDao referencedOn AdvisorRunsTable.environmentId

    var startTime by AdvisorRunsTable.startTime.transformToDatabasePrecision()
    var endTime by AdvisorRunsTable.endTime.transformToDatabasePrecision()

    val advisorConfiguration by AdvisorConfigurationDao backReferencedOn AdvisorConfigurationsTable.advisorRunId
    val results by AdvisorRunIdentifierDao referrersOn AdvisorRunsIdentifiersTable.advisorRunId

    fun mapToModel(): AdvisorRun {
        val runIssues = advisorJob.ortRun.issues.filter { it.worker == ISSUE_WORKER_TYPE }
            .map(OrtRunIssueDao::mapToModel)

        return AdvisorRun(
            id = id.value,
            advisorJobId = advisorJob.id.value,
            startTime = startTime,
            endTime = endTime,
            environment = environment.mapToModel(),
            config = advisorConfiguration.mapToModel(),
            results = results.associate { it.mapToModel(runIssues) }
        )
    }
}
