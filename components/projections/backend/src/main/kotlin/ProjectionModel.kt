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

package org.eclipse.apoapsis.ortserver.components.projections

import kotlin.time.Instant

/** The key that identifies the projection of a specific [type] for the ORT run with the given [ortRunId]. */
data class ProjectionKey(
    /** The ID of the ORT run the projection is built for. */
    val ortRunId: Long,

    /** The type of the projection. */
    val type: String
)

/** The status of a projection in the registry. */
enum class ProjectionStatus {
    /** The projection was requested, but its build has not been started yet. */
    PENDING,

    /** The projection is being built by the owner of the lease. */
    BUILDING,

    /** The projection was built successfully and can be queried. */
    READY,

    /** The build of the projection failed. It is retried after a delay. */
    FAILED
}

/** The state of a projection as stored in the registry. */
data class ProjectionState(
    /** The key of the projection. */
    val key: ProjectionKey,

    /** The schema version the projection was requested or built with. */
    val schemaVersion: Int,

    /** The current status of the projection. */
    val status: ProjectionStatus,

    /** The number of attempts to build the projection since it was last requested. */
    val attempts: Int,

    /** The time after which a failed build can be retried, or `null` if no retry is scheduled. */
    val nextAttempt: Instant?,

    /** The error message of the last failed build, or `null` if the last build did not fail. */
    val error: String?
)
