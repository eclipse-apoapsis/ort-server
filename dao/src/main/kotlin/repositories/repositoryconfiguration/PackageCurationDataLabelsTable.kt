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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * A table to store key-value style labels for package curations data
 */
object PackageCurationDataLabelsTable : LongIdTable("package_curation_data_labels") {
    val packageCurationDataId = reference("package_curation_data_id", PackageCurationDataTable)
    val key = text("key")
    val value = text("value")
}

class PackageCurationDataLabelDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageCurationDataLabelDao>(PackageCurationDataLabelsTable) {
        fun findByKeyAndValue(key: String, value: String): PackageCurationDataLabelDao? =
            find {
                PackageCurationDataLabelsTable.key eq key and
                        (PackageCurationDataLabelsTable.value eq value)
            }.firstOrNull()

        fun getOrPut(key: String, value: String): PackageCurationDataLabelDao =
            findByKeyAndValue(key, value) ?: new {
                this.key = key
                this.value = value
            }
    }

    var packageCurationData by PackageCurationDataDao referencedOn PackageCurationDataLabelsTable.packageCurationDataId
    var key by PackageCurationDataLabelsTable.key
    var value by PackageCurationDataLabelsTable.value
}
