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

package org.eclipse.apoapsis.ortserver.dao.tables.shared

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent variables for [Environment][EnvironmentsTable]s.
 */
object VariablesTable : LongIdTable("variables") {
    val name = text("name")
    val value = text("value")
}

class VariableDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<VariableDao>(VariablesTable) {
        fun findByNameAndValue(name: String, value: String): VariableDao? =
            find { VariablesTable.name eq name and (VariablesTable.value eq value) }.firstOrNull()

        fun getOrPut(name: String, value: String): VariableDao =
            findByNameAndValue(name, value) ?: new {
                this.name = name
                this.value = value
            }
    }

    var name by VariablesTable.name
    var value by VariablesTable.value

    val environments by EnvironmentDao via EnvironmentsVariablesTable
}
