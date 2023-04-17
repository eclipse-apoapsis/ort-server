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

import org.ossreviewtoolkit.server.dao.tables.runs.shared.OrtIssueDao
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision

/**
 * A table to represent a scan result.
 */
object ScanSummariesTable : LongIdTable("scan_summaries") {
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
    val packageVerificationCode = text("package_verification_code")
}

class ScanSummaryDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScanSummaryDao>(ScanSummariesTable)

    var startTime by ScanSummariesTable.startTime.transform({ it.toDatabasePrecision() }, { it })
    var endTime by ScanSummariesTable.endTime.transform({ it.toDatabasePrecision() }, { it })
    var packageVerificationCode by ScanSummariesTable.packageVerificationCode
    val licenseFindings by LicenseFindingDao referrersOn LicenseFindingsTable.scanSummaryId
    val copyrightFindings by CopyrightFindingDao referrersOn CopyrightFindingsTable.scanSummaryId
    var issues by OrtIssueDao via ScanSummariesIssuesTable
}
