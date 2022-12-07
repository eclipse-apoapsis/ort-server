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

package org.ossreviewtoolkit.server.dao.tables.runs.advisor

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

import org.ossreviewtoolkit.server.dao.tables.runs.shared.OrtIssueDao
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision

/**
 * A table to represent a result of an advisor for a single identifier.
 */
object AdvisorResultsTable : LongIdTable("advisor_results") {
    val advisorRunIdentifierId = reference(
        "advisor_run_identifier_id",
        AdvisorRunsIdentifiersTable.id,
        ReferenceOption.CASCADE
    )
    val advisorName = text("advisor_name")
    val capabilities = text("capabilities")
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
}

class AdvisorResultDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AdvisorResultDao>(AdvisorResultsTable)

    var advisorRunIdentifier by AdvisorRunIdentifierDao referencedOn AdvisorResultsTable.advisorRunIdentifierId
    var advisorName by AdvisorResultsTable.advisorName
    var capabilities by AdvisorResultsTable.capabilities
        .transform({ it.joinToString(",") }, { it.split(",") })
    var startTime by AdvisorResultsTable.startTime.transform({ it.toDatabasePrecision() }, { it })
    var endTime by AdvisorResultsTable.endTime.transform({ it.toDatabasePrecision() }, { it })
    var vulnerabilities by VulnerabilityDao via AdvisorResultsVulnerabilitiesTable
    var defects by DefectDao via AdvisorResultsDefectsTable
    var issues by OrtIssueDao via AdvisorResultsIssuesTable
}
