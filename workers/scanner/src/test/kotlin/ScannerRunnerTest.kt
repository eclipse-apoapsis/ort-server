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

import io.mockk.coEvery
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
import org.ossreviewtoolkit.server.model.PluginConfiguration
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

class ScannerRunnerTest : WordSpec({
    afterEach { unmockkAll() }

    val runner = ScannerRunner(mockk(), mockk(), mockk(), mockk())

    "run" should {
        "return an OrtResult with a valid ScannerRun" {
            val factory = mockScannerWrapperFactory("ScanCode")
            mockScannerWrapperAll(listOf(factory))

            val result = runner.run(mockContext(), OrtResult.EMPTY, ScannerJobConfiguration(), 0L)

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

            val result = runner.run(mockContext(), OrtResult.EMPTY, scannerConfig, 0L)

            result.scanner shouldNotBe null

            result.scanner?.config shouldBe ScannerConfiguration(
                skipConcluded = true,
                createMissingArchives = true,
                detectedLicenseMapping = detectedLicenseMapping,
                ignorePatterns = ignorePatterns
            )
        }

        "scanner configuration should use default value" {
            val factory = mockScannerWrapperFactory("ScanCode")
            mockScannerWrapperAll(listOf(factory))

            val scannerConfig = ScannerJobConfiguration()

            val result = runner.run(mockContext(), OrtResult.EMPTY, scannerConfig, 0L)

            result.scanner shouldNotBe null

            result.scanner?.config shouldBe ScannerConfiguration()
        }

        "create the configured scanners with the correct options and secrets" {
            val scanCodeFactory = mockScannerWrapperFactory("ScanCode")
            val licenseeFactory = mockScannerWrapperFactory("Licensee")
            mockScannerWrapperAll(listOf(scanCodeFactory, licenseeFactory))

            val scanCodeSecretRefs = mapOf("secret1" to "passRef1", "secret2" to "passRef2")
            val scanCodeSecrets = mapOf("secret1" to "pass1", "secret2" to "pass2")
            val scanCodeConfig = PluginConfiguration(
                options = mapOf("option1" to "value1", "option2" to "value2"),
                secrets = scanCodeSecretRefs
            )

            val licenseeSecretRefs = mapOf("secret3" to "passRef3", "secret4" to "passRef4")
            val licenseeSecrets = mapOf("secret3" to "pass3", "secret4" to "pass4")
            val licenseeConfig = PluginConfiguration(
                options = mapOf("option3" to "value3", "option4" to "value4"),
                secrets = licenseeSecretRefs
            )

            val jobConfig = ScannerJobConfiguration(
                scanners = listOf("ScanCode"),
                projectScanners = listOf("Licensee"),
                config = mapOf(
                    "ScanCode" to scanCodeConfig,
                    "Licensee" to licenseeConfig
                )
            )

            val resolvedPluginConfig = mapOf(
                "ScanCode" to scanCodeConfig.copy(secrets = scanCodeSecrets),
                "Licensee" to licenseeConfig.copy(secrets = licenseeSecrets)
            )
            val context = mockContext(jobConfig, resolvedPluginConfig)
            runner.run(context, OrtResult.EMPTY, jobConfig, 0L)

            verify(exactly = 1) {
                scanCodeFactory.create(scanCodeConfig.options, scanCodeSecrets)
                licenseeFactory.create(licenseeConfig.options, licenseeSecrets)
            }
        }
    }
})

private fun mockScannerWrapperFactory(scannerName: String) =
    mockk<ScannerWrapperFactory<*>> {
        every { type } returns scannerName

        every {
            create(any<Map<String, String>>(), any<Map<String, String>>())
        } returns mockk<CommandLinePathScannerWrapper> {
            every { matcher } returns mockk {
                every { matches(any()) } returns true
            }
            every { details } returns mockk {
                every { name } returns scannerName
            }
            every { name } returns scannerName
            every { readFromStorage } returns true
            every { writeToStorage } returns true
            every { getVersion(any()) } returns "1.0.0"
        }
    }

private fun mockScannerWrapperAll(scanners: List<ScannerWrapperFactory<*>>) {
    mockkObject(ScannerWrapper)
    mockk<ScannerWrapper> {
        every { ScannerWrapper.ALL } returns scanners.associateByTo(sortedMapOf()) { it.type }
    }
}

/**
 * Create a mock for the [WorkerContext] and prepare it to return the given [resolvedPluginConfig] when called to
 * resolve the secrets in the plugin configuration of the given [jobConfig].
 */
private fun mockContext(
    jobConfig: ScannerJobConfiguration = ScannerJobConfiguration(),
    resolvedPluginConfig: Map<String, PluginConfiguration> = emptyMap()
): WorkerContext =
    mockk {
        coEvery { resolveConfigSecrets(jobConfig.config) } returns resolvedPluginConfig
    }
