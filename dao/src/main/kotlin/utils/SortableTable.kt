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

package org.eclipse.apoapsis.ortserver.dao.utils

import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * A specialized base class for database tables that provides functionality to mark single columns as sortable.
 *
 * These columns (and only these) can then be referenced as sort fields in a [ListQueryParameters] object.
 */
open class SortableTable(name: String) : LongIdTable(name) {
    /** Stores a map with the columns that are marked as sortable. */
    private val sortableColumns = mutableMapOf<String, Column<*>>()

    /**
     * Mark this [Column] as sortable. Optionally, define a [propertyName] under which this column can be referenced
     * in the sort fields collection of a [ListQueryParameters] instance. If this name is undefined, use the column
     * name instead.
     */
    fun <T : Any> Column<T>.sortable(propertyName: String? = null): Column<T> {
        val finalName = propertyName ?: name
        require(finalName !in sortableColumns) { "Duplicate property name '$finalName' in table '$name'." }

        sortableColumns += finalName to this

        return this
    }

    /**
     * Return the sortable column with the given [property name][name] or *null* if no such column exists.
     */
    fun sortableColumn(name: String): Column<*>? = sortableColumns[name]
}
