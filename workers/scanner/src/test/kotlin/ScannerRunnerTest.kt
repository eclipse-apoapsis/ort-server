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
import io.mockk.verify

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.CommandLinePathScannerWrapper
import org.ossreviewtoolkit.scanner.ScannerWrapper
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

class ScannerRunnerTest : WordSpec({
    afterEach { unmockkAll() }

    val runner = ScannerRunner(mockk(), mockk(), mockk(), mockk(), mockk())

    "run" should {
        "return an OrtResult with a valid ScannerRun" {
            val factory = mockScannerWrapperFactory("ScanCode")
            mockScannerWrapperAll(listOf(factory))

            val result = runner.run(OrtResult.EMPTY, ScannerJobConfiguration())

            val scannerRun = result.scanner.shouldNotBeNull()
            scannerRun.provenances shouldBe emptySet()
            scannerRun.scanResults shouldBe emptySet()
        }

        "pass all the scanner job configuration properties to the scanner" {
            val factory = mockScannerWrapperFactory("ScanCode")
            mockScannerWrapperAll(listOf(factory))

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

        "create the configured scanners with the correct options" {
            val scanCodeFactory = mockScannerWrapperFactory("ScanCode")
            val licenseeFactory = mockScannerWrapperFactory("Licensee")
            mockScannerWrapperAll(listOf(scanCodeFactory, licenseeFactory))

            val scanCodeOptions = mapOf("option1" to "value1", "option2" to "value2")
            val licenseeOptions = mapOf("option3" to "value3", "option4" to "value4")

            val jobConfig = ScannerJobConfiguration(
                scanners = listOf("ScanCode"),
                projectScanners = listOf("Licensee"),
                options = mapOf(
                    "ScanCode" to scanCodeOptions,
                    "Licensee" to licenseeOptions
                )
            )

            runner.run(OrtResult.EMPTY, jobConfig)

            verify(exactly = 1) {
                scanCodeFactory.create(scanCodeOptions)
                licenseeFactory.create(licenseeOptions)
            }
        }
    }
})

private fun mockScannerWrapperFactory(scannerName: String) =
    mockk<ScannerWrapperFactory<*>> {
        every { type } returns scannerName

        every { create(any()) } returns mockk<CommandLinePathScannerWrapper> {
            every { criteria } returns mockk {
                every { matches(any()) } returns true
            }
            every { details } returns mockk {
                every { name } returns scannerName
            }
            every { name } returns scannerName
            every { filterSecretOptions(any()) } returnsArgument 0
        }
    }

private fun mockScannerWrapperAll(scanners: List<ScannerWrapperFactory<*>>) {
    mockkObject(ScannerWrapper)
    mockk<ScannerWrapper> {
        every { ScannerWrapper.ALL } returns scanners.associateByTo(sortedMapOf()) { it.type }
    }
}
