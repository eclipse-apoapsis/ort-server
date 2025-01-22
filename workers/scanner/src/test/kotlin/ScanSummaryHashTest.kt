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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createArtifactProvenance
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createIssue
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createRepositoryProvenance
import org.eclipse.apoapsis.ortserver.workers.scanner.ScanResultFixtures.createScanResult

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.toSpdx

/**
 * Test class for testing the hash values generated for scan summaries. Since this functionality does not require
 * database access, it is tested by a separate class to speed up test execution.
 */
class ScanSummaryHashTest : WordSpec({
    "calculateScanSummariesHash" should {
        "return different hashes for scan summaries with different start times" {
            val provenance = createRepositoryProvenance()
            val summary1 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
            val summary2 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
                .copy(startTime = summary1.startTime.plusNanos(1))

            calculateScanSummaryHash(summary1) shouldNotBe calculateScanSummaryHash(summary2)
        }

        "return different hashes for scan summaries with different end times" {
            val provenance = createRepositoryProvenance()
            val summary1 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
            val summary2 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
                .copy(startTime = summary1.endTime.plusNanos(1))

            calculateScanSummaryHash(summary1) shouldNotBe calculateScanSummaryHash(summary2)
        }

        "return different hashes for scan summaries with different license findings" {
            val provenance = createRepositoryProvenance()
            val summary1 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
            val summary2 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
                .copy(
                    licenseFindings = setOf(
                        LicenseFinding(
                            "LicenseRef-24",
                            TextLocation("/example/path", 1, 50),
                            Float.MIN_VALUE
                        )
                    )
                )

            calculateScanSummaryHash(summary1) shouldNotBe calculateScanSummaryHash(summary2)
        }

        "return different hashes for scan summaries with different copyright findings" {
            val provenance = createRepositoryProvenance()
            val summary1 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
            val summary2 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
                .copy(
                    copyrightFindings = setOf(
                        CopyrightFinding("(C)", TextLocation("/example/path", 1, 50))
                    )
                )

            calculateScanSummaryHash(summary1) shouldNotBe calculateScanSummaryHash(summary2)
        }

        "return different hashes for scan summaries with different snippet findings" {
            val provenance = createRepositoryProvenance()
            val summary1 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
            val summary2 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
                .copy(
                    snippetFindings = setOf(
                        SnippetFinding(
                            TextLocation("/example/path", 1, 50),
                            setOf(
                                Snippet(
                                    score = 1.0f,
                                    location = TextLocation("/example/path", 1, 50),
                                    provenance = createArtifactProvenance().mapToOrt(),
                                    purl = "org.apache.logging.log4j:log4j-api:2.14.1",
                                    license = "LicenseRef-23".toSpdx(),
                                    additionalData = mapOf("data" to "value")
                                )
                            )
                        )
                    )
                )

            calculateScanSummaryHash(summary1) shouldNotBe calculateScanSummaryHash(summary2)
        }

        "return different hashes for scan summaries with different issues" {
            val provenance = createArtifactProvenance()
            val summary1 = createScanResult("ScanCode", createIssue("source1"), provenance).summary
            val summary2 = createScanResult("ScanCode", createIssue("source2"), provenance).summary

            calculateScanSummaryHash(summary1) shouldNotBe calculateScanSummaryHash(summary2)
        }

        "return the same hash for equivalent scan summaries" {
            fun createComplexSummary(): ScanSummary =
                createScanResult("ScanCode", createIssue("source3"), createArtifactProvenance()).summary
                    .copy(
                        licenseFindings = setOf(
                            LicenseFinding(
                                "LicenseRef-24",
                                TextLocation("/example/path", 1, 50),
                                Float.MIN_VALUE
                            )
                        ),
                        copyrightFindings = setOf(
                            CopyrightFinding("(C)", TextLocation("/example/path", 1, 50))
                        ),
                        snippetFindings = setOf(
                            SnippetFinding(
                                TextLocation("/example/path", 1, 50),
                                setOf(
                                    Snippet(
                                        score = 1.0f,
                                        location = TextLocation("/example/path", 1, 50),
                                        provenance = createArtifactProvenance().mapToOrt(),
                                        purl = "org.apache.logging.log4j:log4j-api:2.14.1",
                                        license = "LicenseRef-23".toSpdx(),
                                        additionalData = mapOf("data" to "value")
                                    )
                                )
                            )
                        )
                    )

            val summary1 = createComplexSummary()
            val summary2 = createComplexSummary()

            calculateScanSummaryHash(summary1) shouldBe calculateScanSummaryHash(summary2)
        }
    }
})
