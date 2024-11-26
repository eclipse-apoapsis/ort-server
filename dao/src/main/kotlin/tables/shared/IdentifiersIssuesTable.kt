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

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * An intermediate table to store references from [IdentifiersTable] and [IssuesTable].
 */
object IdentifiersIssuesTable : LongIdTable("identifiers_issues") {
    val identifierId = reference("identifier_id", IdentifiersTable)
    val issueId = reference("issue_id", IssuesTable)
}

class IdentifierIssueDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<IdentifierIssueDao>(IdentifiersIssuesTable) {
        fun findByIdentifierAndIssue(identifier: IdentifierDao, issue: IssueDao): IdentifierIssueDao? =
            find {
                IdentifiersIssuesTable.identifierId eq identifier.id and
                        (IdentifiersIssuesTable.issueId eq issue.id)
            }.firstOrNull()

        fun getOrPut(identifier: IdentifierDao, issue: IssueDao): IdentifierIssueDao =
            findByIdentifierAndIssue(identifier, issue) ?: new {
                this.identifier = identifier
                this.issueDao = issue
            }
    }

    var identifier by IdentifierDao referencedOn IdentifiersIssuesTable.identifierId
    var issueDao by IssueDao referencedOn IdentifiersIssuesTable.issueId
}
