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

package org.eclipse.apoapsis.ortserver.components.dependencygraph.backend

import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphProjectGroup
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphScope
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField

internal val DEFAULT_DEPENDENCY_GRAPH_SORT_FIELDS = listOf(
    OrderField("name", OrderDirection.ASCENDING)
)

internal fun List<DependencyGraphProjectGroup>.sortProjectGroups(
    sortFields: List<OrderField>
): List<DependencyGraphProjectGroup> =
    sortedWith(createDependencyGraphComparator(sortFields, { it.projectLabel }, { it.packageCount }))

internal fun List<DependencyGraphScope>.sortScopes(sortFields: List<OrderField>): List<DependencyGraphScope> =
    sortedWith(createDependencyGraphComparator(sortFields, { it.scopeLabel ?: it.scopeName }, { it.packageCount }))

private fun <T> createDependencyGraphComparator(
    sortFields: List<OrderField>,
    labelSelector: (T) -> String,
    packageCountSelector: (T) -> Int?
): Comparator<T> =
    sortFields.ifEmpty { DEFAULT_DEPENDENCY_GRAPH_SORT_FIELDS }
        .fold<_, Comparator<T>?>(null) { comparator, orderField ->
            val next = when (orderField.name) {
                "name" -> compareBy<T, String>(String.CASE_INSENSITIVE_ORDER, labelSelector)
                "packageCount" -> compareBy<T> { packageCountSelector(it) ?: 0 }
                else -> throw QueryParametersException("Unsupported sort field: '${orderField.name}'.")
            }.withDirection(orderField.direction)

            comparator?.then(next) ?: next
        } ?: compareBy<T, String>(String.CASE_INSENSITIVE_ORDER, labelSelector)

private fun <T> Comparator<T>.withDirection(direction: OrderDirection): Comparator<T> =
    when (direction) {
        OrderDirection.ASCENDING -> this
        OrderDirection.DESCENDING -> reversed()
    }
