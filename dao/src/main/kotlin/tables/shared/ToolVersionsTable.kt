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
 * A table to represent tool versions for [Environment][EnvironmentsTable]s.
 */
object ToolVersionsTable : LongIdTable("tool_versions") {
    val name = text("name")
    val version = text("version")
}

class ToolVersionDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ToolVersionDao>(ToolVersionsTable) {
        fun findByNameAndVersion(name: String, version: String): ToolVersionDao? =
            find { ToolVersionsTable.name eq name and (ToolVersionsTable.version eq version) }.firstOrNull()

        fun getOrPut(name: String, version: String): ToolVersionDao =
            findByNameAndVersion(name, version) ?: new {
                this.name = name
                this.version = version
            }
    }

    var name by ToolVersionsTable.name
    var version by ToolVersionsTable.version

    val environments by EnvironmentDao via EnvironmentsToolVersionsTable
}
