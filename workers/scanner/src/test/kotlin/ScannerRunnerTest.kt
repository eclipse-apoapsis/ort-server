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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.plugins.scanners.scancode.ScanCode
import org.ossreviewtoolkit.scanner.ScannerWrapper
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

class ScannerRunnerTest : WordSpec({
    beforeSpec { mockScanCode() }

    afterSpec { unmockkAll() }

    val runner = ScannerRunner(mockk(), mockk(), mockk(), mockk(), mockk())

    "run" should {
        "return an OrtResult with a valid ScannerRun" {
            val result = runner.run(OrtResult.EMPTY, ScannerJobConfiguration())

            val scannerRun = result.scanner.shouldNotBeNull()
            scannerRun.provenances shouldBe emptySet()
            scannerRun.scanResults shouldBe emptySet()
        }

        "pass all the scanner job configuration properties to a scanner" {
            val detectedLicenseMapping: Map<String, String> = mapOf(
                "LicenseRef-scancode-agpl-generic-additional-terms" to SpdxConstants.NOASSERTION,
                "LicenseRef-scancode-generic-cla" to SpdxConstants.NOASSERTION,
                "LicenseRef-scancode-generic-exception" to SpdxConstants.NOASSERTION
            )

            val ignorePatterns: List<String> = listOf(
                "**/*.spdx.yml",
                "**/*.spdx.yaml",
                "**/*.spdx.json"
            )

            val scannerConfig = ScannerJobConfiguration(
                skipConcluded = true,
                createMissingArchives = true,
                detectedLicenseMappings = detectedLicenseMapping,
                ignorePatterns = ignorePatterns
            )

            val result = runner.run(OrtResult.EMPTY, scannerConfig)

            result.scanner shouldNotBe null

            result.scanner?.config shouldBe ScannerConfiguration(
                skipConcluded = true,
                createMissingArchives = true,
                detectedLicenseMapping = detectedLicenseMapping,
                ignorePatterns = ignorePatterns,
                options = emptyMap()
            )
        }
    }
})

private fun mockScanCode() {
    mockkObject(ScannerWrapper)
    mockk<ScannerWrapper> {
        every { ScannerWrapper.ALL } returns sortedMapOf(
            "ScanCode" to mockk {
                every { create(any(), any()) } returns mockk<ScanCode> {
                    every { criteria } returns mockk {
                        every { matches(any()) } returns true
                    }
                    every { details } returns mockk {
                        every { name } returns "ScanCode"
                    }
                    every { name } returns "ScanCode"
                }
            }
        )
    }
}
