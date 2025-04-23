/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import kotlinx.serialization.Serializable

/**
 * An enum class defining constants for the direction in which fields are sorted.
 */
enum class OrderDirection {
    /** Constant for _ascending_ order direction. */
    ASCENDING,

    /** Constant for _descending_ order direction. */
    DESCENDING
}

/**
 * A data class defining a field by which a query result should be ordered.
 */
@Serializable
data class OrderField(
    /** The name of the field. */
    val name: String,

    /** The order direction to be applied for this field. */
    val direction: OrderDirection
)

/**
 * A data class defining standard parameters for queries that can return multiple entities.
 *
 * Via the properties defined here, query results can be customized, e.g., by applying ordering or paging. This is a
 * generic mechanism supported by all query functions returning lists.
 */
@Serializable
data class ListQueryParameters(
    /**
     * A list with fields by which the query result should be sorted. Here multiple [OrderField] objects can be
     * specified to come to a fine-granular order definition.
     */
    val sortFields: List<OrderField> = emptyList(),

    /**
     * An optional limit for the number of items to return. This can be used to restrict the data to be fetched.
     * Together with the [offset] property, paging of results can be achieved.
     */
    val limit: Int? = null,

    /**
     * The optional offset for a query. If defined, all query results with a smaller index are dropped. This can be
     * used to implement offset-based paging. Note that in order for paging to work correctly, there must be a
     * defined sort order.
     */
    val offset: Long? = null
) {
    companion object {
        /**
         * Constant for a [ListQueryParameters] instance that does not define any parameters. This instance does not
         * affect the result of a query.
         */
        val DEFAULT = ListQueryParameters()

        /**
         * Constant for [limit] used as default if no limit is given as request parameter.
         */
        const val DEFAULT_LIMIT = 20
    }
}
