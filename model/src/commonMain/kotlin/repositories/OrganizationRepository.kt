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

import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

/**
 * A repository of [organizations][Organization].
 */
interface OrganizationRepository {
    /**
     * Create an organization.
     */
    fun create(name: String, description: String?): Organization

    /**
     * Get an organization by [id]. Returns null if the organization is not found.
     */
    fun get(id: Long): Organization?

    /**
     * List all organizations according to the given [parameters]. Optionally, a [nameFilter] on the product name and a
     * [hierarchyFilter] can be provided.
     */
    fun list(
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        nameFilter: FilterParameter? = null,
        hierarchyFilter: HierarchyFilter = HierarchyFilter.WILDCARD
    ): ListQueryResult<Organization>

    /**
     * Update an organization by [id] with the [present][OptionalValue.Present] values.
     */
    fun update(
        id: Long,
        name: OptionalValue<String> = OptionalValue.Absent,
        description: OptionalValue<String?> = OptionalValue.Absent
    ): Organization

    /**
     * Delete an organization by [id].
     */
    fun delete(id: Long)
}
