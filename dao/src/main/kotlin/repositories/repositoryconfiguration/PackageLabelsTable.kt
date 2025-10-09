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

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackageDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to store key-value style labels for packages.
 */
object PackageLabelsTable : LongIdTable("package_labels") {
    val packageId = reference("package_id", PackagesTable)
    val key = text("key")
    val value = text("value")
}

class PackageLabelDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageLabelDao>(PackageLabelsTable) {
        fun findByKeyAndValue(key: String, value: String): PackageLabelDao? =
            find {
                PackageLabelsTable.key eq key and
                        (PackageLabelsTable.value eq value)
            }.firstOrNull()

        fun getOrPut(key: String, value: String): PackageLabelDao =
            findByKeyAndValue(key, value) ?: new {
                this.key = key
                this.value = value
            }
    }

    var pkg by PackageDao referencedOn PackageLabelsTable.packageId
    var key by PackageLabelsTable.key
    var value by PackageLabelsTable.value
}
