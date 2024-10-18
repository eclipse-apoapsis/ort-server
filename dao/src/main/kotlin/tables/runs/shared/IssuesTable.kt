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

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

/**
 * A table to represent an ort issue.
 */
object IssuesTable : SortableTable("issues") {
    val issueSource = text("source")
    val message = text("message")
    val severity = enumerationByName<Severity>("severity", 128)
    val affectedPath = text("affected_path").nullable()
}

class IssueDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<IssueDao>(IssuesTable) {
        fun findByIssue(issue: Issue): IssueDao? = find {
            IssuesTable.issueSource eq issue.source and
                    (IssuesTable.message eq issue.message) and
                    (IssuesTable.severity eq issue.severity) and
                    (IssuesTable.affectedPath eq issue.affectedPath)
        }.firstOrNull()

        /**
         * Return an [IssueDao] to represent the given [issue]. If the properties of the [issue] can be matched,
         * an existing [IssueDao] is returned, otherwise a new one is created.
         */
        fun createByIssue(issue: Issue): IssueDao =
            findByIssue(issue) ?: new {
                source = issue.source
                message = issue.message
                severity = issue.severity
                affectedPath = issue.affectedPath
            }
    }

    var source by IssuesTable.issueSource
    var message by IssuesTable.message
    var severity by IssuesTable.severity
    var affectedPath by IssuesTable.affectedPath

    /**
     * Return a model representation of this [IssueDao] with the given additional properties.
     */
    fun mapToModel(at: Instant, identifier: Identifier?, worker: String?) = Issue(
        timestamp = at,
        source = source,
        message = message,
        severity = severity,
        affectedPath = affectedPath,
        identifier = identifier,
        worker = worker
    )
}
