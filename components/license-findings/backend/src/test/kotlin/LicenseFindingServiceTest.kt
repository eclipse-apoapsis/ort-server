/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.licensefindings

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.LicenseFindingDao
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummaryDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField

import org.jetbrains.exposed.v1.jdbc.Database

class LicenseFindingServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures
    private lateinit var service: LicenseFindingService
    private lateinit var seed: SeedResult

    init {
        beforeEach { testCase ->
            db = dbExtension.db
            fixtures = dbExtension.fixtures
            service = LicenseFindingService(db)
            seed = seedData(
                fixtures,
                db,
                duplicatePackageEntries = testCase.name.name ==
                    "count each package only once even with duplicate package rows"
            )
        }

        "getDetectedLicensesForRun" should {
            "return all licenses with package counts sorted by license ascending" {
                val result = service.getDetectedLicensesForRun(
                    seed.ortRunId,
                    ListQueryParameters(sortFields = listOf(OrderField("license", OrderDirection.ASCENDING))),
                    null
                )

                result.totalCount shouldBe 3
                result.data shouldContainExactly listOf(
                    DetectedLicense("Apache-2.0", 2),
                    DetectedLicense("BSD-3-Clause", 1),
                    DetectedLicense("MIT", 1)
                )
            }

            "apply licenseFilter ILIKE" {
                val result = service.getDetectedLicensesForRun(
                    seed.ortRunId,
                    ListQueryParameters(sortFields = listOf(OrderField("license", OrderDirection.ASCENDING))),
                    "apache"
                )

                result.totalCount shouldBe 1
                result.data shouldContainExactly listOf(DetectedLicense("Apache-2.0", 2))
            }

            "sort by packageCount descending without throwing" {
                val result = service.getDetectedLicensesForRun(
                    seed.ortRunId,
                    ListQueryParameters(sortFields = listOf(OrderField("packageCount", OrderDirection.DESCENDING))),
                    null
                )

                result.data.first() shouldBe DetectedLicense("Apache-2.0", 2)
            }

            "exclude findings from other ORT runs" {
                val result = service.getDetectedLicensesForRun(
                    seed.ortRunId,
                    ListQueryParameters(sortFields = listOf(OrderField("license", OrderDirection.ASCENDING))),
                    null
                )

                result.data.map { it.license } shouldContainExactly listOf("Apache-2.0", "BSD-3-Clause", "MIT")
            }

            "count each package only once even with duplicate package rows" {
                val result = service.getDetectedLicensesForRun(
                    seed.ortRunId,
                    ListQueryParameters(sortFields = listOf(OrderField("license", OrderDirection.ASCENDING))),
                    null
                )

                result.data shouldContainExactly listOf(
                    DetectedLicense("Apache-2.0", 2),
                    DetectedLicense("BSD-3-Clause", 1),
                    DetectedLicense("MIT", 1)
                )
            }

            "respect limit and offset" {
                val result = service.getDetectedLicensesForRun(
                    seed.ortRunId,
                    ListQueryParameters(
                        sortFields = listOf(OrderField("license", OrderDirection.ASCENDING)),
                        limit = 1,
                        offset = 1
                    ),
                    null
                )

                result.totalCount shouldBe 3
                result.data shouldContainExactly listOf(
                    DetectedLicense("BSD-3-Clause", 1)
                )
            }
        }

        "getPackagesWithDetectedLicenseForRun" should {
            "return the package that has the given license" {
                val result = service.getPackagesWithDetectedLicenseForRun(
                    seed.ortRunId,
                    "MIT",
                    ListQueryParameters(sortFields = listOf(OrderField("identifier", OrderDirection.ASCENDING))),
                    null,
                    null
                )

                result.totalCount shouldBe 1
                result.data shouldContainExactly listOf(
                    PackageIdentifier(seed.vcsIdentifier.mapToApi(), seed.vcsPurl)
                )
            }

            "return empty list for a license not found in this run" {
                val result = service.getPackagesWithDetectedLicenseForRun(
                    seed.ortRunId,
                    "Zlib",
                    ListQueryParameters(sortFields = listOf(OrderField("identifier", OrderDirection.ASCENDING))),
                    null,
                    null
                )

                result.totalCount shouldBe 0
                result.data.shouldBeEmpty()
            }

            "apply identifierFilter ILIKE" {
                val result = service.getPackagesWithDetectedLicenseForRun(
                    seed.ortRunId,
                    "Apache-2.0",
                    ListQueryParameters(sortFields = listOf(OrderField("identifier", OrderDirection.ASCENDING))),
                    "ARTIFACT-package",
                    null
                )

                result.totalCount shouldBe 1
                result.data shouldContainExactly listOf(
                    PackageIdentifier(seed.artifactIdentifier.mapToApi(), seed.artifactPurl)
                )
            }

            "return empty list when identifierFilter matches nothing" {
                val result = service.getPackagesWithDetectedLicenseForRun(
                    seed.ortRunId,
                    "Apache-2.0",
                    ListQueryParameters(sortFields = listOf(OrderField("identifier", OrderDirection.ASCENDING))),
                    "does-not-exist",
                    null
                )

                result.totalCount shouldBe 0
                result.data.shouldBeEmpty()
            }

            "respect limit and offset" {
                val result = service.getPackagesWithDetectedLicenseForRun(
                    seed.ortRunId,
                    "Apache-2.0",
                    ListQueryParameters(
                        sortFields = listOf(OrderField("identifier", OrderDirection.ASCENDING)),
                        limit = 1,
                        offset = 1
                    ),
                    null,
                    null
                )

                result.totalCount shouldBe 2
                result.data shouldContainExactly listOf(
                    PackageIdentifier(seed.vcsIdentifier.mapToApi(), seed.vcsPurl)
                )
            }

            "apply purlFilter ILIKE" {
                val result = service.getPackagesWithDetectedLicenseForRun(
                    seed.ortRunId,
                    "Apache-2.0",
                    ListQueryParameters(sortFields = listOf(OrderField("identifier", OrderDirection.ASCENDING))),
                    null,
                    "artifact-package@1.0"
                )

                result.totalCount shouldBe 1
                result.data shouldContainExactly listOf(
                    PackageIdentifier(seed.artifactIdentifier.mapToApi(), seed.artifactPurl)
                )
            }

            "sort by purl" {
                val result = service.getPackagesWithDetectedLicenseForRun(
                    seed.ortRunId,
                    "Apache-2.0",
                    ListQueryParameters(sortFields = listOf(OrderField("purl", OrderDirection.DESCENDING))),
                    null,
                    null
                )

                result.totalCount shouldBe 2
                result.data shouldContainExactly listOf(
                    PackageIdentifier(seed.vcsIdentifier.mapToApi(), seed.vcsPurl),
                    PackageIdentifier(seed.artifactIdentifier.mapToApi(), seed.artifactPurl)
                )
            }
        }

        "getLicenseFindingsForRun" should {
            "return findings for the given license and package sorted by path" {
                val result = service.getLicenseFindingsForRun(
                    seed.ortRunId,
                    "Apache-2.0",
                    seed.artifactIdentifier.toCoordinates(),
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 2
                result.data shouldContainExactly listOf(
                    LicenseFinding("docs/NOTICE.Apache", 11, 18, 87.5f, "LicenseScanner 1.0.0"),
                    LicenseFinding("LICENSE", 1, 10, 99f, "LicenseScanner 1.0.0")
                )
            }

            "return findings with null score when score is absent" {
                val result = service.getLicenseFindingsForRun(
                    seed.ortRunId,
                    "BSD-3-Clause",
                    seed.artifactIdentifier.toCoordinates(),
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 1
                result.data shouldContainExactly listOf(
                    LicenseFinding("NOTICE", 2, 3, null, "LicenseScanner 1.0.0")
                )
            }

            "respect limit and offset" {
                val result = service.getLicenseFindingsForRun(
                    seed.ortRunId,
                    "Apache-2.0",
                    seed.artifactIdentifier.toCoordinates(),
                    ListQueryParameters(
                        sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)),
                        limit = 1,
                        offset = 1
                    )
                )

                result.totalCount shouldBe 2
                result.data shouldContainExactly listOf(
                    LicenseFinding("LICENSE", 1, 10, 99f, "LicenseScanner 1.0.0")
                )
            }

            "return empty list for an unknown license" {
                service.getLicenseFindingsForRun(
                    seed.ortRunId,
                    "NOASSERTION",
                    seed.artifactIdentifier.toCoordinates(),
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                ).data.shouldBeEmpty()
            }

            "return empty list for an unknown identifier" {
                service.getLicenseFindingsForRun(
                    seed.ortRunId,
                    "Apache-2.0",
                    "Maven:com.example:missing:1.0",
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                ).data.shouldBeEmpty()
            }

            "not return findings from other ORT runs" {
                service.getLicenseFindingsForRun(
                    seed.ortRunId,
                    "Zlib",
                    seed.otherIdentifier.toCoordinates(),
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                ).data.shouldBeEmpty()
            }
        }
    }
}

internal data class SeedResult(
    val ortRunId: Long,
    val otherOrtRunId: Long,
    val artifactIdentifier: Identifier,
    val vcsIdentifier: Identifier,
    val otherIdentifier: Identifier,
    val artifactPurl: String,
    val vcsPurl: String,
    val otherPurl: String
)

internal fun seedData(fixtures: Fixtures, db: Database): SeedResult =
    seedData(fixtures, db, duplicatePackageEntries = false)

internal fun seedData(fixtures: Fixtures, db: Database, duplicatePackageEntries: Boolean): SeedResult {
    val artifactIdentifier = Identifier("Maven", "com.example", "artifact-package", "1.0")
    val vcsIdentifier = Identifier("Maven", "com.example", "vcs-package", "2.0")
    val otherIdentifier = Identifier("NPM", "", "other-package", "3.0")

    val artifactPurl = "pkg:maven/com.example/artifact-package@1.0"
    val artifactDuplicatePurl = "pkg:maven/com.example/artifact-package@1.0?classifier=duplicate"
    val vcsPurl = "pkg:maven/com.example/vcs-package@2.0"
    val otherPurl = "pkg:npm/other-package@3.0"

    val artifactPackage = fixtures.generatePackage(artifactIdentifier).copy(purl = artifactPurl)
    val duplicateArtifactPackage = fixtures.generatePackage(artifactIdentifier).copy(purl = artifactDuplicatePurl)
    val vcsPackage = fixtures.generatePackage(vcsIdentifier).copy(purl = vcsPurl)
    val otherPackage = fixtures.generatePackage(otherIdentifier).copy(purl = otherPurl)

    val ortRun = fixtures.createOrtRun()
    val analyzerJob = fixtures.createAnalyzerJob(ortRun.id)
    fixtures.createAnalyzerRun(
        analyzerJobId = analyzerJob.id,
        packages = buildSet {
            add(artifactPackage)
            add(vcsPackage)

            if (duplicatePackageEntries) {
                add(duplicateArtifactPackage)
            }
        }
    )

    val otherOrtRun = fixtures.createOrtRun()
    val otherAnalyzerJob = fixtures.createAnalyzerJob(otherOrtRun.id)
    fixtures.createAnalyzerRun(analyzerJobId = otherAnalyzerJob.id, packages = setOf(otherPackage))

    val artifactProvenance = RemoteArtifact(
        url = "https://example.com/files/artifact-package-1.0.tar.gz",
        hashValue = "1111111111111111111111111111111111111111",
        hashAlgorithm = "SHA-1"
    )
    val otherArtifactProvenance = RemoteArtifact(
        url = "https://example.com/files/other-package-3.0.tgz",
        hashValue = "2222222222222222222222222222222222222222",
        hashAlgorithm = "SHA-1"
    )
    val vcsProvenance = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/scm/vcs-package.git",
        revision = "deadbeef",
        path = ""
    )

    db.blockingQuery {
        val scannerJob = fixtures.createScannerJob(ortRun.id)
        val scannerRun = fixtures.scannerRunRepository.create(scannerJob.id)

        val otherScannerJob = fixtures.createScannerJob(otherOrtRun.id)
        val otherScannerRun = fixtures.scannerRunRepository.create(otherScannerJob.id)

        createPackageProvenance(scannerRun.id, artifactIdentifier, artifact = artifactProvenance)
        createPackageProvenance(scannerRun.id, vcsIdentifier, vcs = vcsProvenance)
        createPackageProvenance(otherScannerRun.id, otherIdentifier, artifact = otherArtifactProvenance)

        createArtifactScanResult(
            scannerRun.id,
            artifactProvenance,
            listOf(
                FindingSeed("Apache-2.0", "LICENSE", 1, 10, 99f),
                FindingSeed("Apache-2.0", "docs/NOTICE.Apache", 11, 18, 87.5f),
                FindingSeed("BSD-3-Clause", "NOTICE", 2, 3, null)
            )
        )
        createVcsScanResult(
            scannerRun.id,
            vcsProvenance,
            listOf(
                FindingSeed("Apache-2.0", "THIRD-PARTY.txt", 4, 8, 90f),
                FindingSeed("MIT", "src/main/resources/license.txt", 5, 7, 91.5f)
            )
        )
        createArtifactScanResult(
            otherScannerRun.id,
            otherArtifactProvenance,
            listOf(FindingSeed("Zlib", "COPYING", 1, 20, 100f))
        )
    }

    return SeedResult(
        ortRunId = ortRun.id,
        otherOrtRunId = otherOrtRun.id,
        artifactIdentifier = artifactIdentifier,
        vcsIdentifier = vcsIdentifier,
        otherIdentifier = otherIdentifier,
        artifactPurl = artifactPurl,
        vcsPurl = vcsPurl,
        otherPurl = otherPurl
    )
}

private data class FindingSeed(
    val license: String,
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val score: Float?
)

private fun createPackageProvenance(
    scannerRunId: Long,
    identifier: Identifier,
    artifact: RemoteArtifact? = null,
    vcs: VcsInfo? = null
) {
    val provenance = PackageProvenanceDao.new {
        this.identifier = IdentifierDao.getOrPut(identifier)
        this.artifact = artifact?.let(RemoteArtifactDao::getOrPut)
        this.vcs = vcs?.let(VcsInfoDao::getOrPut)
        this.resolvedRevision = vcs?.revision
        this.clonedRevision = null
        this.isFixedRevision = null
        this.errorMessage = null
    }

    ScannerRunsPackageProvenancesTable.insertIfNotExists(scannerRunId, provenance.id.value)
}

private fun createArtifactScanResult(
    scannerRunId: Long,
    artifact: RemoteArtifact,
    findings: List<FindingSeed>
) {
    val scanSummary = createScanSummary(findings)

    val scanResult = ScanResultDao.new {
        artifactUrl = artifact.url
        artifactHash = artifact.hashValue
        artifactHashAlgorithm = artifact.hashAlgorithm
        vcsType = null
        vcsUrl = null
        vcsRevision = null
        this.scanSummary = scanSummary
        scannerName = "LicenseScanner"
        scannerVersion = "1.0.0"
        scannerConfiguration = "default"
        additionalScanResultData = null
    }

    ScannerRunsScanResultsTable.insertIfNotExists(scannerRunId, scanResult.id.value)
}

private fun createVcsScanResult(
    scannerRunId: Long,
    vcs: VcsInfo,
    findings: List<FindingSeed>
) {
    val scanSummary = createScanSummary(findings)

    val scanResult = ScanResultDao.new {
        artifactUrl = null
        artifactHash = null
        artifactHashAlgorithm = null
        vcsType = vcs.type.name
        vcsUrl = vcs.url
        vcsRevision = vcs.revision
        this.scanSummary = scanSummary
        scannerName = "LicenseScanner"
        scannerVersion = "1.0.0"
        scannerConfiguration = "default"
        additionalScanResultData = null
    }

    ScannerRunsScanResultsTable.insertIfNotExists(scannerRunId, scanResult.id.value)
}

private fun createScanSummary(findings: List<FindingSeed>): ScanSummaryDao {
    val scanSummary = ScanSummaryDao.new {
        startTime = Clock.System.now()
        endTime = Clock.System.now()
        hash = "summary-hash-${Clock.System.now().toEpochMilliseconds()}-${findings.hashCode()}"
    }

    findings.forEach { finding ->
        LicenseFindingDao.new {
            license = finding.license
            path = finding.path
            startLine = finding.startLine
            endLine = finding.endLine
            score = finding.score
            this.scanSummary = scanSummary
        }
    }

    return scanSummary
}
