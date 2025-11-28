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

package org.eclipse.apoapsis.ortserver.components.authorization.rights

import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId

/**
 * A data class storing information about a role assignment. An instance holds the assigned [Role] and information
 * about where in the hierarchy the role was assigned. This makes it possible to distinguish between explicit role
 * assignments for a specific level and inherited role assignments from other levels.
 */
data class RoleInfo(
    /** The assigned [Role]. */
    val role: Role,

    /** The hierarchy ID where the role was assigned. */
    val assignedAt: CompoundHierarchyId
)
