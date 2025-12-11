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
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel

/**
 * A data class that holds information about elements in the hierarchy the current user has access to. A filter
 * instance is created based on the user's permissions and can be used to generate `WHERE` conditions for database
 * queries to only select accessible entities.
 *
 * In ORT Server's role model, permissions granted on higher levels of the hierarchy inherit down to lower levels.
 * Therefore, if a user can access a specific element, the elements below in the hierarchy are accessible as well.
 *
 * There is also the case that an element on a higher level is implicitly accessible because the user has permissions
 * on lower levels. Such elements must be added to results, but not their child elements.
 *
 * All these conditions are reflected by the different properties of this class.
 */
data class HierarchyFilter(
    /**
     * A [Map] with the IDs of elements that should be included together with their child element, grouped by their
     * hierarchy level.
     */
    val transitiveIncludes: Map<HierarchyLevel, List<CompoundHierarchyId>>,

    /**
     * A [Map] with the IDs of elements that should be included, but without their child elements, grouped by their
     * hierarchy level.
     */
    val nonTransitiveIncludes: Map<HierarchyLevel, List<CompoundHierarchyId>>,

    /**
     * A flag whether this filter is a wildcard filter, which means that it matches all elements in the hierarchy.
     * If this is *true*, all other properties can be ignored, and no filter condition needs to be generated. Filters
     * of this type are created if the user has superuser rights.
     */
    val isWildcard: Boolean = false
) {
    companion object {
        /**
         * A special instance of [HierarchyFilter] that declares itself as a wildcard filter and therefore matches
         * all entities. This filter should not alter any query results.
         */
        val WILDCARD = HierarchyFilter(
            transitiveIncludes = emptyMap(),
            nonTransitiveIncludes = emptyMap(),
            isWildcard = true
        )
    }
}
