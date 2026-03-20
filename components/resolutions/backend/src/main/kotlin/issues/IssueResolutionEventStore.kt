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
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

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

        updateReadModel(event)
    }

    internal fun getIssueResolution(repositoryId: RepositoryId, messageHash: String) =
        loadEvents(repositoryId, messageHash).takeIf { it.isNotEmpty() }?.let { events ->
            IssueResolutionState(
                repositoryId = repositoryId,
                messageHash = messageHash
            ).applyAll(events)
        }

    internal fun getResolutionsForRepository(repositoryId: RepositoryId): List<IssueResolution> = db.blockingQuery {
        IssueResolutionsReadModel
            .selectAll()
            .where { IssueResolutionsReadModel.repositoryId eq repositoryId }
            .map { it.toIssueResolution() }
    }

    private fun updateReadModel(issueResolutionEvent: IssueResolutionEvent) {
        when (issueResolutionEvent.payload) {
            is Created -> {
                IssueResolutionsReadModel.insert {
                    it[repositoryId] = issueResolutionEvent.repositoryId
                    it[messageHash] = issueResolutionEvent.messageHash
                    it[message] = issueResolutionEvent.payload.message
                    it[reason] = issueResolutionEvent.payload.reason
                    it[comment] = issueResolutionEvent.payload.comment
                }
            }

            is Deleted -> {
                IssueResolutionsReadModel.deleteWhere {
                    (IssueResolutionsReadModel.repositoryId eq issueResolutionEvent.repositoryId) and
                            (IssueResolutionsReadModel.messageHash eq issueResolutionEvent.messageHash)
                }
            }

            is Updated -> {
                IssueResolutionsReadModel.update(where = {
                    (IssueResolutionsReadModel.repositoryId eq issueResolutionEvent.repositoryId) and
                            (IssueResolutionsReadModel.messageHash eq issueResolutionEvent.messageHash)
                }) {
                    if (issueResolutionEvent.payload.reason != null) {
                        it[reason] = issueResolutionEvent.payload.reason
                    }
                    if (issueResolutionEvent.payload.comment != null) {
                        it[comment] = issueResolutionEvent.payload.comment
                    }
                }
            }
        }
    }

    private fun ResultRow.toIssueResolutionEvent() = IssueResolutionEvent(
        repositoryId = this[IssueResolutionEvents.repositoryId],
        messageHash = this[IssueResolutionEvents.messageHash],
        version = this[IssueResolutionEvents.version],
        payload = this[IssueResolutionEvents.payload],
        createdBy = this[IssueResolutionEvents.createdBy],
        createdAt = this[IssueResolutionEvents.createdAt]
    )

    private fun ResultRow.toIssueResolution(): IssueResolution =
        IssueResolution(
            message = this[IssueResolutionsReadModel.message],
            messageHash = this[IssueResolutionsReadModel.messageHash],
            reason = this[IssueResolutionsReadModel.reason],
            comment = this[IssueResolutionsReadModel.comment],
            source = ResolutionSource.SERVER
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

internal object IssueResolutionsReadModel : Table("issue_resolutions_read_model") {
    val repositoryId = long("repository_id").transform({ RepositoryId(it) }, { it.value })
    val messageHash = text("message_hash")
    val message = text("message")
    val reason = text("reason").transform({ enumValueOf<IssueResolutionReason>(it) }, { it.name })
    val comment = text("comment")

    override val primaryKey = PrimaryKey(repositoryId, messageHash)
}
