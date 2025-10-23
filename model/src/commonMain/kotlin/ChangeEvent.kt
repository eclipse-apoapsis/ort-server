/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model

import kotlinx.datetime.Instant

/**
 * A data class representing a change event performed by a user.
 */
data class ChangeEvent(
    /** The user who performed the change. */
    val user: UserDisplayName,

    /** The time the change occurred. */
    val occurredAt: Instant,

    /** The action performed. */
    val action: ChangeEventAction
)

/**
 * An enumeration of the entity types that can be affected by a [ChangeEvent].
 */
enum class ChangeEventEntityType {
    VULNERABILITY_RESOLUTION_DEFINITION
}

/**
 * An enumeration of the actions that can be performed, resulting in a [ChangeEvent].
 */
enum class ChangeEventAction {
    /** The creation of a new entity. */
    CREATE,

    /** The update of an existing entity. */
    UPDATE,

    /** The archival, i.e. soft deletion, of an existing entity. */
    ARCHIVE,

    /** The restoration, i.e. un-archival, of an archived entity. */
    RESTORE
}
