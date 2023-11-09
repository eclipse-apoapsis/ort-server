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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.server.dao.tables.ScanResultDao
import org.ossreviewtoolkit.server.dao.tables.ScanSummaryDao
import org.ossreviewtoolkit.server.dao.tables.provenance.NestedProvenanceDao
import org.ossreviewtoolkit.server.dao.tables.provenance.NestedProvenanceSubRepositoryDao
import org.ossreviewtoolkit.server.dao.tables.provenance.PackageProvenanceDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.PluginConfiguration
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.scanner.ArtifactProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileArchiveConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileBasedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.LocalFileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.PostgresConnection
import org.ossreviewtoolkit.server.model.runs.scanner.PostgresStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceResolutionResult
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.RepositoryProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.ScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun

class DaoScannerRunRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var scannerRunRepository: DaoScannerRunRepository
    lateinit var analyzerRunRepository: DaoAnalyzerRunRepository
    lateinit var fixtures: Fixtures

    var scannerJobId = -1L

    beforeEach {
        scannerRunRepository = dbExtension.fixtures.scannerRunRepository
        analyzerRunRepository = dbExtension.fixtures.analyzerRunRepository
        fixtures = dbExtension.fixtures

        scannerJobId = dbExtension.fixtures.scannerJob.id
    }

    "create should create an entry in the database" {
        val createdScannerRun = scannerRunRepository.create(scannerJobId)

        val dbEntry = scannerRunRepository.get(createdScannerRun.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe scannerRun.copy(
            id = createdScannerRun.id,
            scannerJobId = scannerJobId,
            startTime = null,
            endTime = null,
            environment = null,
            config = null
        )
    }

    "update should store the provided values" {
        val createdScannerRun = scannerRunRepository.create(scannerJobId)

        scannerRunRepository.update(
            createdScannerRun.id,
            scannerRun.startTime!!,
            scannerRun.endTime!!,
            scannerRun.environment!!,
            scannerRun.config!!
        )

        val dbEntry = scannerRunRepository.get(createdScannerRun.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe scannerRun.copy(id = createdScannerRun.id, scannerJobId = scannerJobId)
    }

    "update should fail if it is called twice" {
        val createdScannerRun = scannerRunRepository.create(scannerJobId)

        scannerRunRepository.update(
            createdScannerRun.id,
            scannerRun.startTime!!,
            scannerRun.endTime!!,
            scannerRun.environment!!,
            scannerRun.config!!
        )

        shouldThrow<IllegalArgumentException> {
            scannerRunRepository.update(
                createdScannerRun.id,
                scannerRun.startTime!!,
                scannerRun.endTime!!,
                scannerRun.environment!!,
                scannerRun.config!!
            )
        }
    }

    "get should return null" {
        scannerRunRepository.get(1L).shouldBeNull()
    }

    "get should return a scanner run with scan results" {
        val pkg1 = pkg.copy(
            identifier = pkg.identifier.copy(name = "package-1"),
            vcs = pkg.vcs.copy(url = "https://example.com/package-1.git"),
            vcsProcessed = pkg.vcsProcessed.copy(url = "https://example.com/package-1.git"),
            sourceArtifact = pkg.sourceArtifact.copy("https://example.com/package-1-sources.zip")
        )
        val pkg2 = pkg.copy(
            identifier = pkg.identifier.copy(name = "package-2"),
            vcs = pkg.vcs.copy(url = "https://example.com/package-2.git"),
            vcsProcessed = pkg.vcsProcessed.copy(url = "https://example.com/package-2.git"),
            sourceArtifact = pkg.sourceArtifact.copy("https://example.com/package-2-sources.zip")
        )
        val pkg3 = pkg.copy(
            identifier = pkg.identifier.copy(name = "package-3"),
            vcs = pkg.vcs.copy(url = "https://example.com/package-3.git"),
            vcsProcessed = pkg.vcsProcessed.copy(url = "https://example.com/package-3.git"),
            sourceArtifact = pkg.sourceArtifact.copy("https://example.com/package-3-sources.zip")
        )
        val pkg4 = pkg.copy(
            identifier = pkg.identifier.copy(name = "package-4"),
            vcs = pkg.vcs.copy(url = "https://example.com/package-4.git"),
            vcsProcessed = pkg.vcsProcessed.copy(url = "https://example.com/package-4.git"),
            sourceArtifact = pkg.sourceArtifact.copy("https://example.com/package-4-sources.zip")
        )

        createPackageProvenance(id = pkg1.identifier, sourceArtifact = pkg1.sourceArtifact)
        createPackageProvenance(id = pkg2.identifier, vcsProcessed = pkg2.vcsProcessed)
        createNestedProvenance(pkg2.vcsProcessed, mapOf("sub-repo" to pkg3.vcsProcessed))

        createPackageProvenance(pkg4.identifier, vcsProcessed = pkg4.vcsProcessed)
        createNestedProvenance(pkg4.vcsProcessed, emptyMap())

        val scanResultPkg1 = createScanResult(artifact = pkg1.sourceArtifact)
        val scanResultPkg2 = createScanResult(vcs = pkg2.vcsProcessed)
        val scanResultPkg3 = createScanResult(vcs = pkg3.vcsProcessed)

        analyzerRunRepository.create(fixtures.analyzerJob.id, analyzerRun.copy(packages = setOf(pkg1, pkg2)))
        scannerRunRepository.create(scannerJobId, scannerRun)

        val newOrtRun = fixtures.createOrtRun()
        val newAnalyzerJobId = fixtures.createAnalyzerJob(newOrtRun.id).id
        val newScannerJobId = fixtures.createScannerJob(newOrtRun.id).id
        analyzerRunRepository.create(newAnalyzerJobId, analyzerRun.copy(packages = setOf(pkg4)))
        scannerRunRepository.create(newScannerJobId, scannerRun)

        val scannerRun = scannerRunRepository.get(scannerJobId)

        scannerRun.shouldNotBeNull()

        scannerRun.provenances should containExactlyInAnyOrder(
            ProvenanceResolutionResult(
                id = pkg1.identifier,
                packageProvenance = ArtifactProvenance(sourceArtifact = pkg1.sourceArtifact)
            ),
            ProvenanceResolutionResult(
                id = pkg2.identifier,
                packageProvenance = RepositoryProvenance(vcsInfo = pkg2.vcsProcessed, resolvedRevision = "main"),
                subRepositories = mapOf("sub-repo" to pkg3.vcsProcessed)
            )
        )

        scannerRun.scanResults should containExactlyInAnyOrder(
            scanResultPkg1.copy(provenance = ArtifactProvenance(pkg1.sourceArtifact)),
            scanResultPkg2.copy(provenance = RepositoryProvenance(pkg2.vcsProcessed, pkg2.vcsProcessed.revision)),
            scanResultPkg3.copy(provenance = RepositoryProvenance(pkg3.vcsProcessed, pkg3.vcsProcessed.revision))
        )
    }
})

internal fun DaoScannerRunRepository.create(scannerJobId: Long, scannerRun: ScannerRun): ScannerRun {
    val createdScannerRun = create(scannerJobId = scannerJobId)
    return update(
        id = createdScannerRun.id,
        startTime = scannerRun.startTime!!,
        endTime = scannerRun.endTime!!,
        environment = scannerRun.environment!!,
        config = scannerRun.config!!
    )
}

internal fun createPackageProvenance(
    id: Identifier,
    vcsProcessed: VcsInfo? = null,
    sourceArtifact: RemoteArtifact? = null
): PackageProvenanceDao = transaction {
    PackageProvenanceDao.new {
        this.identifier = IdentifierDao.getOrPut(id)
        this.vcs = vcsProcessed?.let { VcsInfoDao.getOrPut(it) }
        this.resolvedRevision = this.vcs?.revision
        this.artifact = sourceArtifact?.let { RemoteArtifactDao.getOrPut(it) }
    }
}

private fun createNestedProvenance(
    root: VcsInfo,
    subRepositories: Map<String, VcsInfo>
): List<NestedProvenanceSubRepositoryDao> = transaction {
    val nestedProvenance = NestedProvenanceDao.new {
        this.rootVcs = VcsInfoDao.getOrPut(root)
        this.rootResolvedRevision = root.revision
        this.hasOnlyFixedRevisions = true
    }

    subRepositories.map { (path, vcs) ->
        NestedProvenanceSubRepositoryDao.new {
            this.nestedProvenance = nestedProvenance
            this.vcs = VcsInfoDao.getOrPut(vcs)
            this.resolvedRevision = vcs.revision
            this.path = path
        }
    }
}

private fun createScanResult(vcs: VcsInfo? = null, artifact: RemoteArtifact? = null): ScanResult = transaction {
    val scanSummary = ScanSummaryDao.new {
        this.startTime = Clock.System.now()
        this.endTime = Clock.System.now()
    }

    ScanResultDao.new {
        this.scanSummary = scanSummary
        this.scannerName = "scanner-name"
        this.scannerVersion = "scanner-version"
        this.scannerConfiguration = "scanner-configuration"
        this.artifactUrl = artifact?.url
        this.artifactHash = artifact?.hashValue
        this.artifactHashAlgorithm = artifact?.hashAlgorithm
        this.vcsType = vcs?.type?.name
        this.vcsUrl = vcs?.url
        this.vcsRevision = vcs?.revision
    }.mapToModel()
}

internal val fileStorageConfiguration = FileStorageConfiguration(
    localFileStorage = LocalFileStorageConfiguration(
        directory = "/path/to/storage",
        compression = true
    )
)

internal val scannerConfiguration = ScannerConfiguration(
    skipConcluded = false,
    archive = FileArchiveConfiguration(
        enabled = true,
        fileStorage = fileStorageConfiguration
    ),
    createMissingArchives = true,
    detectedLicenseMappings = mapOf(
        "license-1" to "spdx-license-1",
        "license-2" to "spdx-license-2"
    ),
    config = mapOf(
        "scanner-1" to PluginConfiguration(
            options = mapOf("option-key-1" to "option-value-1"),
            secrets = mapOf("secret-key-1" to "secret-value-1")
        ),
        "scanner-2" to PluginConfiguration(
            options = mapOf("option-key-1" to "option-value-1", "option-key-2" to "option-value-2"),
            secrets = mapOf("secret-key-1" to "secret-value-1", "secret-key-2" to "secret-value-2")
        )
    ),
    storages = mapOf(
        "local" to FileBasedStorageConfiguration(
            backend = fileStorageConfiguration,
            type = "PROVENANCE_BASED"
        ),
        "clearlyDefined" to ClearlyDefinedStorageConfiguration(
            serverUrl = "https://api.clearlydefined.io"
        )
    ),
    storageReaders = listOf("reader-1", "reader-2"),
    storageWriters = listOf("writer-1", "writer-2"),
    ignorePatterns = listOf("pattern-1", "pattern-2"),
    provenanceStorage = ProvenanceStorageConfiguration(
        postgresStorageConfiguration = PostgresStorageConfiguration(
            connection = PostgresConnection(
                url = "jdbc:postgresql://postgresql-server:5432/database",
                schema = "public",
                username = "username",
                sslMode = "required",
                sslCert = "/defaultdir/postgresql.crt",
                sslKey = "/defaultdir/postgresql.pk8",
                sslRootCert = "/defaultdir/root.crt",
                parallelTransactions = 5
            ),
            type = "PROVENANCE_BASED"
        )
    )
)

internal val scannerRun = ScannerRun(
    id = -1L,
    scannerJobId = -1L,
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    environment = environment,
    config = scannerConfiguration,
    provenances = emptySet(),
    scanResults = emptySet()
)
