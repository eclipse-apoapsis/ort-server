/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.repositories

import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

/**
 * A repository of [secrets][Secret].
 */
interface SecretRepository {
    /**
     * Create a secret for the given hierarchy [id].
     */
    fun create(path: String, name: String, description: String?, id: HierarchyId): Secret

    /**
     * Get a secret by [id] and [name]. Returns null if the secret is not found.
     */
    fun getByIdAndName(id: HierarchyId, name: String): Secret?

    /**
     * List all secrets for an [id] according to the given [parameters].
     * Only if [includeDeleted] is true, also includes secrets that are marked as deleted.
     */
    fun listForId(
        id: HierarchyId,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        includeDeleted: Boolean = false
    ): ListQueryResult<Secret>

    /**
     * Update a secret by [id] and name with the [present][OptionalValue.Present] values.
     */
    fun updateForIdAndName(id: HierarchyId, name: String, description: OptionalValue<String?>): Secret

    /**
     * Mark a secret as deleted by [id] and secret's [name].
     */
    fun markAsDeletedForIdAndName(id: HierarchyId, name: String): Secret?
}
