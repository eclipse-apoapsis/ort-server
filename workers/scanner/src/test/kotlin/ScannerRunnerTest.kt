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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify

import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.LocalPathScannerWrapper
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

class ScannerRunnerTest : WordSpec({
    afterEach { unmockkAll() }

    val runner = ScannerRunner(mockk(), mockk(), mockk())

    "run" should {
        "return an OrtResult with a valid ScannerRun" {
            val factory = mockScannerWrapperFactory("ScanCode")
            mockScannerWrapperAll(listOf(factory))

            val result = runner.run(mockContext(), OrtResult.EMPTY, ScannerJobConfiguration(), 0L)

            result.scannerRun shouldNotBeNull {
                provenances shouldBe emptySet()
                scanResults shouldBe emptySet()
            }
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
                detectedLicenseMappings = detectedLicenseMapping,
                ignorePatterns = ignorePatterns
            )

            val result = runner.run(mockContext(), OrtResult.EMPTY, scannerConfig, 0L)

            result.scannerRun shouldNotBe null

            result.scannerRun.config shouldBe ScannerConfiguration(
                skipConcluded = true,
                detectedLicenseMapping = detectedLicenseMapping,
                ignorePatterns = ignorePatterns
            )
        }

        "scanner configuration should use default value" {
            val factory = mockScannerWrapperFactory("ScanCode")
            mockScannerWrapperAll(listOf(factory))

            val scannerConfig = ScannerJobConfiguration()

            val result = runner.run(mockContext(), OrtResult.EMPTY, scannerConfig, 0L)

            result.scannerRun shouldNotBe null

            result.scannerRun.config shouldBe ScannerConfiguration()
        }

        "create the configured scanners with the correct options and secrets" {
            val scanCodeFactory = mockScannerWrapperFactory("ScanCode")
            val licenseeFactory = mockScannerWrapperFactory("Licensee")
            mockScannerWrapperAll(listOf(scanCodeFactory, licenseeFactory))

            val scanCodeSecretRefs = mapOf("secret1" to "passRef1", "secret2" to "passRef2")
            val scanCodeSecrets = mapOf("secret1" to "pass1", "secret2" to "pass2")
            val scanCodeConfig = PluginConfig(
                options = mapOf("option1" to "value1", "option2" to "value2"),
                secrets = scanCodeSecretRefs
            )

            val licenseeSecretRefs = mapOf("secret3" to "passRef3", "secret4" to "passRef4")
            val licenseeSecrets = mapOf("secret3" to "pass3", "secret4" to "pass4")
            val licenseeConfig = PluginConfig(
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
                scanCodeFactory.create(scanCodeConfig.copy(secrets = scanCodeSecrets).mapToOrt())
                licenseeFactory.create(licenseeConfig.copy(secrets = licenseeSecrets).mapToOrt())
            }
        }

        "throw an exception if no scanner run was created" {
            val factory = mockScannerWrapperFactory("ScanCode")
            mockScannerWrapperAll(listOf(factory))
            mockkConstructor(Scanner::class)
            coEvery { anyConstructed<Scanner>().scan(any(), any(), any()) } returns OrtResult.EMPTY

            shouldThrow<ScannerException> {
                runner.run(mockContext(), OrtResult.EMPTY, ScannerJobConfiguration(), 0L)
            }
        }

        "return the issues from the scan result storage" {
            val factory = mockScannerWrapperFactory("ScanCode")
            mockScannerWrapperAll(listOf(factory))

            mockkConstructor(OrtServerScanResultStorage::class)
            val issuesMap = mapOf<Provenance, Set<Issue>>(
                mockk<ArtifactProvenance>() to setOf(Issue(source = "source1", message = "message1")),
                mockk<RepositoryProvenance>() to setOf(
                    Issue(source = "source2", message = "message2"),
                    Issue(source = "source3", message = "message3")
                )
            )
            every { anyConstructed<OrtServerScanResultStorage>().getAllIssues() } returns issuesMap

            val result = runner.run(mockContext(), OrtResult.EMPTY, ScannerJobConfiguration(), 0L)

            result.issues shouldBe issuesMap
        }
    }
})

private fun mockScannerWrapperFactory(scannerName: String) =
    mockk<ScannerWrapperFactory> {
        every { descriptor.id } returns scannerName

        every {
            create(any())
        } returns mockk<LocalPathScannerWrapper> {
            every { matcher } returns mockk {
                every { matches(any()) } returns true
            }
            every { details } returns mockk {
                every { name } returns scannerName
            }
            every { descriptor.id } returns scannerName
            every { descriptor.displayName } returns scannerName
            every { readFromStorage } returns true
            every { writeToStorage } returns true
        }
    }

private fun mockScannerWrapperAll(scanners: List<ScannerWrapperFactory>) {
    mockkObject(ScannerWrapperFactory)
    every { ScannerWrapperFactory.ALL } returns scanners.associateByTo(sortedMapOf()) { it.descriptor.id }
}

/**
 * Create a mock for the [WorkerContext] and prepare it to return the given [resolvedPluginConfig] when called to
 * resolve the secrets in the plugin configuration of the given [jobConfig].
 */
private fun mockContext(
    jobConfig: ScannerJobConfiguration = ScannerJobConfiguration(),
    resolvedPluginConfig: Map<String, PluginConfig> = emptyMap()
): WorkerContext =
    mockk {
        coEvery { resolvePluginConfigSecrets(jobConfig.config) } returns resolvedPluginConfig
    }
