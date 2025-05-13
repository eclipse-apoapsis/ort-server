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

package org.eclipse.apoapsis.ortserver.workers.evaluator

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.File

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.shared.orttestdata.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.spdx.toSpdx

const val SCRIPT_FILE = "/example.rules.kts"
private const val PACKAGE_CONFIGURATION_RULES = "package-configurations.rules.kts"
private const val LICENSE_CLASSIFICATIONS_FILE = "/license-classifications.yml"
private const val RESOLUTIONS_FILE = "/resolutions.yml"
private const val UNKNOWN_RULES_KTS = "unknown.rules.kts"

private val resolvedConfigContext = Context("resolvedContext")

class EvaluatorRunnerTest : WordSpec({
    afterEach { unmockkAll() }

    val runner = EvaluatorRunner(mockk())

    "run" should {
        "return an EvaluatorRun with one rule violation" {
            val result = runner.run(
                OrtResult.EMPTY,
                EvaluatorJobConfiguration(
                    ruleSet = SCRIPT_FILE,
                    licenseClassificationsFile = LICENSE_CLASSIFICATIONS_FILE
                ),
                createWorkerContext()
            )
            val expectedRuleViolation = RuleViolation(
                rule = "TEST_RULE",
                pkg = null,
                license = null,
                licenseSource = null,
                severity = Severity.ERROR,
                message = "This is an example RuleViolation for test cases.",
                howToFix = ""
            )

            result.evaluatorRun.violations shouldBe listOf(expectedRuleViolation)
        }

        "try to read the default rule file when no rule set is provided" {
            shouldThrow<ConfigException> {
                runner.run(OrtResult.EMPTY, EvaluatorJobConfiguration(), createWorkerContext())
            }.message shouldContain ORT_EVALUATOR_RULES_FILENAME
        }

        "throw an exception if script file could not be found" {
            shouldThrow<ConfigException> {
                runner.run(
                    OrtResult.EMPTY,
                    EvaluatorJobConfiguration(ruleSet = UNKNOWN_RULES_KTS),
                    createWorkerContext()
                )
            }
        }

        "use the package configurations from the repository configuration" {
            val result = runner.run(
                OrtTestData.result,
                EvaluatorJobConfiguration(ruleSet = PACKAGE_CONFIGURATION_RULES),
                createWorkerContext()
            )

            // The test data contains a package with a LicenseRef-detected1 and a LicenseRef-detected2 license finding
            // (amongst others) and a package configuration that removes the LicenseRef-detected1 finding. The used
            // rules create rule violations if detected licenses LicenseRef-detected1 or LicenseRef-detected2 are
            // found. If the package configuration is applied, the result will only contain a rule violation for the
            // LicenseRef-detected2 finding.
            result.evaluatorRun.violations should haveSize(1)
            result.evaluatorRun.violations.first().message shouldBe "Found forbidden license 'LicenseRef-detected2'."

            result.packageConfigurations should containExactly(
                PackageConfiguration(
                    id = OrtTestData.pkgIdentifier,
                    sourceArtifactUrl = OrtTestData.pkgCuratedSourceArtifactUrl,
                    pathExcludes = listOf(OrtTestData.pathExclude),
                    licenseFindingCurations = listOf(OrtTestData.licenseFindingCuration)
                )
            )
        }

        "use the package configurations from the configured providers" {
            // Remove the package configurations from the ORT result as there can only be one package configuration for
            // each package.
            val ortResult = OrtTestData.result.copy(
                repository = OrtTestData.repository.copy(
                    config = OrtTestData.repository.config.copy(packageConfigurations = emptyList())
                )
            )

            val packageConfigurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf(
                        "path" to "src/test/resources/package-configurations",
                        "mustExist" to "true"
                    )
                )
            )

            val result = runner.run(
                ortResult,
                EvaluatorJobConfiguration(
                    ruleSet = PACKAGE_CONFIGURATION_RULES,
                    packageConfigurationProviders = packageConfigurationProviderConfigs
                ),
                createWorkerContext(packageConfigurationProviderConfigs)
            )

            // The test data contains a package with a LicenseRef-detected1 and a LicenseRef-detected2 license finding
            // (amongst others). The configured provider contains a package configuration that removes the
            // LicenseRef-detected2 finding. The used rules create rule violations if detected licenses
            // LicenseRef-detected1 or LicenseRef-detected2 are found. If the package configuration is applied, the
            // result will only contain a rule violation for the LicenseRef-detected1 finding.
            result.evaluatorRun.violations should haveSize(1)
            result.evaluatorRun.violations.first().message shouldBe "Found forbidden license 'LicenseRef-detected1'."

            result.packageConfigurations should containExactly(
                PackageConfiguration(
                    id = OrtTestData.pkgIdentifier,
                    sourceArtifactUrl = OrtTestData.pkgCuratedSourceArtifactUrl,
                    pathExcludes = emptyList(),
                    licenseFindingCurations = listOf(
                        LicenseFindingCuration(
                            path = "file2",
                            startLines = listOf(1),
                            lineCount = 2,
                            detectedLicense = "LicenseRef-detected2".toSpdx(),
                            concludedLicense = "LicenseRef-detected2-concluded".toSpdx(),
                            reason = LicenseFindingCurationReason.INCORRECT,
                            comment = "Test license finding curation."
                        )
                    )
                )
            )
        }

        "resolve secrets in the package configuration provider configurations" {
            mockkObject(PackageConfigurationProviderFactory)
            every { PackageConfigurationProviderFactory.create(any()) } returns mockk(relaxed = true)

            val packageConfigurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to "ref1")
                ),
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to "ref2")
                )
            )

            val resolvedPackageConfigurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to "value1")
                ),
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to "value2")
                )
            )

            runner.run(
                OrtTestData.result,
                EvaluatorJobConfiguration(
                    ruleSet = SCRIPT_FILE,
                    packageConfigurationProviders = packageConfigurationProviderConfigs
                ),
                createWorkerContext(packageConfigurationProviderConfigs, resolvedPackageConfigurationProviderConfigs)
            )

            verify(exactly = 1) {
                PackageConfigurationProviderFactory.create(
                    resolvedPackageConfigurationProviderConfigs.map { it.mapToOrt() }
                )
            }
        }

        "use the resolutions from the repository configuration and resolutions file" {
            val result = runner.run(
                OrtTestData.result,
                EvaluatorJobConfiguration(resolutionsFile = RESOLUTIONS_FILE, ruleSet = SCRIPT_FILE),
                createWorkerContext()
            )

            val expectedResolutions = OrtTestData.result.repository.config.resolutions.merge(
                yamlMapper.readValue(File("src/test/resources/resolutions.yml"))
            )

            result.resolutions shouldBe expectedResolutions
        }
    }
})

private fun createConfigManager(): ConfigManager {
    val configManager = mockk<ConfigManager> {
        every { getFileAsString(resolvedConfigContext, Path(SCRIPT_FILE)) } returns
                File("src/test/resources/example.rules.kts").readText()

        every { getFileAsString(resolvedConfigContext, Path(PACKAGE_CONFIGURATION_RULES)) } returns
                File("src/test/resources/$PACKAGE_CONFIGURATION_RULES").readText()

        every { getFile(resolvedConfigContext, Path(LICENSE_CLASSIFICATIONS_FILE)) } answers
                { File("src/test/resources/license-classifications.yml").inputStream() }

        every { getFile(resolvedConfigContext, Path(ORT_COPYRIGHT_GARBAGE_FILENAME)) } throws ConfigException("", null)

        every { getFileAsString(resolvedConfigContext, Path(ORT_EVALUATOR_RULES_FILENAME)) } throws
                ConfigException("Could not read '$ORT_EVALUATOR_RULES_FILENAME'.", null)

        every { getFile(resolvedConfigContext, Path(ORT_LICENSE_CLASSIFICATIONS_FILENAME)) } answers
                { File("src/test/resources/license-classifications.yml").inputStream() }

        every { getFile(resolvedConfigContext, Path(ORT_RESOLUTIONS_FILENAME)) } throws ConfigException("", null)

        every { getFile(resolvedConfigContext, Path(RESOLUTIONS_FILE)) } answers
                { File("src/test/resources/resolutions.yml").inputStream() }

        every { getFileAsString(resolvedConfigContext, Path(UNKNOWN_RULES_KTS)) } answers { callOriginal() }

        every { getFile(resolvedConfigContext, Path(UNKNOWN_RULES_KTS)) } answers { callOriginal() }
    }

    return configManager
}

private fun createWorkerContext(
    providerPluginConfigs: List<ProviderPluginConfiguration> = emptyList(),
    resolvedProviderPluginConfigs: List<ProviderPluginConfiguration> = providerPluginConfigs
): WorkerContext {
    val configManagerMock = createConfigManager()

    return mockk {
        every { configManager } returns configManagerMock
        every { ortRun.resolvedJobConfigContext } returns resolvedConfigContext.name
        coEvery { resolveProviderPluginConfigSecrets(providerPluginConfigs) } returns resolvedProviderPluginConfigs
    }
}
