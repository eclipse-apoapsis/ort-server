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

import kotlin.time.Clock
import kotlin.time.Instant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason

internal data class IssueResolutionEvent(
    val repositoryId: RepositoryId,
    val messageHash: String,
    val version: Long,
    val payload: IssueResolutionEventPayload,
    val createdBy: String,
    val createdAt: Instant = Clock.System.now()
)

@Serializable
internal sealed interface IssueResolutionEventPayload

@Serializable
@SerialName("Created")
internal class Created(
    val message: String,
    val reason: IssueResolutionReason,
    val comment: String
) : IssueResolutionEventPayload

@Serializable
@SerialName("Updated")
internal class Updated(
    val reason: IssueResolutionReason?,
    val comment: String?
) : IssueResolutionEventPayload

@Serializable
@SerialName("Deleted")
internal object Deleted : IssueResolutionEventPayload
