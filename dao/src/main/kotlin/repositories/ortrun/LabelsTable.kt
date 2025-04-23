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

package org.eclipse.apoapsis.ortserver.dao.repositories.ortrun

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent an ORT run.
 */
object LabelsTable : LongIdTable("labels") {
    val key = text("key")
    val value = text("value")
}

class LabelDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<LabelDao>(LabelsTable) {
        private fun findByKeyAndValue(key: String, value: String): LabelDao? =
            find { LabelsTable.key eq key and (LabelsTable.value eq value) }.firstOrNull()

        fun getOrPut(key: String, value: String): LabelDao =
            findByKeyAndValue(key, value) ?: new {
                this.key = key
                this.value = value
            }
    }

    var key by LabelsTable.key
    var value by LabelsTable.value

    fun mapToModel() = Pair(key, value)
}
