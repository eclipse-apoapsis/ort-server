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

package org.eclipse.apoapsis.ortserver.dao.tables

import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanSummary

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent a scan result.
 */
object ScanSummariesTable : LongIdTable("scan_summaries") {
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
}

class ScanSummaryDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScanSummaryDao>(ScanSummariesTable)

    var startTime by ScanSummariesTable.startTime.transformToDatabasePrecision()
    var endTime by ScanSummariesTable.endTime.transformToDatabasePrecision()
    val licenseFindings by LicenseFindingDao referrersOn LicenseFindingsTable.scanSummaryId
    val copyrightFindings by CopyrightFindingDao referrersOn CopyrightFindingsTable.scanSummaryId
    val snippetFindings by SnippetFindingDao referrersOn SnippetFindingsTable.scanSummaryId
    var issues by IssueDao via ScanSummariesIssuesTable

    fun mapToModel() = ScanSummary(
        startTime = startTime,
        endTime = endTime,
        licenseFindings = licenseFindings.mapTo(mutableSetOf(), LicenseFindingDao::mapToModel),
        copyrightFindings = copyrightFindings.mapTo(mutableSetOf(), CopyrightFindingDao::mapToModel),
        snippetFindings = snippetFindings.mapTo(mutableSetOf(), SnippetFindingDao::mapToModel),
        issues = issues.map(IssueDao::mapToModel)
    )
}
