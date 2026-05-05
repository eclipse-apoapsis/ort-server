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

package org.eclipse.apoapsis.ortserver.components.snippetfindings

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier as ApiIdentifier
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceSubRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceSubRepositoryDao
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummaryDao
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetDao
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingDao
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
import org.jetbrains.exposed.v1.jdbc.SizedCollection

class SnippetFindingServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures
    private lateinit var service: SnippetFindingService
    private lateinit var seed: SeedResult

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
            service = SnippetFindingService(db)
            seed = seedData(fixtures, db)
        }

        "getProvenancesForRun" should {
            "return empty list for an unknown ORT run" {
                val result = service.getProvenancesForRun(
                    seed.otherOrtRunId + 999L,
                    ListQueryParameters(sortFields = listOf(OrderField("name", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 0
                result.data.shouldBeEmpty()
            }

            "return the scan result provenance for the run" {
                val result = service.getProvenancesForRun(
                    seed.ortRunId,
                    ListQueryParameters(sortFields = listOf(OrderField("name", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 1
                result.data shouldContainExactly listOf(
                    SnippetFindingProvenance(
                        id = seed.provenanceId,
                        identifier = ApiIdentifier("Maven", "com.example", "artifact-package", "1.0"),
                        provenanceType = "REPOSITORY",
                        snippetFindingCount = 2,
                        vcsType = "GIT",
                        vcsUrl = "https://example.com/scm/artifact-package.git",
                        vcsRevision = "abcdef1234567890"
                    )
                )
            }

            "return both direct and nested sub-repository provenances" {
                var subRepoScanResultId = -1L
                db.blockingQuery {
                    subRepoScanResultId = addNestedSubRepoScanResult(seed)
                }

                val result = service.getProvenancesForRun(
                    seed.ortRunId,
                    ListQueryParameters(sortFields = listOf(OrderField("name", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 2
                result.data.map { it.id } shouldContainExactlyInAnyOrder listOf(
                    seed.provenanceId,
                    subRepoScanResultId
                )
                // Both provenances belong to the same package identifier.
                result.data.map { it.identifier }.toSet() shouldBe setOf(
                    ApiIdentifier("Maven", "com.example", "artifact-package", "1.0")
                )
                // The nested provenance has the sub-repository VCS details.
                result.data.first { it.id == subRepoScanResultId }.let { nested ->
                    nested.provenanceType shouldBe "REPOSITORY"
                    nested.vcsUrl shouldBe "https://example.com/scm/artifact-package-sub.git"
                    nested.vcsRevision shouldBe "fedcba9876543210"
                }
            }

            "not return provenances from other runs" {
                val result = service.getProvenancesForRun(
                    seed.ortRunId,
                    ListQueryParameters(sortFields = listOf(OrderField("name", OrderDirection.ASCENDING)))
                )

                result.data.none { it.id == seed.otherProvenanceId } shouldBe true
            }
        }

        "getSnippetFindingsForRun" should {
            "return empty list for an unknown ORT run" {
                val result = service.getSnippetFindingsForRun(
                    seed.otherOrtRunId + 999L,
                    seed.provenanceId,
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 0
                result.data.shouldBeEmpty()
            }

            "return empty list when provenance belongs to a different run" {
                val result = service.getSnippetFindingsForRun(
                    seed.ortRunId,
                    seed.otherProvenanceId,
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 0
                result.data.shouldBeEmpty()
            }

            "return findings for a nested sub-repository scan result" {
                var subRepoScanResultId = -1L
                db.blockingQuery {
                    subRepoScanResultId = addNestedSubRepoScanResult(seed)
                }

                // Direct provenance still returns its own findings.
                val directResult = service.getSnippetFindingsForRun(
                    seed.ortRunId,
                    seed.provenanceId,
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                )
                directResult.totalCount shouldBe 2

                // Nested provenance returns zero findings (none were seeded for it) but the query succeeds.
                val nestedResult = service.getSnippetFindingsForRun(
                    seed.ortRunId,
                    subRepoScanResultId,
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                )
                nestedResult.totalCount shouldBe 0
                nestedResult.data.shouldBeEmpty()
            }

            "return all findings for a run and provenance sorted by path with snippet counts" {
                val result = service.getSnippetFindingsForRun(
                    seed.ortRunId,
                    seed.provenanceId,
                    ListQueryParameters(sortFields = listOf(OrderField("path", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 2
                result.data shouldContainExactly listOf(
                    SnippetFinding(seed.firstFindingId, "src/main/App.kt", 12, 18, 2),
                    SnippetFinding(seed.secondFindingId, "src/test/AppTest.kt", 3, 7, 1)
                )
            }

            "respect sorting, limit, and offset" {
                val result = service.getSnippetFindingsForRun(
                    seed.ortRunId,
                    seed.provenanceId,
                    ListQueryParameters(
                        sortFields = listOf(OrderField("snippetCount", OrderDirection.DESCENDING)),
                        limit = 1,
                        offset = 1
                    )
                )

                result.totalCount shouldBe 2
                result.data shouldContainExactly listOf(
                    SnippetFinding(seed.secondFindingId, "src/test/AppTest.kt", 3, 7, 1)
                )
            }
        }

        "getSnippetsForSnippetFinding" should {
            "return empty list for an unknown snippet finding" {
                val result = service.getSnippetsForSnippetFinding(
                    seed.ortRunId,
                    seed.secondFindingId + seed.otherFindingId + 999L,
                    ListQueryParameters(sortFields = listOf(OrderField("purl", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 0
                result.data.shouldBeEmpty()
            }

            "return all upstream snippets with artifact and VCS provenance" {
                val result = service.getSnippetsForSnippetFinding(
                    seed.ortRunId,
                    seed.firstFindingId,
                    ListQueryParameters(sortFields = listOf(OrderField("purl", OrderDirection.ASCENDING)))
                )

                result.totalCount shouldBe 2
                result.data shouldContainExactly listOf(
                    SnippetSource(
                        purl = "pkg:maven/com.example/upstream-artifact@1.0",
                        path = "src/App.kt",
                        startLine = 1,
                        endLine = 5,
                        license = "Apache-2.0",
                        score = 97.5f,
                        artifactUrl = "https://example.com/sources/upstream-artifact-1.0.tar.gz"
                    ),
                    SnippetSource(
                        purl = "pkg:maven/com.example/upstream-vcs@2.0",
                        path = "lib/Utils.kt",
                        startLine = 10,
                        endLine = 14,
                        license = "MIT",
                        score = 88.5f,
                        vcsType = "GIT",
                        vcsUrl = "https://example.com/scm/upstream-vcs.git",
                        vcsRevision = "cafebabe",
                        vcsPath = "modules/core"
                    )
                )
            }

            "respect sorting, limit, and offset" {
                val result = service.getSnippetsForSnippetFinding(
                    seed.ortRunId,
                    seed.firstFindingId,
                    ListQueryParameters(
                        sortFields = listOf(OrderField("score", OrderDirection.ASCENDING)),
                        limit = 1,
                        offset = 1
                    )
                )

                result.totalCount shouldBe 2
                result.data shouldContainExactly listOf(
                    SnippetSource(
                        purl = "pkg:maven/com.example/upstream-artifact@1.0",
                        path = "src/App.kt",
                        startLine = 1,
                        endLine = 5,
                        license = "Apache-2.0",
                        score = 97.5f,
                        artifactUrl = "https://example.com/sources/upstream-artifact-1.0.tar.gz"
                    )
                )
            }
        }
    }
}

internal data class SeedResult(
    val ortRunId: Long,
    val otherOrtRunId: Long,
    /** ID of the ScanResult for ortRun — used as the provenance ID in API calls. */
    val provenanceId: Long,
    /** ID of the ScanResult for otherOrtRun — used to verify cross-run isolation. */
    val otherProvenanceId: Long,
    /** Scanner run ID for ortRun — needed to link additional scan results in tests. */
    val scannerRunId: Long,
    /** PackageProvenance ID for ortRun — needed to attach a nested provenance in tests. */
    val packageProvenanceId: Long,
    val firstFindingId: Long,
    val secondFindingId: Long,
    val otherFindingId: Long
)

internal fun seedData(fixtures: Fixtures, db: Database): SeedResult {
    val ortRun = fixtures.createOrtRun()
    val otherOrtRun = fixtures.createOrtRun()

    val artifactIdentifier = Identifier("Maven", "com.example", "artifact-package", "1.0")
    val otherIdentifier = Identifier("NPM", "", "other-package", "3.0")

    val artifactProvenance = RemoteArtifact(
        url = "https://example.com/packages/artifact-package-1.0.tar.gz",
        hashValue = "1111111111111111111111111111111111111111",
        hashAlgorithm = "SHA-1"
    )
    val otherArtifactProvenance = RemoteArtifact(
        url = "https://example.com/packages/other-package-3.0.tgz",
        hashValue = "2222222222222222222222222222222222222222",
        hashAlgorithm = "SHA-1"
    )

    val firstFindingSeed = SnippetFindingSeed("src/main/App.kt", 12, 18)
    val secondFindingSeed = SnippetFindingSeed("src/test/AppTest.kt", 3, 7)
    val otherFindingSeed = SnippetFindingSeed("vendor/lib.c", 40, 55)

    lateinit var firstFinding: SnippetFindingDao
    lateinit var secondFinding: SnippetFindingDao
    lateinit var otherFinding: SnippetFindingDao
    var scanResultId = -1L
    var otherScanResultId = -1L
    var mainScannerRunId = -1L
    var mainPackageProvenanceId = -1L

    db.blockingQuery {
        val scannerJob = fixtures.createScannerJob(ortRun.id)
        val scannerRun = fixtures.scannerRunRepository.create(scannerJob.id)
        mainScannerRunId = scannerRun.id

        val otherScannerJob = fixtures.createScannerJob(otherOrtRun.id)
        val otherScannerRun = fixtures.scannerRunRepository.create(otherScannerJob.id)

        val packageProvenance = createPackageProvenance(scannerRun.id, artifactIdentifier, artifactProvenance)
        mainPackageProvenanceId = packageProvenance.id.value
        val otherPackageProvenance = createPackageProvenance(
            otherScannerRun.id,
            otherIdentifier,
            otherArtifactProvenance
        )

        val findingSnippets = mapOf(
            firstFindingSeed to listOf(
                createArtifactSnippet(
                    purl = "pkg:maven/com.example/upstream-artifact@1.0",
                    path = "src/App.kt",
                    startLine = 1,
                    endLine = 5,
                    license = "Apache-2.0",
                    score = 97.5f,
                    artifactUrl = "https://example.com/sources/upstream-artifact-1.0.tar.gz"
                ),
                createVcsSnippet(
                    purl = "pkg:maven/com.example/upstream-vcs@2.0",
                    path = "lib/Utils.kt",
                    startLine = 10,
                    endLine = 14,
                    license = "MIT",
                    score = 88.5f,
                    vcsType = RepositoryType.GIT,
                    vcsUrl = "https://example.com/scm/upstream-vcs.git",
                    vcsRevision = "cafebabe",
                    vcsPath = "modules/core"
                )
            ),
            secondFindingSeed to listOf(
                createArtifactSnippet(
                    purl = "pkg:npm/example/secondary@4.0",
                    path = "test/AppTest.js",
                    startLine = 20,
                    endLine = 24,
                    license = "BSD-3-Clause",
                    score = 91f,
                    artifactUrl = "https://example.com/sources/secondary-4.0.tgz"
                )
            )
        )

        val otherFindingSnippets = mapOf(
            otherFindingSeed to listOf(
                createArtifactSnippet(
                    purl = "pkg:npm/example/other@3.0",
                    path = "vendor/lib.c",
                    startLine = 100,
                    endLine = 110,
                    license = "Zlib",
                    score = 99f,
                    artifactUrl = "https://example.com/sources/other-3.0.tgz"
                )
            )
        )

        val (mainScanResultId, findings) = createScanResultWithSnippetFindings(
            scannerRun.id,
            packageProvenance.id.value,
            findingSnippets,
            // Use a VCS provenance for the scan result to exercise the REPOSITORY branch.
            vcsType = "GIT",
            vcsUrl = "https://example.com/scm/artifact-package.git",
            vcsRevision = "abcdef1234567890"
        )
        scanResultId = mainScanResultId
        firstFinding = checkNotNull(findings[firstFindingSeed])
        secondFinding = checkNotNull(findings[secondFindingSeed])

        val (otherMainScanResultId, otherFindings) = createScanResultWithSnippetFindings(
            otherScannerRun.id,
            otherPackageProvenance.id.value,
            otherFindingSnippets
        )
        otherScanResultId = otherMainScanResultId
        otherFinding = checkNotNull(otherFindings[otherFindingSeed])
    }

    return SeedResult(
        ortRunId = ortRun.id,
        otherOrtRunId = otherOrtRun.id,
        provenanceId = scanResultId,
        otherProvenanceId = otherScanResultId,
        scannerRunId = mainScannerRunId,
        packageProvenanceId = mainPackageProvenanceId,
        firstFindingId = firstFinding.id.value,
        secondFindingId = secondFinding.id.value,
        otherFindingId = otherFinding.id.value
    )
}

internal data class SnippetFindingSeed(
    val path: String,
    val startLine: Int,
    val endLine: Int
)

internal fun createPackageProvenance(
    scannerRunId: Long,
    identifier: Identifier,
    artifact: RemoteArtifact
): PackageProvenanceDao {
    val provenance = PackageProvenanceDao.new {
        this.identifier = IdentifierDao.getOrPut(identifier)
        this.artifact = RemoteArtifactDao.getOrPut(artifact)
        this.vcs = null
        this.resolvedRevision = null
        this.clonedRevision = null
        this.isFixedRevision = null
        this.errorMessage = null
    }

    ScannerRunsPackageProvenancesTable.insertIfNotExists(scannerRunId, provenance.id.value)
    return provenance
}

internal fun createArtifactSnippet(
    purl: String,
    path: String,
    startLine: Int,
    endLine: Int,
    license: String,
    score: Float,
    artifactUrl: String
): SnippetDao =
    SnippetDao.new {
        this.purl = purl
        this.path = path
        this.startLine = startLine
        this.endLine = endLine
        this.license = license
        this.score = score
        this.additionalData = null
        this.artifact = RemoteArtifactDao.getOrPut(
            RemoteArtifact(artifactUrl, "${artifactUrl.hashCode()}", "SHA-1")
        )
        this.vcs = null
    }

internal fun createVcsSnippet(
    purl: String,
    path: String,
    startLine: Int,
    endLine: Int,
    license: String,
    score: Float,
    vcsType: RepositoryType,
    vcsUrl: String,
    vcsRevision: String,
    vcsPath: String
): SnippetDao =
    SnippetDao.new {
        this.purl = purl
        this.path = path
        this.startLine = startLine
        this.endLine = endLine
        this.license = license
        this.score = score
        this.additionalData = null
        this.artifact = null
        this.vcs = VcsInfoDao.getOrPut(
            VcsInfo(vcsType, vcsUrl, vcsRevision, vcsPath)
        )
    }

internal fun createScanResultWithSnippetFindings(
    scannerRunId: Long,
    packageProvenanceId: Long,
    snippetFindings: Map<SnippetFindingSeed, List<SnippetDao>>,
    artifactUrl: String? = "https://example.com/results/$scannerRunId",
    vcsType: String? = null,
    vcsUrl: String? = null,
    vcsRevision: String? = null
): Pair<Long, Map<SnippetFindingSeed, SnippetFindingDao>> {
    val scanSummary = ScanSummaryDao.new {
        startTime = Clock.System.now()
        endTime = Clock.System.now()
        hash = "summary-hash-${Clock.System.now().toEpochMilliseconds()}-${snippetFindings.hashCode()}"
    }

    val findingDaos = snippetFindings.mapValues { (finding, snippets) ->
        SnippetFindingDao.new {
            path = finding.path
            startLine = finding.startLine
            endLine = finding.endLine
            this.scanSummary = scanSummary
        }.also { findingDao ->
            findingDao.snippets = SizedCollection(snippets)
        }
    }

    val scanResult = ScanResultDao.new {
        this.artifactUrl = if (vcsUrl == null) artifactUrl else null
        artifactHash = if (vcsUrl == null) "scan-result-$scannerRunId" else null
        artifactHashAlgorithm = if (vcsUrl == null) "SHA-1" else null
        this.vcsType = vcsType
        this.vcsUrl = vcsUrl
        this.vcsRevision = vcsRevision
        this.scanSummary = scanSummary
        scannerName = "SnippetScanner"
        scannerVersion = "1.0.0"
        scannerConfiguration = "default"
        additionalScanResultData = null
    }

    ScannerRunsScanResultsTable.insertIfNotExists(scannerRunId, scanResult.id.value)
    ScanResultPackageProvenancesTable.insertIfNotExists(scanResult.id.value, packageProvenanceId)

    return Pair(scanResult.id.value, findingDaos)
}

/**
 * Create a sub-repository scan result for [seed]'s ORT run, simulating a git submodule that was scanned as a
 * separate scan result. Mirrors real scanner worker behaviour: the sub-repo scan result is linked to the scanner run
 * via [ScannerRunsScanResultsTable] but **not** via [ScanResultPackageProvenancesTable] (which is only populated for
 * the root package VCS). The nested provenance path through [NestedProvenanceSubRepositoriesTable] is the only way
 * to resolve its package identifier.
 *
 * Must be called inside a transaction (e.g. `db.blockingQuery { ... }`).
 */
internal fun addNestedSubRepoScanResult(
    seed: SeedResult,
    subRepoUrl: String = "https://example.com/scm/artifact-package-sub.git",
    subRepoRevision: String = "fedcba9876543210"
): Long {
    val rootVcsInfo = VcsInfoDao.getOrPut(
        VcsInfo(RepositoryType.GIT, "https://example.com/scm/artifact-package.git", "abcdef1234567890", "")
    )

    val nestedProvenance = NestedProvenanceDao.new {
        rootVcs = rootVcsInfo
        rootResolvedRevision = "abcdef1234567890"
        hasOnlyFixedRevisions = true
        vcsPluginConfigs = null
    }

    // path="" is required so the join condition `VcsInfoTable.path eq ""` matches.
    val subRepoVcsInfo = VcsInfoDao.getOrPut(
        VcsInfo(RepositoryType.GIT, subRepoUrl, subRepoRevision, "")
    )

    NestedProvenanceSubRepositoryDao.new {
        this.nestedProvenance = nestedProvenance
        this.vcs = subRepoVcsInfo
        this.resolvedRevision = subRepoRevision
        this.path = "submodule"
    }

    // Link the package provenance to the nested provenance so the join can navigate back to the identifier.
    PackageProvenanceDao[seed.packageProvenanceId].nestedProvenance = nestedProvenance

    // Create the scan result for the sub-repo. Intentionally NOT inserted into ScanResultPackageProvenancesTable —
    // that's what distinguishes nested sub-repo scan results from root ones.
    val subRepoScanResult = ScanResultDao.new {
        artifactUrl = null
        artifactHash = null
        artifactHashAlgorithm = null
        vcsType = "GIT"
        vcsUrl = subRepoUrl
        vcsRevision = subRepoRevision
        scanSummary = ScanSummaryDao.new {
            startTime = Clock.System.now()
            endTime = Clock.System.now()
            hash = "sub-repo-summary-$subRepoRevision"
        }
        scannerName = "SnippetScanner"
        scannerVersion = "1.0.0"
        scannerConfiguration = "default"
        additionalScanResultData = null
    }

    ScannerRunsScanResultsTable.insertIfNotExists(seed.scannerRunId, subRepoScanResult.id.value)

    return subRepoScanResult.id.value
}
