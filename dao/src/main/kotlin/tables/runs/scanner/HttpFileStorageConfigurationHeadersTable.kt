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

package org.eclipse.apoapsis.ortserver.dao.tables.runs.scanner

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to represent a key-value pair for an HTTP file-based storage.
 */
object HttpFileStorageConfigurationHeadersTable : LongIdTable("http_file_storage_configuration_headers") {
    val httpFileStorageConfigurationId = reference(
        name = "http_file_storage_configuration_id",
        foreign = HttpFileStorageConfigurationsTable
    )

    val key = text("key")
    val value = text("value")
}

class HttpFileStorageConfigurationHeaderDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<HttpFileStorageConfigurationHeaderDao>(HttpFileStorageConfigurationHeadersTable)

    var httpFileStorageConfiguration by HttpFileStorageConfigurationDao referencedOn
            HttpFileStorageConfigurationHeadersTable.httpFileStorageConfigurationId

    var key by HttpFileStorageConfigurationHeadersTable.key
    var value by HttpFileStorageConfigurationHeadersTable.value
}
