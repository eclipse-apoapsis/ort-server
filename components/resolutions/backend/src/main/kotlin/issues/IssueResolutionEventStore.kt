/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.resolutions.issues

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.model.RepositoryId

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** A store for [IssueResolutionEvent]s. */
class IssueResolutionEventStore(private val db: Database) {
    private fun loadEvents(repositoryId: RepositoryId, messageHash: String): List<IssueResolutionEvent> =
        db.blockingQuery {
            IssueResolutionEvents.selectAll()
                .where { IssueResolutionEvents.repositoryId eq repositoryId }
                .andWhere { IssueResolutionEvents.messageHash eq messageHash }
                .orderBy(IssueResolutionEvents.version)
                .map { it.toIssueResolutionEvent() }
        }

    internal fun appendEvent(event: IssueResolutionEvent): Unit = db.blockingQuery {
        IssueResolutionEvents.insert {
            it[repositoryId] = event.repositoryId
            it[messageHash] = event.messageHash
            it[version] = event.version
            it[payload] = event.payload
            it[createdBy] = event.createdBy
            it[createdAt] = event.createdAt
        }
    }

    internal fun getIssueResolution(repositoryId: RepositoryId, messageHash: String) =
        loadEvents(repositoryId, messageHash).takeIf { it.isNotEmpty() }?.let { events ->
            IssueResolutionState(
                repositoryId = repositoryId,
                messageHash = messageHash
            ).applyAll(events)
        }

    private fun ResultRow.toIssueResolutionEvent() = IssueResolutionEvent(
        repositoryId = this[IssueResolutionEvents.repositoryId],
        messageHash = this[IssueResolutionEvents.messageHash],
        version = this[IssueResolutionEvents.version],
        payload = this[IssueResolutionEvents.payload],
        createdBy = this[IssueResolutionEvents.createdBy],
        createdAt = this[IssueResolutionEvents.createdAt]
    )
}

internal object IssueResolutionEvents : Table("issue_resolution_events") {
    val repositoryId = long("repository_id").transform({ RepositoryId(it) }, { it.value })
    val messageHash = text("message_hash")
    val version = long("version")
    val payload = jsonb<IssueResolutionEventPayload>("payload")
    val createdBy = text("created_by")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(repositoryId, messageHash, version)
}
