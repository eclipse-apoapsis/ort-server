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

import org.eclipse.apoapsis.ortserver.model.runs.Identifier

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent an identifier of a software package.
 */
object IdentifiersTable : LongIdTable("identifiers") {
    val type = text("type")
    val namespace = text("namespace")
    val name = text("name")
    val version = text("version")
}

class IdentifierDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<IdentifierDao>(IdentifiersTable) {
        fun findByIdentifier(identifier: Identifier): IdentifierDao? =
            find {
                IdentifiersTable.type eq identifier.type and
                        (IdentifiersTable.namespace eq identifier.namespace) and
                        (IdentifiersTable.name eq identifier.name) and
                        (IdentifiersTable.version eq identifier.version)
            }.firstOrNull()

        fun getOrPut(identifier: Identifier): IdentifierDao =
            findByIdentifier(identifier) ?: new {
                type = identifier.type
                namespace = identifier.namespace
                name = identifier.name
                version = identifier.version
            }
    }

    var type by IdentifiersTable.type
    var namespace by IdentifiersTable.namespace
    var name by IdentifiersTable.name
    var version by IdentifiersTable.version

    fun mapToModel() = Identifier(type = type, namespace = namespace, name = name, version = version)
}
