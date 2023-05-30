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

package org.ossreviewtoolkit.server.workers.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

import java.time.Instant

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
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension

private const val TIME_STAMP_SECONDS = 1678119934L

class OrtServerScanResultStorageTest : WordSpec() {
    private lateinit var scanResultStorage: OrtServerScanResultStorage

    init {
        extension(
            DatabaseTestExtension { db -> scanResultStorage = OrtServerScanResultStorage(db) }
        )

        "write" should {
            "create a repository provenance scan result in the storage" {
                val provenance = createRepositoryProvenance()
                val scanResult = createScanResult("VulnerableCode", createIssue("source1"), provenance)
                scanResultStorage.write(scanResult)

                scanResultStorage.read(provenance) shouldBe listOf(scanResult)
            }

            "create an artifact provenance scan result in the storage" {
                val provenance = createArtifactProvenance()
                val scanResult = createScanResult("VulnerableCode", createIssue("source1"), provenance)
                scanResultStorage.write(scanResult)

                scanResultStorage.read(provenance) shouldBe listOf(scanResult)
            }
        }

        "read" should {
            "read scan results by repository provenance in the database" {
                val repositoryProvenance = createRepositoryProvenance()
                val artifactProvenance = createArtifactProvenance()

                val scanResult1 = createScanResult("VulnerableCode", createIssue("source1"), repositoryProvenance)
                val scanResult2 = createScanResult("FossID", createIssue("source2"), repositoryProvenance)
                val scanResult3 = createScanResult("FossID", createIssue("source3"), artifactProvenance)

                scanResultStorage.write(scanResult1)
                scanResultStorage.write(scanResult2)
                scanResultStorage.write(scanResult3)

                val readResult = scanResultStorage.read(repositoryProvenance)
                readResult shouldContainExactlyInAnyOrder listOf(scanResult1, scanResult2)
                readResult shouldNotContain scanResult3
            }

            "read scan results by artifact provenance in the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult1 = createScanResult("VulnerableCode", createIssue("source1"), artifactProvenance)
                val scanResult2 = createScanResult("FossID", createIssue("source2"), artifactProvenance)
                val scanResult3 = createScanResult("FossID", createIssue("source3"), repositoryProvenance)

                scanResultStorage.write(scanResult1)
                scanResultStorage.write(scanResult2)
                scanResultStorage.write(scanResult3)

                val readResult = scanResultStorage.read(artifactProvenance)
                readResult shouldContainExactlyInAnyOrder listOf(scanResult1, scanResult2)
                readResult shouldNotContain scanResult3
            }

            "return empty results list in case no matching repository provenance scan results are found the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult = createScanResult("VulnerableCode", createIssue("source"), artifactProvenance)

                scanResultStorage.write(scanResult)

                val readResult = scanResultStorage.read(repositoryProvenance)
                readResult shouldBe emptyList()
            }

            "return empty results list in case no matching artifact provenance scan results are found the database" {
                val artifactProvenance = createArtifactProvenance()
                val repositoryProvenance = createRepositoryProvenance()

                val scanResult = createScanResult("FossID", createIssue("source"), repositoryProvenance)

                scanResultStorage.write(scanResult)

                val readResult = scanResultStorage.read(artifactProvenance)
                readResult shouldBe emptyList()
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

private fun createScanResult(scannerName: String, issue: Issue, provenance: KnownProvenance): ScanResult {
    return ScanResult(
        provenance = provenance,
        scanner = ScannerDetails(scannerName, "1.2", "config"),
        summary = ScanSummary(
            Instant.ofEpochSecond(TIME_STAMP_SECONDS),
            Instant.ofEpochSecond(TIME_STAMP_SECONDS),
            "fyi3g4i72g482",
            sortedSetOf(
                LicenseFinding(
                    "LicenseRef-23",
                    TextLocation("//example/path", 1, 50),
                    Float.MIN_VALUE
                )
            ),
            sortedSetOf(
                CopyrightFinding(
                    "Copyright Finding Statement",
                    TextLocation("//example/path", 1, 50)
                )
            ),
            listOf(issue)
        ),
        mapOf("additional" to "data", "additional" to "data")
    )
}
