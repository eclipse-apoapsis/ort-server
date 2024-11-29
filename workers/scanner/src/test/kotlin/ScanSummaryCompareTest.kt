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
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.runs.scanner.CopyrightFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.LicenseFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.Snippet
import org.eclipse.apoapsis.ortserver.model.runs.scanner.SnippetFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createArtifactProvenance
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createIssue
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createRepositoryProvenance
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createServerScanResult

/**
 * Test class for testing the hash values generated for scan summaries. Since this functionality does not require
 * database access, it is tested by a separate class to speed up test execution.
 */
class ScanSummaryCompareTest : WordSpec({
    "compareScanSummaries" should {
        "return false for scan summaries with different license findings" {
            val provenance = createRepositoryProvenance()
            val summary1 = createServerScanResult("ScanCode", createIssue("source1"), provenance).summary.mapToOrt()
            val summary2 = createServerScanResult("ScanCode", createIssue("source2"), provenance).summary
                .copy(
                    licenseFindings = setOf(
                        LicenseFinding(
                            "LicenseRef-24",
                            TextLocation("/example/path", 1, 50),
                            Float.MIN_VALUE
                        )
                    )
                )

            compareScanSummaries(summary1, summary2) shouldBe false
        }

        "return false for scan summaries with different copyright findings" {
            val provenance = createRepositoryProvenance()
            val summary1 = createServerScanResult("ScanCode", createIssue("source1"), provenance).summary.mapToOrt()
            val summary2 = createServerScanResult("ScanCode", createIssue("source2"), provenance).summary
                .copy(
                    copyrightFindings = setOf(
                        CopyrightFinding("(C)", TextLocation("/example/path", 1, 50))
                    )
                )

            compareScanSummaries(summary1, summary2) shouldBe false
        }

        "return false for scan summaries with different snippet findings" {
            val provenance = createRepositoryProvenance()
            val summary1 = createServerScanResult("ScanCode", createIssue("source1"), provenance).summary.mapToOrt()
            val summary2 = createServerScanResult("ScanCode", createIssue("source2"), provenance).summary
                .copy(
                    snippetFindings = setOf(
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
                                )
                            )
                        )
                    )
                )

            compareScanSummaries(summary1, summary2) shouldBe false
        }

        "return false for scan summaries with different issues" {
            val provenance = createArtifactProvenance()
            val summary1 = createServerScanResult("ScanCode", createIssue("source1"), provenance).summary.mapToOrt()
            val summary2 = createServerScanResult("ScanCode", createIssue("source2"), provenance).summary

            compareScanSummaries(summary1, summary2) shouldBe false
        }

        "return true for equivalent scan summaries from ORT and ORT Server" {
            val serverSummaries = listOf(
                createServerScanResult("ScanCode", createIssue("source1"), createArtifactProvenance()).summary,
                createServerScanResult("ScanCode", createIssue("source2"), createArtifactProvenance()).summary,
                createServerScanResult("ScanCode", createIssue("source3"), createArtifactProvenance()).summary
                    .copy(
                        licenseFindings = setOf(
                            LicenseFinding(
                                "LicenseRef-24",
                                TextLocation("/example/path", 1, 50),
                                Float.MIN_VALUE
                            )
                        )
                    ),
                createServerScanResult("ScanCode", createIssue("source4"), createArtifactProvenance()).summary
                    .copy(
                        copyrightFindings = setOf(
                            CopyrightFinding("(C)", TextLocation("/example/path", 1, 50))
                        )
                    ),
                createServerScanResult("ScanCode", createIssue("source5"), createRepositoryProvenance()).summary
                    .copy(
                        snippetFindings = setOf(
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
                                    )
                                )
                            )
                        )
                    )
            )

            serverSummaries.forAll { summary ->
                val ortSummary = summary.mapToOrt()
                compareScanSummaries(ortSummary, summary) shouldBe true
            }
        }
    }
})
