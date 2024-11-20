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
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.utils.spdx.toSpdx

import org.semver4j.Semver

private const val SCANNER_VERSION = "1.0.0"
private const val TIME_STAMP_SECONDS = 1678119934L

/** A matcher that matches all scanners with the default [SCANNER_VERSION]. */
private val scannerMatcher = ScannerMatcher(
    regScannerName = ".*",
    minVersion = Semver(SCANNER_VERSION),
    maxVersion = Semver(SCANNER_VERSION).nextMinor(),
    configuration = null
)

class OrtServerScanResultStorageTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var scanResultStorage: OrtServerScanResultStorage
    private lateinit var scannerRun: ScannerRun

    init {
        beforeEach {
            scannerRun = dbExtension.fixtures.scannerRunRepository.create(dbExtension.fixtures.scannerJob.id)
            scanResultStorage = OrtServerScanResultStorage(dbExtension.db, scannerRun.id)
        }

        fun verifyAssociatedScanResults(scannerRun: ScannerRun, vararg scanResults: ScanResult) {
            dbExtension.db.blockingQuery {
                val associatedScanResults = ScannerRunDao[scannerRun.id].scanResults.map { it.mapToModel() }

                associatedScanResults should haveSize(scanResults.size)
                associatedScanResults.map { it.mapToOrt() } should containExactlyInAnyOrder(*scanResults)
            }
        }

        "write" should {
            "create a repository provenance scan result in the storage and associate it to the scanner run" {
                val provenance = createRepositoryProvenance()
                val scanResult = createScanResult("ScanCode", createIssue("source1"), provenance)
                scanResultStorage.write(scanResult)

                verifyAssociatedScanResults(scannerRun, scanResult)

                scanResultStorage.read(provenance, scannerMatcher) shouldBe listOf(scanResult)
            }

            "create an artifact provenance scan result in the storage and associate it to the scanner run" {
                val provenance = createArtifactProvenance()
                val scanResult = createScanResult("ScanCode", createIssue("source1"), provenance)
                scanResultStorage.write(scanResult)

                verifyAssociatedScanResults(scannerRun, scanResult)

                scanResultStorage.read(provenance, scannerMatcher) shouldBe listOf(scanResult)
            }

            "not create duplicate scan results for repository provenances" {
                val provenance = createRepositoryProvenance()
                val scanResult = createScanResult(
                    "ScanCode",
                    createIssue("source1"),
                    provenance,
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
                    provenance,
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

                val readResult = scanResultStorage.read(repositoryProvenance, scannerMatcher)
                readResult shouldContainExactlyInAnyOrder listOf(scanResult1, scanResult2)
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

                val readResult = scanResultStorage.read(artifactProvenance, scannerMatcher)
                readResult shouldContainExactlyInAnyOrder listOf(scanResult1, scanResult2)
                readResult shouldNotContain scanResult3
            }

            "return empty results list in case no matching repository provenance scan results are found the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult = createScanResult("ScanCode", createIssue("source"), artifactProvenance)

                scanResultStorage.write(scanResult)

                val readResult = scanResultStorage.read(repositoryProvenance, scannerMatcher)
                readResult shouldBe emptyList()
            }

            "return empty results list in case no matching artifact provenance scan results are found the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult = createScanResult("FossID", createIssue("source"), repositoryProvenance)

                scanResultStorage.write(scanResult)

                val readResult = scanResultStorage.read(artifactProvenance, scannerMatcher)
                readResult shouldBe emptyList()
            }

            "apply the scanner matcher" {
                val repositoryProvenance = createRepositoryProvenance()

                val matchingScanResult =
                    createScanResult("ScanCode", createIssue("source"), repositoryProvenance, SCANNER_VERSION)
                val notMatchingScanResult =
                    createScanResult("ScanCode", createIssue("source"), repositoryProvenance, "0.0.1")

                scanResultStorage.write(matchingScanResult)
                scanResultStorage.write(notMatchingScanResult)

                val readResult = scanResultStorage.read(repositoryProvenance, scannerMatcher)
                readResult shouldContain matchingScanResult
                readResult shouldNotContain notMatchingScanResult
            }
        }
    }
}

private fun createVcsInfo() = VcsInfo(
    VcsType.GIT,
    "https://github.com/apache/logging-log4j2.git",
    "be881e503e14b267fb8a8f94b6d15eddba7ed8c4"
)

private fun createRepositoryProvenance(
    vcsInfo: VcsInfo = createVcsInfo(),
    resolvedRevision: String = vcsInfo.revision
) = RepositoryProvenance(vcsInfo, resolvedRevision)

private fun createRemoteArtifact() =
    RemoteArtifact(
        url = "https://repo1.maven.org/maven2/org/apache/logging/" +
                "log4j/log4j-api/2.14.1/log4j-api-2.14.1-sources.jar",
        hash = Hash("b2327c47ca413c1ec183575b19598e281fcd74d8", HashAlgorithm.SHA1)
    )

private fun createArtifactProvenance() = ArtifactProvenance(createRemoteArtifact())

private fun createIssue(source: String) =
    Issue(Instant.ofEpochSecond(TIME_STAMP_SECONDS), source, "message", Severity.ERROR)

private fun createScanResult(
    scannerName: String,
    issue: Issue,
    provenance: KnownProvenance,
    scannerVersion: String = SCANNER_VERSION,
    scannerConfig: String = "config",
    additionalData: Map<String, String> = mapOf("additional1" to "data1", "additional2" to "data2")
): ScanResult {
    return ScanResult(
        provenance = provenance,
        scanner = ScannerDetails(scannerName, scannerVersion, scannerConfig),
        summary = ScanSummary(
            Instant.ofEpochSecond(TIME_STAMP_SECONDS),
            Instant.ofEpochSecond(TIME_STAMP_SECONDS),
            setOf(
                LicenseFinding(
                    "LicenseRef-23",
                    TextLocation("/example/path", 1, 50),
                    Float.MIN_VALUE
                )
            ),
            setOf(
                CopyrightFinding(
                    "Copyright Finding Statement",
                    TextLocation("/example/path", 1, 50)
                )
            ),
            setOf(
                SnippetFinding(
                    TextLocation("/example/path", 1, 50),
                    setOf(
                        Snippet(
                            score = 1.0f,
                            location = TextLocation("/example/path", 1, 50),
                            provenance = createArtifactProvenance(),
                            purl = "org.apache.logging.log4j:log4j-api:2.14.1",
                            license = "LicenseRef-23".toSpdx(),
                            additionalData = mapOf("data" to "value")
                        ),
                        Snippet(
                            score = 2.0f,
                            location = TextLocation("/example/path2", 10, 20),
                            provenance = createRepositoryProvenance(),
                            purl = "org.apache.logging.log4j:log4j-api:2.14.1",
                            license = "LicenseRef-23".toSpdx(),
                            additionalData = mapOf("data2" to "value2")
                        )
                    )
                )
            ),
            listOf(issue)
        ),
        additionalData
    )
}
