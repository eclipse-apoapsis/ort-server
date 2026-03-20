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

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toResultOr

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.jetbrains.exposed.v1.jdbc.Database

class IssueResolutionService(
    private val db: Database,
    private val eventStore: IssueResolutionEventStore,
    private val repositoryService: RepositoryService
) {
    internal fun createResolution(
        repositoryId: RepositoryId,
        message: String,
        reason: IssueResolutionReason,
        comment: String,
        createdBy: String
    ): Result<IssueResolutionState, IssueResolutionError> = db.blockingQuery {
        binding {
            validateRepositoryExists(repositoryId).bind()
            val messageHash = calculateResolutionMessageHash(message)
            val state = getIssueResolutionStateOrEmpty(repositoryId, messageHash)
            validateIsDeleted(state).bind()

            val event = IssueResolutionEvent(
                repositoryId = repositoryId,
                messageHash = messageHash,
                version = state.version + 1,
                payload = Created(
                    message = message,
                    reason = reason,
                    comment = comment
                ),
                createdBy = createdBy
            )
            val newState = state.apply(event)

            eventStore.appendEvent(event)
            newState
        }
    }

    fun updateResolutionByHash(
        repositoryId: RepositoryId,
        messageHash: String,
        reason: IssueResolutionReason?,
        comment: String?,
        updatedBy: String
    ): Result<Unit, IssueResolutionError> = db.blockingQuery {
        binding {
            validateRepositoryExists(repositoryId).bind()
            val state = getIssueResolutionStateByHash(repositoryId, messageHash).bind()
            validateNotDeleted(state).bind()

            if (reason == null && comment == null) return@binding Unit

            val event = IssueResolutionEvent(
                repositoryId = repositoryId,
                messageHash = messageHash,
                version = state.version + 1,
                payload = Updated(
                    reason = reason,
                    comment = comment
                ),
                createdBy = updatedBy
            )

            eventStore.appendEvent(event)
        }
    }

    fun deleteResolutionByHash(
        repositoryId: RepositoryId,
        messageHash: String,
        deletedBy: String
    ): Result<Unit, IssueResolutionError> = db.blockingQuery {
        binding {
            validateRepositoryExists(repositoryId).bind()
            val state = getIssueResolutionStateByHash(repositoryId, messageHash).bind()
            validateNotDeleted(state).bind()

            eventStore.appendEvent(
                IssueResolutionEvent(
                    repositoryId = repositoryId,
                    messageHash = messageHash,
                    version = state.version + 1,
                    payload = Deleted,
                    createdBy = deletedBy
                )
            )
        }
    }

    fun getResolutionsForRepository(
        repositoryId: RepositoryId
    ): Result<List<IssueResolution>, IssueResolutionError> = db.blockingQuery {
        binding {
            validateRepositoryExists(repositoryId).bind()

            eventStore.getResolutionsForRepository(repositoryId)
        }
    }

    private fun getIssueResolutionStateByHash(
        repositoryId: RepositoryId,
        messageHash: String
    ): Result<IssueResolutionState, IssueResolutionError> =
        eventStore.getIssueResolution(repositoryId, messageHash).toResultOr {
            IssueResolutionError.ResolutionNotFound(messageHash)
        }

    private fun getIssueResolutionStateOrEmpty(
        repositoryId: RepositoryId,
        messageHash: String
    ): IssueResolutionState =
        eventStore.getIssueResolution(repositoryId, messageHash)
            ?: IssueResolutionState(repositoryId, messageHash)

    private fun validateIsDeleted(
        state: IssueResolutionState
    ): Result<IssueResolutionState, IssueResolutionError> =
        state.takeIf { it.isDeleted }.toResultOr {
            IssueResolutionError.InvalidState("Issue resolution for '${state.message}' already exists.")
        }

    private fun validateNotDeleted(
        state: IssueResolutionState
    ): Result<IssueResolutionState, IssueResolutionError> =
        state.takeUnless { it.isDeleted }.toResultOr {
            IssueResolutionError.ResolutionNotFound(state.message)
        }

    private fun validateRepositoryExists(repositoryId: RepositoryId): Result<Unit, IssueResolutionError> =
        runBlocking {
            repositoryService.getRepository(repositoryId.value).toResultOr {
                IssueResolutionError.RepositoryNotFound(repositoryId)
            }.map { }
        }
}

sealed class IssueResolutionError(val message: String) {
    class InvalidState(message: String) : IssueResolutionError(message)

    class RepositoryNotFound(repositoryId: RepositoryId) :
        IssueResolutionError("Repository with ID '${repositoryId.value}' not found.")

    class ResolutionNotFound(message: String) :
        IssueResolutionError("Issue resolution for '$message' not found.")
}
