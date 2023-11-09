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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.mapAndDeduplicate
import org.ossreviewtoolkit.server.dao.tables.ScanResultDao
import org.ossreviewtoolkit.server.dao.tables.ScannerJobDao
import org.ossreviewtoolkit.server.dao.tables.provenance.NestedProvenanceDao
import org.ossreviewtoolkit.server.dao.tables.provenance.PackageProvenanceDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AnalyzerRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackageDao
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
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerConfigurationSecretDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerConfigurationStorageDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerConfigurationsOptionsTable
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerConfigurationsSecretsTable
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.ScannerRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.StorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.scanner.Sw360StorageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.model.repositories.ScannerRunRepository
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.runs.scanner.ArtifactProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileArchiveConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileBasedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.KnownProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.NestedProvenanceScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.PostgresStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceResolutionResult
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.RepositoryProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.ScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.ScanStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.server.model.runs.scanner.Sw360StorageConfiguration

/**
 * An implementation of [ScannerRunRepository] that stores scanner runs in the [ScannerRunsTable].
 */
class DaoScannerRunRepository(private val db: Database) : ScannerRunRepository {
    override fun create(scannerJobId: Long): ScannerRun = db.blockingQuery {
        val scannerRunDao = ScannerRunDao.new {
            this.scannerJob = ScannerJobDao[scannerJobId]
        }

        scannerRunDao.mapToModel()
    }

    override fun update(
        id: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: ScannerConfiguration
    ) = db.blockingQuery {
        val scannerRunDao = ScannerRunDao[id]

        require(scannerRunDao.startTime == null) {
            "Called 'update' for scanner run '${scannerRunDao.id}' which already has a start time set."
        }

        scannerRunDao.startTime = startTime
        scannerRunDao.endTime = endTime
        scannerRunDao.environment = EnvironmentDao.getOrPut(environment)

        createScannerConfiguration(scannerRunDao, config)

        scannerRunDao.mapToModel()
    }

    /**
     * Due to the fact that there are no junction tables storing the scan results for a [ScannerRun], it has to be
     * queried from the database with JOIN clauses. First, all packages from an [AnalyzerRunsTable] have to be collected
     * that belong to the same ORT run like the requested [ScannerRun]. With the help of the packages, package
     * provenances can be queried and a [NestedProvenanceScanResult] can be created.
     */
    override fun get(id: Long): ScannerRun? = db.entityQuery {
        // Get all packages for the scanner run, based on the related analyzer run.
        val packages = getPackagesForScannerRun(id)

        // Get the provenance resolution results for all packages.
        val provenanceResolutionResults = getProvenanceResolutionResults(packages)

        // Get the scan results for all provenances.
        val allProvenances = provenanceResolutionResults.flatMapTo(mutableSetOf()) { it.getProvenances() }
        val scanResults = getScanResults(allProvenances)

        ScannerRunDao[id].mapToModel().copy(provenances = provenanceResolutionResults, scanResults = scanResults)
    }

    override fun getByJobId(scannerJobId: Long): ScannerRun? = db.blockingQuery {
        ScannerRunDao.find { ScannerRunsTable.scannerJobId eq scannerJobId }.firstOrNull()?.let { get(it.id.value) }
    }

    /**
     * Get all packages that are related to the scanner run identified by [id].
     */
    private fun getPackagesForScannerRun(id: Long): List<PackageDao> {
        val scannerRun = ScannerRunDao[id]
        val analyzerRun = scannerRun.scannerJob.ortRun.analyzerJob?.analyzerRun
        return analyzerRun?.packages?.toList().orEmpty()
    }

    /**
     * Get the [ProvenanceResolutionResult]s for the provided [packages].
     */
    private fun getProvenanceResolutionResults(
        packages: List<PackageDao>
    ): Set<ProvenanceResolutionResult> = buildSet {
        packages.forEach { pkg ->
            val identifier = pkg.identifier.mapToModel()

            val packageProvenance = PackageProvenanceDao.findByPackage(pkg)
            val packageProvenanceModel = packageProvenance?.mapToModel()
            if (packageProvenance == null || packageProvenanceModel !is KnownProvenance) {
                val result = ProvenanceResolutionResult(
                    id = identifier,
                    packageProvenanceResolutionIssue = OrtIssue(
                        timestamp = Clock.System.now(),
                        source = "scanner",
                        message = "Could not resolve provenance for package '$identifier'.",
                        severity = "ERROR"
                    )
                )
                add(result)
                return@forEach
            }

            val nestedProvenance = NestedProvenanceDao.findByRootProvenance(packageProvenance)
            if (packageProvenance.vcs != null && nestedProvenance == null) {
                val result = ProvenanceResolutionResult(
                    id = identifier,
                    packageProvenance = packageProvenanceModel,
                    nestedProvenanceResolutionIssue = OrtIssue(
                        timestamp = Clock.System.now(),
                        source = "scanner",
                        message = "Could not resolve nested provenance for provenance " +
                                "'${packageProvenance.mapToModel()}'.",
                        severity = "ERROR"
                    )
                )
                add(result)
                return@forEach
            }

            val result = ProvenanceResolutionResult(
                id = identifier,
                packageProvenance = packageProvenanceModel,
                subRepositories = nestedProvenance?.subRepositories?.associate {
                    it.path to it.vcs.mapToModel()
                }.orEmpty()
            )
            add(result)
        }
    }

    /**
     * Return all [ScanResult]s for the provided [provenances].
     */
    private fun getScanResults(provenances: MutableSet<KnownProvenance>): Set<ScanResult> = buildSet {
        provenances.forEach { provenance ->
            val scanResults = when (provenance) {
                is ArtifactProvenance -> ScanResultDao.findByRemoteArtifact(provenance.sourceArtifact)
                is RepositoryProvenance ->
                    ScanResultDao.findByVcsInfo(provenance.vcsInfo.copy(revision = provenance.resolvedRevision))
            }

            addAll(scanResults.map { it.mapToModel() })
        }
    }
}

private fun createScannerConfiguration(
    scannerRunDao: ScannerRunDao,
    scannerConfiguration: ScannerConfiguration
): ScannerConfigurationDao {
    val detectedLicenseMappings = mapAndDeduplicate(scannerConfiguration.detectedLicenseMappings.entries) {
        DetectedLicenseMappingDao.getOrPut(it.toPair())
    }

    val scannerConfigurationDao = ScannerConfigurationDao.new {
        this.scannerRun = scannerRunDao
        this.skipConcluded = scannerConfiguration.skipConcluded
        this.createMissingArchives = scannerConfiguration.createMissingArchives
        this.ignorePatterns = scannerConfiguration.ignorePatterns
        this.storageReaders = scannerConfiguration.storageReaders
        this.storageWriters = scannerConfiguration.storageWriters
        this.detectedLicenseMappings = detectedLicenseMappings
    }

    scannerConfiguration.config.forEach { (scanner, pluginConfig) ->
        pluginConfig.options.forEach { (option, value) ->
            val optionDao = ScannerConfigurationOptionDao.getOrPut(scanner, option, value)

            ScannerConfigurationsOptionsTable.insert {
                it[scannerConfigurationId] = scannerConfigurationDao.id
                it[scannerConfigurationOptionId] = optionDao.id
            }
        }

        pluginConfig.secrets.forEach { (secret, value) ->
            val secretDao = ScannerConfigurationSecretDao.getOrPut(scanner, secret, value)

            ScannerConfigurationsSecretsTable.insert {
                it[scannerConfigurationId] = scannerConfigurationDao.id
                it[scannerConfigurationSecretId] = secretDao.id
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

            else -> null
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
