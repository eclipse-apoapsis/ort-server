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

package org.ossreviewtoolkit.server.dao.tables.runs.shared

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * An intermediate table to store references from [IdentifiersTable] and [OrtIssuesTable].
 */
object IdentifiersOrtIssuesTable : LongIdTable("identifiers_ort_issues") {
    val identifierId = reference("identifier_id", IdentifiersTable)
    val ortIssueId = reference("ort_issue_id", OrtIssuesTable)
}

class IdentifierOrtIssueDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<IdentifierOrtIssueDao>(IdentifiersOrtIssuesTable) {
        fun findByIdentifierAndIssue(identifier: IdentifierDao, issue: OrtIssueDao): IdentifierOrtIssueDao? =
            find {
                IdentifiersOrtIssuesTable.identifierId eq identifier.id and
                        (IdentifiersOrtIssuesTable.ortIssueId eq issue.id)
            }.singleOrNull()

        fun getOrPut(identifier: IdentifierDao, issue: OrtIssueDao): IdentifierOrtIssueDao =
            findByIdentifierAndIssue(identifier, issue) ?: new {
                this.identifier = identifier
                this.ortIssueDao = issue
            }
    }

    var identifier by IdentifierDao referencedOn IdentifiersOrtIssuesTable.identifierId
    var ortIssueDao by OrtIssueDao referencedOn IdentifiersOrtIssuesTable.ortIssueId
}
