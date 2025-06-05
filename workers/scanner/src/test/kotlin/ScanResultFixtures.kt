/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.CopyrightFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.KnownProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.LicenseFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.RepositoryProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanSummary
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerDetail
import org.eclipse.apoapsis.ortserver.model.runs.scanner.Snippet
import org.eclipse.apoapsis.ortserver.model.runs.scanner.SnippetFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt

import org.ossreviewtoolkit.model.ScanResult as OrtScanResult
import org.ossreviewtoolkit.scanner.ScannerMatcher

import org.semver4j.Semver

/**
 * An object providing test data and functions to create [ScanResult] instances. It can be used by multiple test
 * classes that need to deal with such data structures.
 */
internal object ScanResultFixtures {
    const val SCANNER_VERSION = "1.0.0"
    const val TIME_STAMP_SECONDS = 1678119934L

    /** A matcher that matches all scanners with the default [SCANNER_VERSION]. */
    val scannerMatcher = ScannerMatcher(
        regScannerName = ".*",
        minVersion = Semver(SCANNER_VERSION),
        maxVersion = Semver(SCANNER_VERSION).nextMinor(),
        configuration = "config"
    )
    fun createVcsInfo() = VcsInfo(
        RepositoryType.GIT,
        "https://github.com/apache/logging-log4j2.git",
        "be881e503e14b267fb8a8f94b6d15eddba7ed8c4",
        ""
    )

    fun createRepositoryProvenance(
        vcsInfo: VcsInfo = createVcsInfo(),
        resolvedRevision: String = vcsInfo.revision
    ) = RepositoryProvenance(vcsInfo, resolvedRevision)

    fun createRemoteArtifact() =
        RemoteArtifact(
            url = "https://repo1.maven.org/maven2/org/apache/logging/" +
                    "log4j/log4j-api/2.14.1/log4j-api-2.14.1-sources.jar",
            hashValue = "b2327c47ca413c1ec183575b19598e281fcd74d8",
            hashAlgorithm = "SHA-1"
        )

    fun createArtifactProvenance() = ArtifactProvenance(createRemoteArtifact())

    fun createIssue(source: String) =
        Issue(Instant.fromEpochSeconds(TIME_STAMP_SECONDS), source, "message", Severity.ERROR)

    fun createServerScanResult(
        scannerName: String,
        issue: Issue,
        provenance: KnownProvenance,
        scannerVersion: String = SCANNER_VERSION,
        scannerConfig: String = "config",
        additionalData: Map<String, String> = mapOf("additional1" to "data1", "additional2" to "data2")
    ): ScanResult {
        return ScanResult(
            provenance = provenance,
            scanner = ScannerDetail(scannerName, scannerVersion, scannerConfig),
            summary = ScanSummary(
                Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                "hash-1",
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
                                spdxLicense = "LicenseRef-23",
                                additionalData = mapOf("data" to "value")
                            ),
                            Snippet(
                                score = 2.0f,
                                location = TextLocation("/example/path2", 10, 20),
                                provenance = createRepositoryProvenance(),
                                purl = "org.apache.logging.log4j:log4j-api:2.14.1",
                                spdxLicense = "LicenseRef-23",
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

    fun createScanResult(
        scannerName: String,
        issue: Issue,
        provenance: KnownProvenance,
        scannerVersion: String = SCANNER_VERSION,
        scannerConfig: String = "config",
        additionalData: Map<String, String> = mapOf("additional1" to "data1", "additional2" to "data2")
    ): OrtScanResult =
        createServerScanResult(scannerName, issue, provenance, scannerVersion, scannerConfig, additionalData).mapToOrt()

    /**
     * Return a copy of this [OrtScanResult] that does not contain any objects from related tables.
     */
    fun OrtScanResult.withoutRelations(): OrtScanResult {
        val strippedSummary = summary.copy(
            licenseFindings = emptySet(),
            copyrightFindings = emptySet(),
            snippetFindings = emptySet(),
            issues = emptyList()
        )

        return copy(summary = strippedSummary)
    }
}
