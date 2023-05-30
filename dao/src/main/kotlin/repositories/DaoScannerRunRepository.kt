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

package org.ossreviewtoolkit.server.dao.repositories

import kotlinx.datetime.Instant

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SizedCollection

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.ScannerJobDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ClearlyDefinedStorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.DetectedLicenseMappingDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.FileArchiverConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.FileBasedStorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.FileStorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.HttpFileStorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.LocalFileStorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.PostgresConnectionDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.PostgresStorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ProvenanceStorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerConfigurationOptionDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerConfigurationScannerOptionDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerConfigurationStorageDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.StorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.Sw360StorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.model.repositories.ScannerRunRepository
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.scanner.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileArchiveConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileBasedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.PostgresStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.ScanStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.server.model.runs.scanner.Sw360StorageConfiguration

/**
 * An implementation of [ScannerRunRepository] that stores scanner runs in the [ScannerRunsTable].
 */
class DaoScannerRunRepository(private val db: Database) : ScannerRunRepository {
    override fun create(
        scannerJobId: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: ScannerConfiguration,
        results: Map<Identifier, List<ScanResult>>
    ): ScannerRun = db.blockingQuery {
        val environmentDao = EnvironmentDao.getOrPut(environment)

        val scannerRunDao = ScannerRunDao.new {
            this.scannerJob = ScannerJobDao[scannerJobId]
            this.startTime = startTime
            this.endTime = endTime
            this.environment = environmentDao
        }

        createScannerConfiguration(scannerRunDao, config)

        scannerRunDao.mapToModel()
    }

    override fun get(id: Long): ScannerRun? = db.entityQuery { ScannerRunDao[id].mapToModel() }

    override fun getByJobId(scannerJobId: Long): ScannerRun? = db.blockingQuery {
        ScannerRunDao.find { ScannerRunsTable.scannerJobId eq scannerJobId }.firstOrNull()?.mapToModel()
    }
}

private fun createScannerConfiguration(
    scannerRunDao: ScannerRunDao,
    scannerConfiguration: ScannerConfiguration
): ScannerConfigurationDao {
    val detectedLicenseMappings = scannerConfiguration.detectedLicenseMappings.map {
        DetectedLicenseMappingDao.getOrPut(it.toPair())
    }

    val scannerConfigurationDao = ScannerConfigurationDao.new {
        this.scannerRun = scannerRunDao
        this.skipConcluded = scannerConfiguration.skipConcluded
        this.createMissingArchives = scannerConfiguration.createMissingArchives
        this.ignorePatterns = scannerConfiguration.ignorePatterns
        this.storageReaders = scannerConfiguration.storageReaders
        this.storageWriters = scannerConfiguration.storageWriters
        this.detectedLicenseMappings = SizedCollection(detectedLicenseMappings)
    }

    scannerConfiguration.options.forEach { (scanner, scannerOptions) ->
        val scannerOptionKey = ScannerConfigurationScannerOptionDao.new {
            this.scannerConfiguration = scannerConfigurationDao
            this.scanner = scanner
        }

        scannerOptions.forEach { (key, value) ->
            ScannerConfigurationOptionDao.new {
                this.scannerOption = scannerOptionKey
                this.key = key
                this.value = value
            }
        }
    }

    createFileArchiverConfiguration(scannerConfigurationDao, scannerConfiguration.archive)
    createProvenanceStorageConfiguration(scannerConfigurationDao, scannerConfiguration.provenanceStorage)
    createStorages(scannerConfigurationDao, scannerConfiguration.storages)

    return scannerConfigurationDao
}

fun createStorages(
    scannerConfigurationDao: ScannerConfigurationDao,
    storages: Map<String, ScanStorageConfiguration?>?
): List<StorageConfigurationDao?>? =
    storages?.map { (storage, storageConfiguration) ->
        val scannerStorageConfigurationDao = ScannerConfigurationStorageDao.new {
            this.scannerConfiguration = scannerConfigurationDao
            this.storage = storage
        }

        when (storageConfiguration) {
            is ClearlyDefinedStorageConfiguration -> {
                StorageConfigurationDao.new {
                    this.scannerConfigurationStorage = scannerStorageConfigurationDao
                    this.clearlyDefinedStorageConfiguration = ClearlyDefinedStorageConfigurationDao
                        .getOrPut(storageConfiguration)
                }
            }

            is PostgresStorageConfiguration -> {
                StorageConfigurationDao.new {
                    this.scannerConfigurationStorage = scannerStorageConfigurationDao
                    this.postgresStorageConfiguration = createPostgresStorageConfiguration(storageConfiguration)
                }
            }

            is FileBasedStorageConfiguration -> {
                StorageConfigurationDao.new {
                    this.scannerConfigurationStorage = scannerStorageConfigurationDao
                    this.fileBasedStorageConfiguration = createFileBasedStorageConfiguration(storageConfiguration)
                }
            }

            is Sw360StorageConfiguration -> {
                StorageConfigurationDao.new {
                    this.scannerConfigurationStorage = scannerStorageConfigurationDao
                    this.sw360StorageConfiguration = Sw360StorageConfigurationDao.getOrPut(storageConfiguration)
                }
            }

            else -> { null }
        }
    }

private fun createProvenanceStorageConfiguration(
    scannerConfigurationDao: ScannerConfigurationDao,
    provenanceStorageConfiguration: ProvenanceStorageConfiguration?
): ProvenanceStorageConfigurationDao? =
    provenanceStorageConfiguration?.let {
        ProvenanceStorageConfigurationDao.new {
            this.scannerConfiguration = scannerConfigurationDao
            this.fileStorageConfiguration = it.fileStorage?.let { createFileStorageConfiguration(it) }
            this.postgresStorageConfiguration = it.postgresStorageConfiguration?.let {
                createPostgresStorageConfiguration(it)
            }
        }
    }

private fun createFileArchiverConfiguration(
    scannerConfigurationDao: ScannerConfigurationDao,
    archive: FileArchiveConfiguration?
): FileArchiverConfigurationDao? =
    archive?.let {
        FileArchiverConfigurationDao.new {
            this.scannerConfiguration = scannerConfigurationDao
            this.fileStorageConfiguration = it.fileStorage?.let { createFileStorageConfiguration(it) }
            this.postgresStorageConfiguration = it.postgresStorage?.let { createPostgresStorageConfiguration(it) }
        }
    }

private fun createFileStorageConfiguration(
    fileStorageConfiguration: FileStorageConfiguration
): FileStorageConfigurationDao =
    FileStorageConfigurationDao.new {
        this.localFileStorageConfiguration = fileStorageConfiguration.localFileStorage?.let {
            LocalFileStorageConfigurationDao.getOrPut(it)
        }

        this.httpFileStorageConfiguration = fileStorageConfiguration.httpFileStorage?.let {
            HttpFileStorageConfigurationDao.getOrPut(it)
        }
    }

private fun createPostgresStorageConfiguration(
    postgresStorageConfiguration: PostgresStorageConfiguration
): PostgresStorageConfigurationDao =
    PostgresStorageConfigurationDao.new {
        this.postgresConnection = PostgresConnectionDao.getOrPut(postgresStorageConfiguration.connection)
        this.type = postgresStorageConfiguration.type
    }

private fun createFileBasedStorageConfiguration(
    fileBasedStorageConfiguration: FileBasedStorageConfiguration
): FileBasedStorageConfigurationDao =
    FileBasedStorageConfigurationDao.new {
        this.fileStorageConfiguration = createFileStorageConfiguration(fileBasedStorageConfiguration.backend)
        this.type = fileBasedStorageConfiguration.type
    }
