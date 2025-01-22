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
    val hash = text("hash")
}

class ScanSummaryDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScanSummaryDao>(ScanSummariesTable)

    var startTime by ScanSummariesTable.startTime.transformToDatabasePrecision()
    var endTime by ScanSummariesTable.endTime.transformToDatabasePrecision()
    var hash by ScanSummariesTable.hash
    val licenseFindings by LicenseFindingDao referrersOn LicenseFindingsTable.scanSummaryId
    val copyrightFindings by CopyrightFindingDao referrersOn CopyrightFindingsTable.scanSummaryId
    val snippetFindings by SnippetFindingDao referrersOn SnippetFindingsTable.scanSummaryId
    val issues by ScanSummariesIssuesDao referrersOn ScanSummariesIssuesTable.scanSummaryId

    /**
     * Map this DAO to a [ScanSummary] model object. Based on the [withFindings] flag, either include all findings
     * or return a summary without findings.
     */
    fun mapToModel(withFindings: Boolean = true) = ScanSummary(
        startTime = startTime,
        endTime = endTime,
        hash = hash,
        licenseFindings = if (withFindings) {
            licenseFindings.mapTo(mutableSetOf(), LicenseFindingDao::mapToModel)
        } else {
            emptySet()
        },
        copyrightFindings = if (withFindings) {
            copyrightFindings.mapTo(mutableSetOf(), CopyrightFindingDao::mapToModel)
        } else {
            emptySet()
        },
        snippetFindings = if (withFindings) {
            snippetFindings.mapTo(mutableSetOf(), SnippetFindingDao::mapToModel)
        } else {
            emptySet()
        },
        issues = issues.map(ScanSummariesIssuesDao::mapToModel)
    )
}
