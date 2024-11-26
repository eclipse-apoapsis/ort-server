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

package org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.DaoAnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummaryDao
import org.eclipse.apoapsis.ortserver.dao.tables.provenance.NestedProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.provenance.NestedProvenanceSubRepositoryDao
import org.eclipse.apoapsis.ortserver.dao.tables.provenance.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.scanner.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.scanner.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ProvenanceResolutionResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.RepositoryProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun

import org.jetbrains.exposed.sql.transactions.transaction

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
        val scanners = mapOf(pkg.identifier to setOf(SCANNER_NAME))

        val createdScannerRun = scannerRunRepository.create(scannerJobId)

        scannerRunRepository.update(
            createdScannerRun.id,
            scannerRun.startTime!!,
            scannerRun.endTime!!,
            scannerRun.environment!!,
            scannerRun.config!!,
            scanners
        )

        val dbEntry = scannerRunRepository.get(createdScannerRun.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe scannerRun.copy(id = createdScannerRun.id, scannerJobId = scannerJobId, scanners = scanners)
    }

    "update should fail if it is called twice" {
        val createdScannerRun = scannerRunRepository.create(scannerJobId)

        scannerRunRepository.update(
            createdScannerRun.id,
            scannerRun.startTime!!,
            scannerRun.endTime!!,
            scannerRun.environment!!,
            scannerRun.config!!,
            scannerRun.scanners
        )

        shouldThrow<IllegalArgumentException> {
            scannerRunRepository.update(
                createdScannerRun.id,
                scannerRun.startTime!!,
                scannerRun.endTime!!,
                scannerRun.environment!!,
                scannerRun.config!!,
                scannerRun.scanners
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

        val packageProvenance1 = createPackageProvenance(id = pkg1.identifier, sourceArtifact = pkg1.sourceArtifact)
        val packageProvenance2 = createPackageProvenance(id = pkg2.identifier, vcsProcessed = pkg2.vcsProcessed)
        val nestedProvenance2 = createNestedProvenance(pkg2.vcsProcessed, mapOf("sub-repo" to pkg3.vcsProcessed))
        associatePackageProvenanceWithNestedProvenance(packageProvenance2, nestedProvenance2)

        val packageProvenance4 = createPackageProvenance(pkg4.identifier, vcsProcessed = pkg4.vcsProcessed)
        val nestedProvenance4 = createNestedProvenance(pkg4.vcsProcessed, emptyMap())
        associatePackageProvenanceWithNestedProvenance(packageProvenance4, nestedProvenance4)

        val otherScanner = "SomeOtherScanner"
        val scanResultPkg1 = createScanResult(artifact = pkg1.sourceArtifact)
        val scanResultPkg2 = createScanResult(vcs = pkg2.vcsProcessed)
        val scanResultPkg3 = createScanResult(vcs = pkg3.vcsProcessed, scanner = otherScanner)
        val scanners = mapOf(
            pkg1.identifier to setOf(SCANNER_NAME),
            pkg2.identifier to setOf(SCANNER_NAME, otherScanner),
            pkg3.identifier to setOf(otherScanner)
        )

        analyzerRunRepository.create(fixtures.analyzerJob.id, analyzerRun.copy(packages = setOf(pkg1, pkg2)))
        val createdScannerRun = scannerRunRepository.create(scannerJobId, scannerRun.copy(scanners = scanners))
        associateScannerRunWithScanResult(createdScannerRun, scanResultPkg1)
        associateScannerRunWithScanResult(createdScannerRun, scanResultPkg2)
        associateScannerRunWithScanResult(createdScannerRun, scanResultPkg3)
        associateScannerRunWithPackageProvenance(createdScannerRun, packageProvenance1)
        associateScannerRunWithPackageProvenance(createdScannerRun, packageProvenance2)

        val newOrtRun = fixtures.createOrtRun()
        val newAnalyzerJobId = fixtures.createAnalyzerJob(newOrtRun.id).id
        val newScannerJobId = fixtures.createScannerJob(newOrtRun.id).id
        analyzerRunRepository.create(newAnalyzerJobId, analyzerRun.copy(packages = setOf(pkg4)))
        val newCreatedScannerRun = scannerRunRepository.create(newScannerJobId, scannerRun)
        associateScannerRunWithPackageProvenance(newCreatedScannerRun, packageProvenance4)

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

        scannerRun.scanners shouldBe scanners

        transaction {
            scannerRun.scanResults should containExactlyInAnyOrder(
                scanResultPkg1.mapToModel().copy(provenance = ArtifactProvenance(pkg1.sourceArtifact)),
                scanResultPkg2.mapToModel()
                    .copy(provenance = RepositoryProvenance(pkg2.vcsProcessed, pkg2.vcsProcessed.revision)),
                scanResultPkg3.mapToModel()
                    .copy(provenance = RepositoryProvenance(pkg3.vcsProcessed, pkg3.vcsProcessed.revision))
            )
        }
    }
})

private const val SCANNER_NAME = "TestScanner"

private fun DaoScannerRunRepository.create(scannerJobId: Long, scannerRun: ScannerRun): ScannerRun {
    val createdScannerRun = create(scannerJobId = scannerJobId)
    return update(
        id = createdScannerRun.id,
        startTime = scannerRun.startTime!!,
        endTime = scannerRun.endTime!!,
        environment = scannerRun.environment!!,
        config = scannerRun.config!!,
        scanners = scannerRun.scanners
    )
}

private fun createPackageProvenance(
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
): NestedProvenanceDao = transaction {
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

    nestedProvenance
}

private fun createScanResult(
    vcs: VcsInfo? = null,
    artifact: RemoteArtifact? = null,
    scanner: String = SCANNER_NAME
): ScanResultDao = transaction {
    val scanSummary = ScanSummaryDao.new {
        this.startTime = Clock.System.now()
        this.endTime = Clock.System.now()
    }

    ScanResultDao.new {
        this.scanSummary = scanSummary
        this.scannerName = scanner
        this.scannerVersion = "scanner-version"
        this.scannerConfiguration = "scanner-configuration"
        this.artifactUrl = artifact?.url
        this.artifactHash = artifact?.hashValue
        this.artifactHashAlgorithm = artifact?.hashAlgorithm
        this.vcsType = vcs?.type?.name
        this.vcsUrl = vcs?.url
        this.vcsRevision = vcs?.revision
    }
}

private val scannerConfiguration = ScannerConfiguration(
    skipConcluded = false,
    skipExcluded = false,
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
    ignorePatterns = listOf("pattern-1", "pattern-2")
)

private fun associateScannerRunWithPackageProvenance(scannerRun: ScannerRun, packageProvenance: PackageProvenanceDao) {
    transaction {
        ScannerRunsPackageProvenancesTable.insertIfNotExists(scannerRun.id, packageProvenance.id.value)
    }
}

private fun associatePackageProvenanceWithNestedProvenance(
    packageProvenance: PackageProvenanceDao,
    nestedProvenance: NestedProvenanceDao
) {
    transaction {
        packageProvenance.nestedProvenance = nestedProvenance
    }
}

private fun associateScannerRunWithScanResult(scannerRun: ScannerRun, scanResult: ScanResultDao) {
    transaction {
        ScannerRunsScanResultsTable.insertIfNotExists(scannerRun.id, scanResult.id.value)
    }
}

private val variables = mapOf(
    "SHELL" to "/bin/bash",
    "TERM" to "xterm-256color"
)

private val toolVersions = mapOf(
    "Conan" to "1.53.0",
    "NPM" to "8.15.1"
)

private val environment = Environment(
    ortVersion = "1.0",
    javaVersion = "11.0.16",
    os = "Linux",
    processors = 8,
    maxMemory = 8321499136,
    variables = variables,
    toolVersions = toolVersions
)

private val scannerRun = ScannerRun(
    id = -1L,
    scannerJobId = -1L,
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    environment = environment,
    config = scannerConfiguration,
    provenances = emptySet(),
    scanResults = emptySet(),
    scanners = emptyMap()
)

private val project = Project(
    identifier = Identifier(
        type = "type",
        namespace = "namespace",
        name = "project",
        version = "version"
    ),
    cpe = "cpe",
    definitionFilePath = "definitionFilePath",
    authors = emptySet(),
    declaredLicenses = emptySet(),
    processedDeclaredLicense = ProcessedDeclaredLicense(
        spdxExpression = null,
        mappedLicenses = emptyMap(),
        unmappedLicenses = emptySet()
    ),
    vcs = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/project.git",
        revision = "",
        path = ""
    ),
    vcsProcessed = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/project.git",
        revision = "main",
        path = ""
    ),
    homepageUrl = "https://example.com",
    scopeNames = emptySet()
)

private val pkg = Package(
    identifier = Identifier(
        type = "type",
        namespace = "namespace",
        name = "package",
        version = "version"
    ),
    purl = "purl",
    cpe = "cpe",
    authors = emptySet(),
    declaredLicenses = emptySet(),
    processedDeclaredLicense = ProcessedDeclaredLicense(
        spdxExpression = null,
        mappedLicenses = emptyMap(),
        unmappedLicenses = emptySet()
    ),
    description = "description",
    homepageUrl = "https://example.com",
    binaryArtifact = RemoteArtifact(
        url = "https://example.com/binary.zip",
        hashValue = "",
        hashAlgorithm = ""
    ),
    sourceArtifact = RemoteArtifact(
        url = "https://example.com/source.zip",
        hashValue = "0123456789abcdef0123456789abcdef01234567",
        hashAlgorithm = "SHA-1"
    ),
    vcs = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/package.git",
        revision = "",
        path = ""
    ),
    vcsProcessed = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/package.git",
        revision = "main",
        path = ""
    )
)

private val analyzerRun = AnalyzerRun(
    id = -1L,
    analyzerJobId = -1L,
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    environment = environment,
    config = AnalyzerConfiguration(
        allowDynamicVersions = true,
        enabledPackageManagers = null,
        disabledPackageManagers = null,
        packageManagers = null,
        skipExcluded = true
    ),
    projects = setOf(project),
    packages = setOf(pkg),
    issues = emptyList(),
    dependencyGraphs = emptyMap()
)

private fun DaoAnalyzerRunRepository.create(analyzerJobId: Long, analyzerRun: AnalyzerRun) = create(
    analyzerJobId = analyzerJobId,
    startTime = analyzerRun.startTime,
    endTime = analyzerRun.endTime,
    environment = analyzerRun.environment,
    config = analyzerRun.config,
    projects = analyzerRun.projects,
    packages = analyzerRun.packages,
    issues = analyzerRun.issues,
    dependencyGraphs = analyzerRun.dependencyGraphs
)
