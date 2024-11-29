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

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.SCANNER_VERSION
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createArtifactProvenance
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createIssue
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createRepositoryProvenance
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createScanResult
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.scannerMatcher

import org.ossreviewtoolkit.model.ScanResult as OrtScanResult

class OrtServerScanResultStorageTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var scanResultStorage: OrtServerScanResultStorage
    private lateinit var scannerRun: ScannerRun

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

                scanResultStorage.read(provenance.mapToOrt(), scannerMatcher) shouldBe listOf(scanResult)
            }

            "create an artifact provenance scan result in the storage and associate it to the scanner run" {
                val provenance = createArtifactProvenance()
                val scanResult = createScanResult("ScanCode", createIssue("source1"), provenance)
                scanResultStorage.write(scanResult)

                verifyAssociatedScanResults(scannerRun, scanResult)

                scanResultStorage.read(provenance.mapToOrt(), scannerMatcher) shouldBe listOf(scanResult)
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

                loadScanSummaryIds().size shouldBe 1
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

                loadScanSummaryIds().size shouldBe 2
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

                loadScanSummaryIds().size shouldBe 2
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

                val readResult = scanResultStorage.read(artifactProvenance.mapToOrt(), scannerMatcher)
                readResult shouldContainExactlyInAnyOrder listOf(scanResult1, scanResult2)
                readResult shouldNotContain scanResult3
            }

            "return empty results list in case no matching repository provenance scan results are found the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult = createScanResult("ScanCode", createIssue("source"), artifactProvenance)

                scanResultStorage.write(scanResult)

                val readResult = scanResultStorage.read(repositoryProvenance.mapToOrt(), scannerMatcher)
                readResult shouldBe emptyList()
            }

            "return empty results list in case no matching artifact provenance scan results are found the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult = createScanResult("FossID", createIssue("source"), repositoryProvenance)

                scanResultStorage.write(scanResult)

                val readResult = scanResultStorage.read(artifactProvenance.mapToOrt(), scannerMatcher)
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

                val readResult = scanResultStorage.read(repositoryProvenance.mapToOrt(), scannerMatcher)
                readResult shouldContain matchingScanResult
                readResult shouldNotContain notMatchingScanResult
            }
        }
    }
}
