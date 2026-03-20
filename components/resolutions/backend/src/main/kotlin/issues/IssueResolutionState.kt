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

import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason

/** The current state of an issue resolution derived from a sequence of events. */
internal class IssueResolutionState(
    val repositoryId: RepositoryId,
    val messageHash: String
) {
    var message: String = ""
        private set

    var reason: IssueResolutionReason = IssueResolutionReason.BUILD_TOOL_ISSUE
        private set

    var comment: String = ""
        private set

    var isDeleted: Boolean = true
        private set

    var version: Long = 0
        private set

    fun apply(event: IssueResolutionEvent) = apply {
        when (event.payload) {
            is Created -> {
                message = event.payload.message
                reason = event.payload.reason
                comment = event.payload.comment
                isDeleted = false
            }

            is Updated -> {
                event.payload.reason?.also { reason = it }
                event.payload.comment?.also { comment = it }
            }

            is Deleted -> isDeleted = true
        }

        version = event.version
    }

    fun applyAll(events: List<IssueResolutionEvent>) = apply {
        events.forEach { apply(it) }
    }
}
