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

@file:OptIn(ExperimentalSerializationApi::class)

package org.ossreviewtoolkit.server.dao.utils

import kotlin.reflect.KClass

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

import org.postgresql.util.PGobject

/**
 * This class provides basic support for Jsonb column types in PostgreSQL databases. This is required because Exposed
 * does not support this column type [1].
 *
 * [1]: https://github.com/JetBrains/Exposed/issues/127
 */
private class JsonbColumnType<T : Any>(private val klass: KClass<T>) : ColumnType() {
    override fun sqlType(): String = "JSONB"

    override fun notNullValueToDB(value: Any): Any =
        json.encodeToString(serializer(klass.javaObjectType), value).escapeNull()

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        stmt[index] = PGobject().apply {
            type = sqlType()
            this.value = value as String?
        }
    }

    override fun valueFromDB(value: Any): Any =
        when (value) {
            is PGobject -> json.decodeFromString(serializer(klass.javaObjectType), value.value!!.unescapeNull())
            else -> value
        }
}

fun <T : Any> Table.jsonb(name: String, klass: KClass<T>): Column<T> = registerColumn(name, JsonbColumnType(klass))

/**
 * The null character "\u0000" is not allowed in PostgreSQL JSONB columns so we need to escape it before writing a
 * string to the database.
 * See: [https://www.postgresql.org/docs/11/datatype-json.html]
 */
private fun String.escapeNull() = replace("\\u0000", "\\\\u0000")

/**
 * Unescape the null character "\u0000". For details see [escapeNull].
 */
private fun String.unescapeNull() = replace("\\\\u0000", "\\u0000")

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
