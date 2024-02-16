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

import org.eclipse.apoapsis.ortserver.model.runs.scanner.LocalFileStorageConfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a configuration of a local file-based storage.
 */
object LocalFileStorageConfigurationsTable : LongIdTable("local_file_storage_configurations") {
    val directory = text("directory")
    val compression = bool("compression").default(true)
}

class LocalFileStorageConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<LocalFileStorageConfigurationDao>(LocalFileStorageConfigurationsTable) {
        fun findByLocalFileStorageConfiguration(
            localFileStorageConfiguration: LocalFileStorageConfiguration
        ): LocalFileStorageConfigurationDao? =
            find {
                LocalFileStorageConfigurationsTable.directory eq localFileStorageConfiguration.directory and
                        (LocalFileStorageConfigurationsTable.compression eq localFileStorageConfiguration.compression)
            }.singleOrNull()

        fun getOrPut(localFileStorageConfiguration: LocalFileStorageConfiguration): LocalFileStorageConfigurationDao =
            findByLocalFileStorageConfiguration(localFileStorageConfiguration) ?: new {
                directory = localFileStorageConfiguration.directory
                compression = localFileStorageConfiguration.compression
            }
    }

    var directory by LocalFileStorageConfigurationsTable.directory
    var compression by LocalFileStorageConfigurationsTable.compression

    fun mapToModel() = LocalFileStorageConfiguration(
        directory = directory,
        compression = compression
    )
}
