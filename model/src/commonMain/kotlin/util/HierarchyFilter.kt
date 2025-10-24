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

package org.eclipse.apoapsis.ortserver.model.util

import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId

/**
 * A class that manages filter conditions for hierarchical entities that are based on a collection of
 * [CompoundHierarchyId]s representing elements the current user has access to. A filter instance can be used to
 * generate `WHERE` conditions for database queries to only select accessible entities.
 */
class HierarchyFilter(
    /**
     * A [Map] with the IDs of elements to be used for filtering, grouped by their hierarchy level.
     */
    val elementsByLevel: Map<Int, List<CompoundHierarchyId>>
) {
    companion object {
        /**
         * A special filter instance that matches all elements in the hierarchy. This filter does not change the
         * result set of a query.
         */
        val WILDCARD = HierarchyFilter(
            mapOf(CompoundHierarchyId.WILDCARD_LEVEL to listOf(CompoundHierarchyId.WILDCARD))
        )

        /**
         * Create a new [HierarchyFilter] for the given [accessibleElements]. With [maxLevel] the elements to be
         * taken into account can be limited to a specific hierarchy level. For instance, if querying products,
         * IDs for repositories are irrelevant.
         */
        fun create(
            accessibleElements: Collection<CompoundHierarchyId>,
            maxLevel: Int = CompoundHierarchyId.REPOSITORY_LEVEL
        ): HierarchyFilter =
            HierarchyFilter(accessibleElements.filter { it.level <= maxLevel }.groupBy { it.level })
    }

    /**
     * Return a flag whether this is a wildcard filter, i.e. it matches all elements in the hierarchy. In this case,
     * there is no need to apply the filter in database queries.
     */
    val isWildcard: Boolean
        get() = CompoundHierarchyId.WILDCARD_LEVEL in elementsByLevel
}
