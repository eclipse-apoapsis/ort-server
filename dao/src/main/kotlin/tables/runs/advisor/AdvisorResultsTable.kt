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

import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent a result of an advisor for a single identifier.
 */
object AdvisorResultsTable : LongIdTable("advisor_results") {
    val advisorRunIdentifierId = reference("advisor_run_identifier_id", AdvisorRunsIdentifiersTable)

    val advisorName = text("advisor_name")
    val capabilities = text("capabilities")
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
}

class AdvisorResultDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AdvisorResultDao>(AdvisorResultsTable)

    var advisorRunIdentifier by AdvisorRunIdentifierDao referencedOn AdvisorResultsTable.advisorRunIdentifierId

    var advisorName by AdvisorResultsTable.advisorName
    @Suppress("DEPRECATION") // See https://youtrack.jetbrains.com/issue/EXPOSED-483.
    var capabilities by AdvisorResultsTable.capabilities
        .transform({ it.joinToString(",") }, { it.split(",") })
    var startTime by AdvisorResultsTable.startTime.transformToDatabasePrecision()
    var endTime by AdvisorResultsTable.endTime.transformToDatabasePrecision()

    var vulnerabilities by VulnerabilityDao via AdvisorResultsVulnerabilitiesTable
    var defects by DefectDao via AdvisorResultsDefectsTable
    var issues by IssueDao via AdvisorResultsIssuesTable

    fun mapToModel() = AdvisorResult(
        advisorName = advisorName,
        capabilities = capabilities.filter(String::isNotEmpty),
        startTime = startTime,
        endTime = endTime,
        issues = issues.map(IssueDao::mapToModel),
        defects = defects.map(DefectDao::mapToModel),
        vulnerabilities = vulnerabilities.map(VulnerabilityDao::mapToModel)
    )
}
