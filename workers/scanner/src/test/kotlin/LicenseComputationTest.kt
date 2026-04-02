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

package org.eclipse.apoapsis.ortserver.workers.scanner

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import java.time.Instant

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.OrtResult

class LicenseComputationTest : StringSpec({
    "computeDetectedLicenses should aggregate licenses across multiple provenances" {
        val id = Identifier("Maven", "com.example", "test-pkg", "1.0")

        val provenance1 = ArtifactProvenance(
            sourceArtifact = RemoteArtifact(
                url = "https://example.com/pkg1.jar",
                hash = Hash.NONE
            )
        )
        val provenance2 = ArtifactProvenance(
            sourceArtifact = RemoteArtifact(
                url = "https://example.com/pkg2.jar",
                hash = Hash.NONE
            )
        )

        val scanResult1 = ScanResult(
            provenance = provenance1,
            scanner = ScannerDetails("scanner1", "1.0", ""),
            summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                licenseFindings = setOf(
                    LicenseFinding("MIT", TextLocation("file1.txt", 1)),
                    LicenseFinding("Apache-2.0", TextLocation("file2.txt", 1))
                )
            )
        )

        val scanResult2 = ScanResult(
            provenance = provenance2,
            scanner = ScannerDetails("scanner2", "1.0", ""),
            summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                licenseFindings = setOf(
                    LicenseFinding("Apache-2.0", TextLocation("file3.txt", 1)),
                    LicenseFinding("GPL-3.0", TextLocation("file4.txt", 1))
                )
            )
        )

        val scannerRun = mockk<ScannerRun>(relaxed = true) {
            every { getAllScanResults() } returns mapOf(id to listOf(scanResult1, scanResult2))
        }

        val result = computeDetectedLicenses(scannerRun, id)

        result shouldContainExactlyInAnyOrder setOf("MIT", "Apache-2.0", "GPL-3.0")
    }

    "computeDetectedLicenses should return empty set when no scan results exist" {
        val id = Identifier("Maven", "com.example", "no-scan-pkg", "1.0")

        val scannerRun = mockk<ScannerRun>(relaxed = true) {
            every { getAllScanResults() } returns emptyMap()
        }

        val result = computeDetectedLicenses(scannerRun, id)

        result.shouldBeEmpty()
    }

    "computeDetectedLicenses should deduplicate licenses across provenances" {
        val id = Identifier("NPM", "@example", "lib", "2.0")

        val provenance = ArtifactProvenance(
            sourceArtifact = RemoteArtifact(
                url = "https://example.com/lib.tgz",
                hash = Hash.NONE
            )
        )

        val scanResult1 = ScanResult(
            provenance = provenance,
            scanner = ScannerDetails("scanner1", "1.0", ""),
            summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                licenseFindings = setOf(
                    LicenseFinding("MIT", TextLocation("a.txt", 1))
                )
            )
        )

        val scanResult2 = ScanResult(
            provenance = provenance,
            scanner = ScannerDetails("scanner2", "1.0", ""),
            summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                licenseFindings = setOf(
                    LicenseFinding("MIT", TextLocation("b.txt", 1))
                )
            )
        )

        val scannerRun = mockk<ScannerRun>(relaxed = true) {
            every { getAllScanResults() } returns mapOf(id to listOf(scanResult1, scanResult2))
        }

        val result = computeDetectedLicenses(scannerRun, id)

        result shouldBe setOf("MIT")
    }

    "computeEffectiveLicense should return null when no license info is available" {
        val id = Identifier("Maven", "com.example", "no-license-pkg", "1.0")

        val ortResult = OrtResult.EMPTY
        val resolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResult),
            copyrightGarbage = org.ossreviewtoolkit.model.config.CopyrightGarbage(),
            addAuthorsToCopyrights = false,
            archiver = mockk(relaxed = true),
            licenseFilePatterns = org.ossreviewtoolkit.model.config.LicenseFilePatterns.DEFAULT
        )

        val result = computeEffectiveLicense(resolver, id)

        result.shouldBeNull()
    }
})
