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

package org.ossreviewtoolkit.server.dao.tables.runs.scanner

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.model.runs.scanner.FileArchiveConfiguration

/**
 * A table to represent a configuration of a file archiver.
 */
object FileArchiverConfigurationsTable : LongIdTable("file_archiver_configurations") {
    val enabled = bool("enabled").default(true)
    val scannerConfigurationId = reference("scanner_configuration_id", ScannerConfigurationsTable)
    val fileStorageConfigurationId = reference(
        name = "file_storage_configuration_id",
        foreign = FileStorageConfigurationsTable
    ).nullable()
    val postgresStorageConfigurationId = reference(
        name = "postgres_storage_configuration_id",
        foreign = PostgresStorageConfigurationsTable
    ).nullable()
}

class FileArchiverConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<FileArchiverConfigurationDao>(FileArchiverConfigurationsTable)

    var scannerConfiguration by ScannerConfigurationDao referencedOn
            FileArchiverConfigurationsTable.scannerConfigurationId
    var fileStorageConfiguration by FileStorageConfigurationDao optionalReferencedOn
            FileArchiverConfigurationsTable.fileStorageConfigurationId
    var postgresStorageConfiguration by PostgresStorageConfigurationDao optionalReferencedOn
            FileArchiverConfigurationsTable.postgresStorageConfigurationId

    var enabled by FileArchiverConfigurationsTable.enabled

    fun mapToModel() = FileArchiveConfiguration(
        enabled = enabled,
        fileStorage = fileStorageConfiguration?.mapToModel(),
        postgresStorage = postgresStorageConfiguration?.mapToModel()
    )
}
