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

package org.eclipse.apoapsis.ortserver.storage.database

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.datetime.xTimestamp

/**
 * Definition of a table for storing arbitrary binary data in a BLOB column.
 */
object StorageTable : LongIdTable("storage") {
    val createdAt = xTimestamp("created_at")
    val namespace = text("namespace")
    val key = text("key")
    val contentType = text("content_type").nullable()
    val size = long("size")
    val data = long("data")
}

class StorageDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StorageDao>(StorageTable)

    var createdAt by StorageTable.createdAt
    var namespace by StorageTable.namespace
    var key by StorageTable.key
    var contentType by StorageTable.contentType
    var size by StorageTable.size
    var data by StorageTable.data
}
