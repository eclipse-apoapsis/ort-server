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

package org.eclipse.apoapsis.ortserver.dao.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.append

/**
 * A custom operation to check if the aggregated values of a string column are equal to a set of values, to be used in a
 * WHERE or HAVING clause. This is useful for checking if a column contains a set of values, which is not directly
 * supported by Exposed.
 *
 * All values are encoded as base64 before comparison to handle special characters and to avoid SQL injection.
 */
class ArrayAggColumnEquals(
    private val column: Column<String>,
    private val value: Set<String>
) : Op<Boolean>() {
    @OptIn(ExperimentalEncodingApi::class)
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        if (value.isEmpty()) {
            append("ARRAY_AGG(DISTINCT ")
            append("ENCODE(CONVERT_TO(", column, ", 'UTF8'), 'base64')")
            append(") = ARRAY[NULL]")
        } else {
            val joinedValue = value.joinToString { "'${Base64.encode(it.toByteArray())}'" }

            append("ARRAY_AGG(DISTINCT ")
            append("ENCODE(CONVERT_TO(", column, ", 'UTF8'), 'base64')")
            append(") <@ ARRAY[", joinedValue, "]")
            append(" AND ")
            append("ARRAY_AGG(DISTINCT ")
            append("ENCODE(CONVERT_TO(", column, ", 'UTF8'), 'base64')")
            append(") @> ARRAY[", joinedValue, "]")
        }
    }
}

/**
 * A custom operation to check if the aggregated values of two string columns are equal to a map of values, to be used
 * in a WHERE or HAVING clause. This is useful for checking if a column contains a set of values, which is not directly
 * supported by Exposed.
 *
 * The keys of the map are the values of the first column, and the values of the map are the values of the second
 * column.
 *
 * All values are encoded as base64 before comparison to handle special characters and to avoid SQL injection.
 */
class ArrayAggTwoColumnsEquals(
    private val column1: Column<String>,
    private val column2: Column<String>,
    private val values: Map<String, String>
) : Op<Boolean>() {
    @OptIn(ExperimentalEncodingApi::class)
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val joinedValues = if (values.isEmpty()) {
            "':'"
        } else {
            values.entries.joinToString {
                "'${Base64.encode(it.key.toByteArray())}:${Base64.encode(it.value.toByteArray())}'"
            }
        }

        append("ARRAY_AGG(DISTINCT CONCAT(")
        append("ENCODE(CONVERT_TO(", column1, ", 'UTF8'), 'base64'), ")
        append("':', ")
        append("ENCODE(CONVERT_TO(", column2, ", 'UTF8'), 'base64') ")
        append(")) <@ ARRAY[", joinedValues, "]")
        append(" AND ")
        append("ARRAY_AGG(DISTINCT CONCAT(")
        append("ENCODE(CONVERT_TO(", column1, ", 'UTF8'), 'base64'), ")
        append("':', ")
        append("ENCODE(CONVERT_TO(", column2, ", 'UTF8'), 'base64') ")
        append(")) @> ARRAY[", joinedValues, "]")
    }
}
