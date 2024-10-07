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

import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.Issue

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * An intermediate table to store references from [OrtRunsTable] and [IssuesTable] together with some additional
 * properties.
 */
object OrtRunsIssuesTable : LongIdTable("ort_runs_issues") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)
    val issueId = reference("issue_id", IssuesTable)
    val identifierId = reference("identifier_id", IdentifiersTable).nullable()
    val worker = text("worker").nullable()
    val timestamp = timestamp("timestamp")
}

class OrtRunIssueDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<OrtRunIssueDao>(OrtRunsIssuesTable) {
        /**
         * Create a new entity based on the given [issue] and associate it with the given [ortRunId].
         */
        fun createByIssue(ortRunId: Long, issue: Issue): OrtRunIssueDao =
            new {
                this.ortRunId = OrtRunDao[ortRunId].id
                this.issue = IssueDao.createByIssue(issue)
                this.identifierId = issue.identifier?.let { IdentifierDao.getOrPut(it) }?.id
                this.worker = issue.worker
                this.timestamp = issue.timestamp
            }
    }

    var ortRunId by OrtRunsIssuesTable.ortRunId
    var issue by IssueDao referencedOn OrtRunsIssuesTable.issueId
    var identifierId by OrtRunsIssuesTable.identifierId
    var worker by OrtRunsIssuesTable.worker
    var timestamp by OrtRunsIssuesTable.timestamp.transformToDatabasePrecision()

    fun mapToModel() = issue.mapToModel(timestamp, identifierId?.let { IdentifierDao[it].mapToModel() }, worker)
}
