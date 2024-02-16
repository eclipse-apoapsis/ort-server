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

package org.eclipse.apoapsis.ortserver.dao.tables.runs.shared

import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.OrtIssue

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent an ort issue.
 */
object OrtIssuesTable : LongIdTable("ort_issues") {
    val timestamp = timestamp("timestamp")
    val issueSource = text("source")
    val message = text("message")
    val severity = text("severity")
}

class OrtIssueDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<OrtIssueDao>(OrtIssuesTable) {
        fun createByIssue(issue: OrtIssue): OrtIssueDao =
            new {
                timestamp = issue.timestamp
                source = issue.source
                message = issue.message
                severity = issue.severity
            }
    }

    var timestamp by OrtIssuesTable.timestamp.transform({ it.toDatabasePrecision() }, { it })
    var source by OrtIssuesTable.issueSource
    var message by OrtIssuesTable.message
    var severity by OrtIssuesTable.severity

    fun mapToModel() = OrtIssue(timestamp = timestamp, source = source, message = message, severity = severity)
}
