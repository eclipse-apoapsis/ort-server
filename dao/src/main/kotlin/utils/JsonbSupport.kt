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

package org.eclipse.apoapsis.ortserver.dao.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

/**
 * Create a JSONB column using [jsonForJsonbColumns] for serialization and deserialization. As the null character
 * "\u0000" is not allowed in PostgreSQL JSONB columns, it is handled using [escapeNull] and [unescapeNull].
 */
inline fun <reified T : Any> Table.jsonb(name: String): Column<T> =
    jsonb(
        name = name,
        serialize = { jsonForJsonbColumns.encodeToString(serializer<T>(), it).escapeNull() },
        deserialize = { jsonForJsonbColumns.decodeFromString(serializer<T>(), it.unescapeNull()) }
    )

/**
 * The null character "\u0000" is not allowed in PostgreSQL JSONB columns, so we need to escape it before writing a
 * string to the database.
 * See: [https://www.postgresql.org/docs/11/datatype-json.html]
 */
fun String.escapeNull() = replace("\\u0000", "\\\\u0000")

/**
 * Unescape the null character "\u0000". For details see [escapeNull].
 */
fun String.unescapeNull() = replace("\\\\u0000", "\\u0000")

/**
 * A [Json] instance used to serialize and deserialize values for [jsonb] columns.
 */
val jsonForJsonbColumns = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
