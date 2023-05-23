/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.server.model.runs.scanner.ScanStorageConfiguration

/**
 * A table to store a specific storage configuration.
 */
object StorageConfigurationsTable : LongIdTable("storage_configurations") {
    val scannerConfigurationStorageId = reference(
        name = "scanner_configuration_storage_id",
        foreign = ScannerConfigurationsStoragesTable
    )
    val clearlyDefinedStorageId = reference(
        name = "clearly_defined_storage_configuration_id",
        foreign = ClearlyDefinedStorageConfigurationsTable
    ).nullable()
    val fileBasedStorageConfigurationId = reference(
        name = "file_based_storage_configuration_id",
        foreign = FileBasedStorageConfigurationsTable
    ).nullable()
    val postgresStorageConfigurationId = reference(
        name = "postgres_storage_configuration_id",
        foreign = PostgresStorageConfigurationsTable
    ).nullable()
    val sw360StorageConfigurationId = reference(
        name = "sw360_storage_configuration_id",
        foreign = Sw360StorageConfigurationsTable
    ).nullable()
}

class StorageConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StorageConfigurationDao>(StorageConfigurationsTable)

    var scannerConfigurationStorage by ScannerConfigurationStorageDao referencedOn
            StorageConfigurationsTable.scannerConfigurationStorageId
    var clearlyDefinedStorageConfiguration by ClearlyDefinedStorageConfigurationDao optionalReferencedOn
            StorageConfigurationsTable.clearlyDefinedStorageId
    var fileBasedStorageConfiguration by FileBasedStorageConfigurationDao optionalReferencedOn
            StorageConfigurationsTable.fileBasedStorageConfigurationId
    var postgresStorageConfiguration by PostgresStorageConfigurationDao optionalReferencedOn
            StorageConfigurationsTable.postgresStorageConfigurationId
    var sw360StorageConfiguration by Sw360StorageConfigurationDao optionalReferencedOn
            StorageConfigurationsTable.sw360StorageConfigurationId

    fun mapToModel(): ScanStorageConfiguration? = when {
        clearlyDefinedStorageConfiguration is ClearlyDefinedStorageConfigurationDao ->
            clearlyDefinedStorageConfiguration?.mapToModel()
        fileBasedStorageConfiguration is FileBasedStorageConfigurationDao -> fileBasedStorageConfiguration?.mapToModel()
        postgresStorageConfiguration is PostgresStorageConfigurationDao -> postgresStorageConfiguration?.mapToModel()
        sw360StorageConfiguration is Sw360StorageConfigurationDao -> sw360StorageConfiguration?.mapToModel()
        else -> null
    }
}
