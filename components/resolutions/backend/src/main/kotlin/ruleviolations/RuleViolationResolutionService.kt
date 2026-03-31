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

package org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toResultOr

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.jetbrains.exposed.v1.jdbc.Database

class RuleViolationResolutionService(
    private val db: Database,
    private val eventStore: RuleViolationResolutionEventStore,
    private val repositoryService: RepositoryService
) {
    fun createResolution(
        repositoryId: RepositoryId,
        message: String,
        reason: RuleViolationResolutionReason,
        comment: String,
        createdBy: String
    ): Result<Unit, RuleViolationResolutionError> = db.blockingQuery {
        binding {
            validateRepositoryExists(repositoryId).bind()
            val messageHash = calculateResolutionMessageHash(message)
            val state = getRuleViolationResolutionStateOrEmpty(repositoryId, messageHash)
            validateIsDeleted(state).bind()

            val event = RuleViolationResolutionEvent(
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

            eventStore.appendEvent(event)
        }
    }

    fun updateResolutionByHash(
        repositoryId: RepositoryId,
        messageHash: String,
        reason: RuleViolationResolutionReason?,
        comment: String?,
        updatedBy: String
    ): Result<Unit, RuleViolationResolutionError> = db.blockingQuery {
        binding {
            validateRepositoryExists(repositoryId).bind()
            val state = getRuleViolationResolutionStateByHash(repositoryId, messageHash).bind()
            validateNotDeleted(state).bind()

            if (reason == null && comment == null) return@binding Unit

            val event = RuleViolationResolutionEvent(
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
    ): Result<Unit, RuleViolationResolutionError> = db.blockingQuery {
        binding {
            validateRepositoryExists(repositoryId).bind()
            val state = getRuleViolationResolutionStateByHash(repositoryId, messageHash).bind()
            validateNotDeleted(state).bind()

            eventStore.appendEvent(
                RuleViolationResolutionEvent(
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
    ): Result<List<RuleViolationResolution>, RuleViolationResolutionError> = db.blockingQuery {
        binding {
            validateRepositoryExists(repositoryId).bind()

            eventStore.getResolutionsForRepository(repositoryId)
        }
    }

    private fun getRuleViolationResolutionStateByHash(
        repositoryId: RepositoryId,
        messageHash: String
    ): Result<RuleViolationResolutionState, RuleViolationResolutionError> =
        eventStore.getRuleViolationResolution(repositoryId, messageHash).toResultOr {
            RuleViolationResolutionError.ResolutionNotFound(messageHash)
        }

    private fun getRuleViolationResolutionStateOrEmpty(
        repositoryId: RepositoryId,
        messageHash: String
    ): RuleViolationResolutionState =
        eventStore.getRuleViolationResolution(repositoryId, messageHash)
            ?: RuleViolationResolutionState(repositoryId, messageHash)

    private fun validateIsDeleted(
        state: RuleViolationResolutionState
    ): Result<RuleViolationResolutionState, RuleViolationResolutionError> =
        state.takeIf { it.isDeleted }.toResultOr {
            RuleViolationResolutionError.InvalidState(
                "Rule violation resolution for '${state.message}' already exists."
            )
        }

    private fun validateNotDeleted(
        state: RuleViolationResolutionState
    ): Result<RuleViolationResolutionState, RuleViolationResolutionError> =
        state.takeUnless { it.isDeleted }.toResultOr {
            RuleViolationResolutionError.ResolutionNotFound(state.message)
        }

    private fun validateRepositoryExists(repositoryId: RepositoryId): Result<Unit, RuleViolationResolutionError> =
        runBlocking {
            repositoryService.getRepository(repositoryId.value).toResultOr {
                RuleViolationResolutionError.RepositoryNotFound(repositoryId)
            }.map { }
        }
}

sealed class RuleViolationResolutionError(val message: String) {
    class InvalidState(message: String) : RuleViolationResolutionError(message)

    class RepositoryNotFound(repositoryId: RepositoryId) :
        RuleViolationResolutionError("Repository with ID '${repositoryId.value}' not found.")

    class ResolutionNotFound(messageHash: String) :
        RuleViolationResolutionError("Rule violation resolution for '$messageHash' not found.")
}
