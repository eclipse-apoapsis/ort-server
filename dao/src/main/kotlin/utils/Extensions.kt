/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.minus

import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.SortOrder

import org.ossreviewtoolkit.server.dao.QueryParametersException
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OrderDirection

/**
 * Convert an instance to microsecond precision which is stored in the database. This function should always be used
 * when writing timestamps to the database to ensure that the created DAO objects use the same precision as the
 * database.
 */
internal fun Instant.toDatabasePrecision() = minus(nanosecondsOfSecond, DateTimeUnit.NANOSECOND)

/**
 * Apply the given [parameters] to this query result using [table] to resolve the columns to be sorted by.
 */
internal fun <T> SizedIterable<T>.apply(table: SortableTable, parameters: ListQueryParameters): SizedIterable<T> {
    val orders = parameters.sortFields.map {
        val column = table.sortableColumn(it.name)
            ?: throw QueryParametersException("Unsupported field for sorting: '${it.name}'.")
        column to it.direction.toSortOrder()
    }.toTypedArray()

    val orderedQuery = orderBy(*orders)
    return parameters.limit?.let { orderedQuery.limit(it, parameters.offset ?: 0) } ?: orderedQuery
}

/**
 * Convert this [OrderDirection] constant to the corresponding [SortOrder].
 */
private fun OrderDirection.toSortOrder(): SortOrder =
    when (this) {
        OrderDirection.ASCENDING -> SortOrder.ASC
        OrderDirection.DESCENDING -> SortOrder.DESC
    }
