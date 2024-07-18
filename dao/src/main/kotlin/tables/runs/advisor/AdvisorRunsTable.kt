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

package org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor

import org.eclipse.apoapsis.ortserver.dao.tables.AdvisorJobDao
import org.eclipse.apoapsis.ortserver.dao.tables.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.EnvironmentsTable
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
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
    companion object : LongEntityClass<AdvisorRunDao>(AdvisorRunsTable)

    var advisorJob by AdvisorJobDao referencedOn AdvisorRunsTable.advisorJobId
    var environment by EnvironmentDao referencedOn AdvisorRunsTable.environmentId

    var startTime by AdvisorRunsTable.startTime.transform({ it.toDatabasePrecision() }, { it })
    var endTime by AdvisorRunsTable.endTime.transform({ it.toDatabasePrecision() }, { it })

    val advisorConfiguration by AdvisorConfigurationDao backReferencedOn AdvisorConfigurationsTable.advisorRunId
    val results by AdvisorRunIdentifierDao referrersOn AdvisorRunsIdentifiersTable.advisorRunId

    fun mapToModel() = AdvisorRun(
        id = id.value,
        advisorJobId = advisorJob.id.value,
        startTime = startTime,
        endTime = endTime,
        environment = environment.mapToModel(),
        config = advisorConfiguration.mapToModel(),
        results = results.associate(AdvisorRunIdentifierDao::mapToModel)
    )
}
