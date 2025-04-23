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

package org.eclipse.apoapsis.ortserver.dao.tables.shared

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.utils.DigestFunction
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.stringLiteral

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
                    (DigestFunction(IssuesTable.message) eq DigestFunction(stringLiteral(issue.message))) and
                    (IssuesTable.severity eq issue.severity) and
                    (IssuesTable.affectedPath eq issue.affectedPath)
        }.firstOrNull()

        /**
         * Return an [IssueDao] to represent the given [issue]. If the properties of the [issue] can be matched,
         * an existing [IssueDao] is returned. Otherwise, a new one is created.
         */
        fun createByIssue(issue: Issue): IssueDao =
            findByIssue(issue) ?: new {
                source = issue.source
                message = issue.message
                severity = issue.severity
                affectedPath = issue.affectedPath
            }

        /**
         * Execute the given [query] and return the result as a list of [Issue] objects. This function allows loading
         * issues from different sources, e.g., the issues from an ORT run or from a scan result. The query must
         * return the properties of an issue in the following order:
         * 1. timestamp
         * 2. source
         * 3. message
         * 4. severity
         * 5. affectedPath (nullable)
         * 6. identifier type (nullable)
         * 7. identifier name (nullable)
         * 8. identifier namespace (nullable)
         * 9. identifier version (nullable)
         * 10. worker (nullable)
         */
        fun createFromQuery(query: Query): List<Issue> =
            query.map(ResultRow::toIssue)
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

/**
 * Create an [Issue] from the fields of this [ResultRow]. Using this function, issues can be created from query
 * results that may include other data as well. The fields of the result define the issue; they must appear in the
 * order as described in [IssueDao.createFromQuery].
 */
@Suppress("ComplexCondition")
fun ResultRow.toIssue(): Issue {
    // The exposed library seems not to fully support fields on alias queries when unions are used.
    // Use the field indexes to extract the values from the ResultRow instead of the field names.
    // Disadvantage: More fragility, losing type safety at compile time.
    val columns = fieldIndex.keys.toList()

    val type = this[columns[5]] as String?
    val name = this[columns[6]] as String?
    val namespace = this[columns[7]] as String?
    val version = this[columns[8]] as String?
    val identifier = if (type == null || name == null || namespace == null || version == null) {
        null
    } else {
        Identifier(type, namespace, name, version)
    }

    return Issue(
        timestamp = this[columns[0]] as Instant,
        source = this[columns[1]] as String,
        message = this[columns[2]] as String,
        severity = this[columns[3]] as Severity,
        affectedPath = this[columns[4]] as String?,
        worker = this[columns[9]] as String?,
        identifier = identifier
    )
}
