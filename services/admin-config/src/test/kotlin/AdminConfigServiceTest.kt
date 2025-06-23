/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services.config

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs

import io.mockk.every
import io.mockk.spyk

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

class AdminConfigServiceTest : WordSpec({
    "loadAdminConfig()" should {
        "return the default configuration if there is no config file" {
            val service = createService(null) {
                every { containsFile(context, Path(AdminConfigService.DEFAULT_PATH)) } returns false
            }

            service.loadAdminConfig(context, ORGANIZATION_ID) shouldBe AdminConfig.DEFAULT
        }

        "throw for an unresolvable non-default config file" {
            val exception = IllegalArgumentException("Test exception: unresolvable config file")
            val service = createService {
                every { getFile(context, Path(ADMIN_CONFIG_PATH)) } throws exception
            }

            shouldThrow<IllegalArgumentException> {
                service.loadAdminConfig(context, ORGANIZATION_ID)
            } shouldBe exception
        }
    }

    "getRuleSet()" should {
        "return the a default rule set with the standard names from ORT" {
            val service = createServiceWithConfig("")

            val ruleSet = service.loadAdminConfig(context, ORGANIZATION_ID).getRuleSet(null)

            ruleSet.copyrightGarbageFile shouldBe ORT_COPYRIGHT_GARBAGE_FILENAME
            ruleSet.licenseClassificationsFile shouldBe ORT_LICENSE_CLASSIFICATIONS_FILENAME
            ruleSet.resolutionsFile shouldBe ORT_RESOLUTIONS_FILENAME
            ruleSet.evaluatorRules shouldBe ORT_EVALUATOR_RULES_FILENAME
        }

        "parse the default rule set from the config file" {
            val config = """
                    defaultRuleSet {
                      copyrightGarbageFile = "testCopyrightGarbageFile"
                      licenseClassificationsFile = "testLicenseClassificationsFile"
                      resolutionsFile = "testResolutionsFile"
                      evaluatorRules = "testEvaluatorRules"
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val ruleSet = service.loadAdminConfig(context, ORGANIZATION_ID).getRuleSet(null)

            ruleSet.copyrightGarbageFile shouldBe "testCopyrightGarbageFile"
            ruleSet.licenseClassificationsFile shouldBe "testLicenseClassificationsFile"
            ruleSet.resolutionsFile shouldBe "testResolutionsFile"
            ruleSet.evaluatorRules shouldBe "testEvaluatorRules"
        }

        "return a named rule set" {
            val config = """
                    ruleSets {
                      customRuleSet1 {
                        copyrightGarbageFile = "testCopyrightGarbageFile1"
                        licenseClassificationsFile = "testLicenseClassificationsFile1"
                        resolutionsFile = "testResolutionsFile1"
                        evaluatorRules = "testEvaluatorRules1"
                      }  
                      customRuleSet2 {
                        copyrightGarbageFile = "testCopyrightGarbageFile2"
                        licenseClassificationsFile = "testLicenseClassificationsFile2"
                        resolutionsFile = "testResolutionsFile2"
                        evaluatorRules = "testEvaluatorRules2"
                      }  
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val ruleSet = service.loadAdminConfig(context, ORGANIZATION_ID).getRuleSet("customRuleSet1")

            ruleSet.copyrightGarbageFile shouldBe "testCopyrightGarbageFile1"
            ruleSet.licenseClassificationsFile shouldBe "testLicenseClassificationsFile1"
            ruleSet.resolutionsFile shouldBe "testResolutionsFile1"
            ruleSet.evaluatorRules shouldBe "testEvaluatorRules1"
        }

        "throw an exception for an unknown rule set name" {
            val config = """
                    ruleSets {
                      someRuleSet {
                        copyrightGarbageFile = "testCopyrightGarbageFile1"
                        licenseClassificationsFile = "testLicenseClassificationsFile1"
                        resolutionsFile = "testResolutionsFile1"
                        evaluatorRules = "testEvaluatorRules1"
                      }  
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val nonExistentRuleSetName = "nonExistentRuleSet"
            val exception = shouldThrow<NoSuchElementException> {
                service.loadAdminConfig(context, ORGANIZATION_ID).getRuleSet(nonExistentRuleSetName)
            }

            exception.message shouldContain nonExistentRuleSetName
            exception.message shouldContain "No rule set"
        }

        "set undefined properties in rule sets from the default rule set" {
            val config = """
                    defaultRuleSet {
                      copyrightGarbageFile = "defaultCopyrightGarbageFile"
                      licenseClassificationsFile = "defaultLicenseClassificationsFile"
                      resolutionsFile = "defaultResolutionsFile"
                      evaluatorRules = "defaultEvaluatorRules"
                    }
                    ruleSets {
                      customRuleSet1 {
                        copyrightGarbageFile = "testCopyrightGarbageFile1"
                      }  
                      customRuleSet2 {
                        licenseClassificationsFile = "testLicenseClassificationsFile2"
                        resolutionsFile = "testResolutionsFile2"
                        evaluatorRules = "testEvaluatorRules2"
                      }  
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)
            val adminConfig = service.loadAdminConfig(context, ORGANIZATION_ID)

            val ruleSet1 = adminConfig.getRuleSet("customRuleSet1")
            ruleSet1.copyrightGarbageFile shouldBe "testCopyrightGarbageFile1"
            ruleSet1.licenseClassificationsFile shouldBe "defaultLicenseClassificationsFile"
            ruleSet1.resolutionsFile shouldBe "defaultResolutionsFile"
            ruleSet1.evaluatorRules shouldBe "defaultEvaluatorRules"

            val ruleSet2 = adminConfig.getRuleSet("customRuleSet2")
            ruleSet2.copyrightGarbageFile shouldBe "defaultCopyrightGarbageFile"
            ruleSet2.licenseClassificationsFile shouldBe "testLicenseClassificationsFile2"
            ruleSet2.resolutionsFile shouldBe "testResolutionsFile2"
            ruleSet2.evaluatorRules shouldBe "testEvaluatorRules2"
        }
    }

    "ruleSetNames" should {
        "be empty for the default configuration" {
            AdminConfig.DEFAULT.ruleSetNames should beEmpty()
        }

        "be empty for a configuration containing only the default rule set" {
            val config = """
                    defaultRuleSet {
                      copyrightGarbageFile = "testCopyrightGarbageFile"
                      licenseClassificationsFile = "testLicenseClassificationsFile"
                      resolutionsFile = "testResolutionsFile"
                      evaluatorRules = "testEvaluatorRules"
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)
            val adminConfig = service.loadAdminConfig(context, ORGANIZATION_ID)

            adminConfig.ruleSetNames should beEmpty()
        }

        "contain the names of all defined rule sets" {
            val config = """
                    ruleSets {
                      customRuleSet1 {
                        copyrightGarbageFile = "testCopyrightGarbageFile1"
                      }  
                      customRuleSet2 {
                        licenseClassificationsFile = "testLicenseClassificationsFile2"
                      }  
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)
            val adminConfig = service.loadAdminConfig(context, ORGANIZATION_ID)

            adminConfig.ruleSetNames shouldContainExactlyInAnyOrder listOf("customRuleSet1", "customRuleSet2")
        }
    }

    "scannerConfig" should {
        "return a default configuration if nothing is configured" {
            val ortScannerConfig = ScannerConfiguration()
            val service = createServiceWithConfig("")

            val scannerConfig = service.loadAdminConfig(context, ORGANIZATION_ID).scannerConfig

            scannerConfig shouldBeSameInstanceAs AdminConfig.DEFAULT_SCANNER_CONFIG
            scannerConfig.detectedLicenseMappings shouldBe ortScannerConfig.detectedLicenseMapping
            scannerConfig.ignorePatterns shouldBe ortScannerConfig.ignorePatterns
            scannerConfig.sourceCodeOrigins shouldContainExactly listOf(
                SourceCodeOrigin.VCS,
                SourceCodeOrigin.ARTIFACT
            )
        }

        "return a configuration with default settings for an empty scanner section" {
            val config = """
                    scanner {
                    }
                """.trimIndent()
            val ortScannerConfig = ScannerConfiguration()
            val service = createServiceWithConfig(config)

            val scannerConfig = service.loadAdminConfig(context, ORGANIZATION_ID).scannerConfig

            scannerConfig.detectedLicenseMappings shouldBe ortScannerConfig.detectedLicenseMapping
            scannerConfig.ignorePatterns shouldBe ortScannerConfig.ignorePatterns
            scannerConfig.sourceCodeOrigins shouldContainExactly listOf(
                SourceCodeOrigin.VCS,
                SourceCodeOrigin.ARTIFACT
            )
        }

        "parse the scanner section from the config file" {
            val config = """
                    scanner {
                      detectedLicenseMappings = {
                        "detectedLicense1" = "spdxLicense1"
                        "detectedLicense2" = "spdxLicense2"
                      }
                      ignorePatterns = ["ignorePattern1", "ignorePattern2"]
                      sourceCodeOrigins = ["vcs", "artifact"]
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val scannerConfig = service.loadAdminConfig(context, ORGANIZATION_ID).scannerConfig

            scannerConfig.detectedLicenseMappings shouldContainExactly mapOf(
                "detectedLicense1" to "spdxLicense1",
                "detectedLicense2" to "spdxLicense2"
            )
            scannerConfig.ignorePatterns shouldContainExactly listOf("ignorePattern1", "ignorePattern2")
            scannerConfig.sourceCodeOrigins shouldContainExactly listOf(
                SourceCodeOrigin.VCS,
                SourceCodeOrigin.ARTIFACT
            )
        }
    }
})

/** The context used by tests when querying the admin configuration. */
private val context = Context("testContext")

/** A path to the admin config used by tests per default. */
private const val ADMIN_CONFIG_PATH = "test-ort-server.conf"

/** A test organization ID. */
private const val ORGANIZATION_ID = 20250617160321L

/**
 * Return an [AdminConfigService] instance for testing that uses a [ConfigManager] spy with the given
 * [adminConfigPath]. The given [block] can be used to further configure the [ConfigManager] spy.
 */
private fun createService(
    adminConfigPath: String? = ADMIN_CONFIG_PATH,
    block: ConfigManager.() -> Unit = {}
): AdminConfigService {
    val config = adminConfigPath?.let { ConfigFactory.parseMap(mapOf("adminConfigPath" to it)) }
        ?: ConfigFactory.empty()
    val configManager = spyk(ConfigManager.create(config)) {
        block()
    }

    return AdminConfigService(configManager)
}

/**
 * Return an [AdminConfigService] instance for testing that yields an [AdminConfig] that is parsed from the given
 * [config] string.
 */
private fun createServiceWithConfig(config: String): AdminConfigService =
    createService { initAdminConfig(config) }

/**
 * Prepare this [ConfigManager] spy to return configuration data with the given [content] when asked for the
 * default admin configuration file.
 */
private fun ConfigManager.initAdminConfig(content: String) {
    every { getFile(context, Path(ADMIN_CONFIG_PATH)) } returns content.byteInputStream()
}
