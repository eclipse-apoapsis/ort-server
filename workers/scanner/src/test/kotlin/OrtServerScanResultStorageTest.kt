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

package org.eclipse.apoapsis.ortserver.workers.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.SCANNER_VERSION
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createArtifactProvenance
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createIssue
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createRemoteArtifact
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createRepositoryProvenance
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createScanResult
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createVcsInfo
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.scannerMatcher
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.withoutRelations

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

import org.ossreviewtoolkit.model.ScanResult as OrtScanResult

class OrtServerScanResultStorageTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var scanResultStorage: OrtServerScanResultStorage
    private lateinit var scannerRun: ScannerRun

    /**
     * Create a [PackageProvenanceDao] for the given [artifact] and associate it with [scannerRun].
     * [namespace] allows creating multiple distinct packages that share the same artifact.
     */
    private fun createPackageProvenanceForArtifact(
        scannerRun: ScannerRun,
        artifact: RemoteArtifact,
        namespace: String = "com.example"
    ): PackageProvenanceDao =
        dbExtension.db.blockingQuery {
            val identifier = IdentifierDao.getOrPut(
                Identifier(type = "Maven", namespace = namespace, name = "lib", version = "1.0")
            )
            val artifactDao = RemoteArtifactDao.getOrPut(artifact)
            val provenance = PackageProvenanceDao.new {
                this.identifier = identifier
                this.artifact = artifactDao
            }
            ScannerRunsPackageProvenancesTable.insertIfNotExists(scannerRun.id, provenance.id.value)
            provenance
        }

    /**
     * Create a [PackageProvenanceDao] for the given [vcsInfo] and associate it with [scannerRun].
     */
    private fun createPackageProvenanceForVcs(
        scannerRun: ScannerRun,
        vcsInfo: VcsInfo
    ): PackageProvenanceDao =
        dbExtension.db.blockingQuery {
            val identifier = IdentifierDao.getOrPut(
                Identifier(type = "Maven", namespace = "com.example", name = "lib", version = "1.0")
            )
            val vcsDao = VcsInfoDao.getOrPut(vcsInfo)
            val provenance = PackageProvenanceDao.new {
                this.identifier = identifier
                this.vcs = vcsDao
                this.resolvedRevision = vcsInfo.revision
            }
            ScannerRunsPackageProvenancesTable.insertIfNotExists(scannerRun.id, provenance.id.value)
            provenance
        }

    /**
     * Assert that [ScanResultPackageProvenancesTable] contains a row linking [scanResult] to [packageProvenance].
     * [run] defaults to the test's [scannerRun].
     */
    private fun verifyJunctionRow(
        scanResult: OrtScanResult,
        packageProvenance: PackageProvenanceDao,
        run: ScannerRun = scannerRun
    ) {
        dbExtension.db.blockingQuery {
            val scanResultDao = ScannerRunDao[run.id].scanResults.first {
                it.scannerName == scanResult.scanner.name
            }
            val count = ScanResultPackageProvenancesTable.selectAll().where {
                (ScanResultPackageProvenancesTable.scanResultId eq scanResultDao.id) and
                        (ScanResultPackageProvenancesTable.packageProvenanceId eq packageProvenance.id)
            }.count()
            count shouldBe 1L
        }
    }

    /**
     * Return a [Set] with the IDs of all scan summaries assigned to a scan result.
     */
    private fun loadScanSummaryIds(): Set<Long> =
        dbExtension.db.blockingQuery {
            ScanResultDao.all().map { it.scanSummary.id.value }.toSet()
        }

    init {
        beforeEach {
            scannerRun = dbExtension.fixtures.scannerRunRepository.create(dbExtension.fixtures.scannerJob.id)
            scanResultStorage = OrtServerScanResultStorage(dbExtension.db, scannerRun.id)
        }

        fun verifyAssociatedScanResults(scannerRun: ScannerRun, vararg scanResults: OrtScanResult) {
            dbExtension.db.blockingQuery {
                val associatedScanResults = ScannerRunDao[scannerRun.id].scanResults.map { it.mapToModel() }

                associatedScanResults shouldHaveSize scanResults.size
                associatedScanResults.map { it.mapToOrt() } should containExactlyInAnyOrder(*scanResults)
            }
        }

        "write" should {
            "create a repository provenance scan result in the storage and associate it to the scanner run" {
                val provenance = createRepositoryProvenance()
                val scanResult = createScanResult("ScanCode", createIssue("source1"), provenance)
                scanResultStorage.write(scanResult)

                verifyAssociatedScanResults(scannerRun, scanResult)
            }

            "create an artifact provenance scan result in the storage and associate it to the scanner run" {
                val provenance = createArtifactProvenance()
                val scanResult = createScanResult("ScanCode", createIssue("source1"), provenance)
                scanResultStorage.write(scanResult)

                verifyAssociatedScanResults(scannerRun, scanResult)
            }

            "not create duplicate scan results for repository provenances" {
                val provenance = createRepositoryProvenance()
                val scanResult = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    provenance
                )
                scanResultStorage.write(scanResult)

                scanResultStorage.write(scanResult)

                dbExtension.db.dbQuery {
                    ScanResultDao.all().count()
                } shouldBe 1
            }

            "not create duplicate scan results for artifact provenances" {
                val provenance = createArtifactProvenance()
                val scanResult = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    provenance
                )
                scanResultStorage.write(scanResult)

                scanResultStorage.write(scanResult)

                dbExtension.db.dbQuery {
                    ScanResultDao.all().count()
                } shouldBe 1
            }

            "duplicate a result if additional data is different" {
                val provenance = createArtifactProvenance()
                val scanResult1 = createScanResult("ScanCode", createIssue("source1"), provenance)
                val scanResult2 = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    provenance,
                    additionalData = mapOf("more" to "data")
                )
                scanResultStorage.write(scanResult1)

                scanResultStorage.write(scanResult2)

                dbExtension.db.dbQuery {
                    ScanResultDao.all().count()
                } shouldBe 2
            }

            "duplicate a result if the scanner version is different" {
                val provenance = createRepositoryProvenance()
                val scanResult1 = createScanResult("ScanCode", createIssue("source1"), provenance)
                val scanResult2 = createScanResult("ScanCode", createIssue("source1"), provenance, "0.0.1")
                scanResultStorage.write(scanResult1)

                scanResultStorage.write(scanResult2)

                dbExtension.db.dbQuery {
                    ScanResultDao.all().count()
                } shouldBe 2
            }

            "duplicate a result if the scanner name is different" {
                val provenance = createRepositoryProvenance()
                val scanResult1 = createScanResult("ScanCode", createIssue("source1"), provenance)
                val scanResult2 = createScanResult("FossID", createIssue("source1"), provenance)
                scanResultStorage.write(scanResult1)

                scanResultStorage.write(scanResult2)

                dbExtension.db.dbQuery {
                    ScanResultDao.all().count()
                } shouldBe 2
            }

            "duplicate a result if the scanner config is different" {
                val provenance = createArtifactProvenance()
                val scanResult1 = createScanResult("ScanCode", createIssue("source1"), provenance)
                val scanResult2 = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    provenance,
                    scannerConfig = "other_config"
                )
                scanResultStorage.write(scanResult1)

                scanResultStorage.write(scanResult2)

                dbExtension.db.dbQuery {
                    ScanResultDao.all().count()
                } shouldBe 2
            }

            "not duplicate a scan summary if there is already a matching one" {
                val scanResult1 = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    createArtifactProvenance()
                )
                val scanResult2 = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    createRepositoryProvenance()
                )
                scanResultStorage.write(scanResult1)

                scanResultStorage.write(scanResult2)

                loadScanSummaryIds() shouldHaveSize 1
            }

            "duplicate a scan summary if it has different timestamps" {
                val scanResult1 = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    createArtifactProvenance()
                )
                val summary2 = scanResult1.summary.copy(startTime = scanResult1.summary.startTime.plusSeconds(1))
                val scanResult2 = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    createRepositoryProvenance()
                ).copy(summary = summary2)
                scanResultStorage.write(scanResult1)

                scanResultStorage.write(scanResult2)

                loadScanSummaryIds() shouldHaveSize 2
            }

            "duplicate a scan summary if it has different content" {
                val scanResult1 = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    createArtifactProvenance()
                )
                val scanResult2 = createScanResult(
                    "ScanCode",
                    createIssue("source2"),
                    createRepositoryProvenance()
                )
                scanResultStorage.write(scanResult1)

                scanResultStorage.write(scanResult2)

                loadScanSummaryIds() shouldHaveSize 2
            }
        }

        "read" should {
            "read scan results by repository provenance and associate them with the scanner run" {
                val repositoryProvenance = createRepositoryProvenance()
                val artifactProvenance = createArtifactProvenance()

                val scanResult1 = createScanResult("ScanCode", createIssue("source1"), repositoryProvenance)
                val scanResult2 = createScanResult("FossID", createIssue("source2"), repositoryProvenance)
                val scanResult3 = createScanResult("FossID", createIssue("source3"), artifactProvenance)

                scanResultStorage.write(scanResult1)
                scanResultStorage.write(scanResult2)
                scanResultStorage.write(scanResult3)

                verifyAssociatedScanResults(scannerRun, scanResult1, scanResult2, scanResult3)

                val readResult = scanResultStorage.read(repositoryProvenance.mapToOrt(), scannerMatcher)
                readResult shouldContainExactlyInAnyOrder listOf(
                    scanResult1.withoutRelations(),
                    scanResult2.withoutRelations()
                )
                readResult shouldNotContain scanResult3
            }

            "read scan results by artifact provenance and associate them with the scanner run" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult1 = createScanResult("ScanCode", createIssue("source1"), artifactProvenance)
                val scanResult2 = createScanResult("FossID", createIssue("source2"), artifactProvenance)
                val scanResult3 = createScanResult("FossID", createIssue("source3"), repositoryProvenance)

                scanResultStorage.write(scanResult1)
                scanResultStorage.write(scanResult2)
                scanResultStorage.write(scanResult3)

                verifyAssociatedScanResults(scannerRun, scanResult1, scanResult2, scanResult3)

                val readResult = scanResultStorage.read(artifactProvenance.mapToOrt(), scannerMatcher)
                readResult shouldContainExactlyInAnyOrder listOf(
                    scanResult1.withoutRelations(),
                    scanResult2.withoutRelations()
                )
                readResult shouldNotContain scanResult3
            }

            "return empty results list in case no matching repository provenance scan results are found the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult = createScanResult("ScanCode", createIssue("source"), artifactProvenance)

                scanResultStorage.write(scanResult)

                val readResult = scanResultStorage.read(repositoryProvenance.mapToOrt(), scannerMatcher)
                readResult should beEmpty()
            }

            "return empty results list in case no matching artifact provenance scan results are found the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult = createScanResult("FossID", createIssue("source"), repositoryProvenance)

                scanResultStorage.write(scanResult)

                val readResult = scanResultStorage.read(artifactProvenance.mapToOrt(), scannerMatcher)
                readResult should beEmpty()
            }

            "apply the scanner matcher" {
                val repositoryProvenance = createRepositoryProvenance()

                val matchingScanResult =
                    createScanResult("ScanCode", createIssue("source"), repositoryProvenance, SCANNER_VERSION)
                val notMatchingScanResult =
                    createScanResult("ScanCode", createIssue("source"), repositoryProvenance, "0.0.1")

                scanResultStorage.write(matchingScanResult)
                scanResultStorage.write(notMatchingScanResult)

                val readResult = scanResultStorage.read(repositoryProvenance.mapToOrt(), scannerMatcher)
                readResult shouldContain matchingScanResult.withoutRelations()
                readResult shouldNotContain notMatchingScanResult
            }
        }

        "write" should {
            "link a new artifact-provenance scan result to the matching package provenance" {
                val remoteArtifact = createRemoteArtifact()
                val packageProvenance = createPackageProvenanceForArtifact(scannerRun, remoteArtifact)
                val scanResult = createScanResult("ScanCode", createIssue("source"), createArtifactProvenance())

                scanResultStorage.write(scanResult)

                verifyJunctionRow(scanResult, packageProvenance)
            }

            "link a new VCS-provenance scan result to the matching package provenance" {
                val vcsInfo = createVcsInfo()
                val packageProvenance = createPackageProvenanceForVcs(scannerRun, vcsInfo)
                val scanResult =
                    createScanResult("ScanCode", createIssue("source"), createRepositoryProvenance(vcsInfo))

                scanResultStorage.write(scanResult)

                verifyJunctionRow(scanResult, packageProvenance)
            }

            "link a cached (reused) scan result to the new scanner run's package provenance" {
                val remoteArtifact = createRemoteArtifact()
                val scanResult = createScanResult("ScanCode", createIssue("source"), createArtifactProvenance())

                // Write in run 1 (no package provenance yet — no junction row expected).
                scanResultStorage.write(scanResult)

                // Start run 2, add a package provenance, then read (cache hit path).
                val scannerRun2 = dbExtension.fixtures.scannerRunRepository.create(dbExtension.fixtures.scannerJob.id)
                val storage2 = OrtServerScanResultStorage(dbExtension.db, scannerRun2.id)
                val packageProvenance = createPackageProvenanceForArtifact(scannerRun2, remoteArtifact)

                storage2.write(scanResult)

                verifyJunctionRow(scanResult, packageProvenance, scannerRun2)
            }

            "link a scan result to all package provenances sharing the same artifact URL" {
                val remoteArtifact = createRemoteArtifact()
                val provenance1 = createPackageProvenanceForArtifact(scannerRun, remoteArtifact, namespace = "com.a")
                val provenance2 = createPackageProvenanceForArtifact(scannerRun, remoteArtifact, namespace = "com.b")
                val scanResult = createScanResult("ScanCode", createIssue("source"), createArtifactProvenance())

                scanResultStorage.write(scanResult)

                verifyJunctionRow(scanResult, provenance1)
                verifyJunctionRow(scanResult, provenance2)
            }
        }

        "getAllIssues" should {
            "return a map with all issues from newly added scan results" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()
                val issue1 = createIssue("source1")
                val issue2 = createIssue("source2")
                val issue3 = createIssue("source3")
                val scanResult1 = createScanResult("ScanCode", issue1, artifactProvenance)
                val scanResult2 = createScanResult("FossID", issue2, repositoryProvenance)
                val scanResult3 = createScanResult("ScanCode", issue3, repositoryProvenance)

                scanResultStorage.write(scanResult1)
                scanResultStorage.write(scanResult2)
                scanResultStorage.write(scanResult3)

                val issues = scanResultStorage.getAllIssues()

                issues[artifactProvenance.mapToOrt()] shouldContainExactlyInAnyOrder listOf(issue1.mapToOrt())
                issues[repositoryProvenance.mapToOrt()] shouldContainExactlyInAnyOrder listOf(
                    issue2.mapToOrt(), issue3.mapToOrt()
                )

                issues.keys shouldHaveSize 2
            }

            "include issues from referenced scan results" {
                val provenance = createArtifactProvenance()
                val issue = createIssue("source")
                val scanResult = createScanResult("ScanCode", issue, provenance)
                scanResultStorage.write(scanResult)

                val storage2 = OrtServerScanResultStorage(dbExtension.db, scannerRun.id)
                storage2.write(scanResult)

                val issues = storage2.getAllIssues()

                issues[provenance.mapToOrt()] shouldContainExactlyInAnyOrder listOf(issue.mapToOrt())
            }
        }
    }
}
