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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * A table to represent an issue resolution, used within a [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object IssueResolutionsTable : LongIdTable("issue_resolutions") {
    val message = text("message")
    val reason = enumerationByName<IssueResolutionReason>("reason", 255)
    val comment = text("comment")
}

class IssueResolutionDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<IssueResolutionDao>(IssueResolutionsTable) {
        fun findByIssueResolution(issueResolution: IssueResolution): IssueResolutionDao? =
            find {
                IssueResolutionsTable.message eq issueResolution.message and
                        (IssueResolutionsTable.reason eq issueResolution.reason) and
                        (IssueResolutionsTable.comment eq issueResolution.comment)
            }.firstOrNull()

        fun getOrPut(issueResolution: IssueResolution): IssueResolutionDao =
            findByIssueResolution(issueResolution) ?: new {
                message = issueResolution.message
                reason = issueResolution.reason
                comment = issueResolution.comment
            }
    }

    var message by IssueResolutionsTable.message
    var reason by IssueResolutionsTable.reason
    var comment by IssueResolutionsTable.comment

    fun mapToModel() = IssueResolution(message, reason, comment)
}
