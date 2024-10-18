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
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.model.runs.Issue

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * An intermediate table to store references from [ScanSummariesTable] and [IssuesTable] together with some
 * additional properties.
 */
object ScanSummariesIssuesTable : LongIdTable("scan_summaries_issues") {
    val scanSummaryId = reference("scan_summary_id", ScanSummariesTable)
    val issueId = reference("issue_id", IssuesTable)
    val timestamp = timestamp("timestamp")
}

class ScanSummariesIssuesDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScanSummariesIssuesDao>(ScanSummariesIssuesTable) {
        /**
         * Create an entity based on the given [issue] and assign it to the given [scanSummaryId].
         */
        fun createByIssue(scanSummaryId: Long, issue: Issue): ScanSummariesIssuesDao = new {
            this.scanSummary = ScanSummaryDao[scanSummaryId]
            this.issue = IssueDao.createByIssue(issue)
            this.timestamp = issue.timestamp
        }
    }

    var scanSummary by ScanSummaryDao referencedOn ScanSummariesIssuesTable.scanSummaryId
    var issue by IssueDao referencedOn ScanSummariesIssuesTable.issueId
    var timestamp by ScanSummariesIssuesTable.timestamp

    /**
     * Map this DAO to an [Issue].
     */
    fun mapToModel(): Issue =
        issue.mapToModel(at = timestamp, identifier = null, worker = null)
}
