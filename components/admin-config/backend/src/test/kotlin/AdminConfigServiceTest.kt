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

package org.eclipse.apoapsis.ortserver.components.adminconfig

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.spyk

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

@Suppress("LargeClass")
class AdminConfigServiceTest : WordSpec({
    "loadAdminConfig()" should {
        "return the default configuration if there is no config file" {
            val service = createService(null) {
                every { containsFile(context, Path(AdminConfigService.DEFAULT_PATH)) } returns false
            }

            service.loadAdminConfig(context) shouldBe AdminConfig.DEFAULT
        }

        "throw an exception if config file validation fails" {
            val config = """
                    defaultRuleSet {
                      copyrightGarbageFile = "non-default-garbage.yml"
                    }
                """.trimIndent()

            val service = createService {
                initAdminConfig(config)

                every { containsFile(context, Path("non-default-garbage.yml")) } returns false
            }

            val exception = shouldThrow<ConfigException> {
                service.loadAdminConfig(context, validateConfigFiles = true)
            }

            exception.message shouldContain "non-default-garbage.yml"
        }

        "throw an exception if the reporter config is invalid" {
            val config = """
                    reporter {
                        reports {
                            invalidReporter {
                                pluginId = "InvalidPlugin"
                            }
                        }
                    }
                """.trimIndent()

            val service = createServiceWithConfig(config)

            val exception = shouldThrow<ConfigException> {
                service.loadAdminConfig(context)
            }

            exception.message shouldContain "'invalidReporter'"
        }

        "throw an exception if the scanner config is invalid" {
            val config = """
                    scanner {
                        sourceCodeOrigins = []
                    }
                """.trimIndent()

            val service = createServiceWithConfig(config)

            val exception = shouldThrow<ConfigException> {
                service.loadAdminConfig(context)
            }

            exception.message shouldContain "'sourceCodeOrigins'"
        }

        "only validate the configuration if requested" {
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
                    }
                """.trimIndent()

            val service = createService {
                initAdminConfig(config)

                every { containsFile(any(), any()) } returns false
            }

            service.loadAdminConfig(context)
        }
    }

    "getRuleSet()" should {
        "return the a default rule set with the standard names from ORT" {
            val service = createServiceWithConfig("")

            with(service.loadAdminConfig(context).getRuleSet(null)) {
                copyrightGarbageFile shouldBe ORT_COPYRIGHT_GARBAGE_FILENAME
                licenseClassificationsFile shouldBe ORT_LICENSE_CLASSIFICATIONS_FILENAME
                resolutionsFile shouldBe ORT_RESOLUTIONS_FILENAME
                evaluatorRules shouldBe ORT_EVALUATOR_RULES_FILENAME
            }
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

            with(service.loadAdminConfig(context).getRuleSet(null)) {
                copyrightGarbageFile shouldBe "testCopyrightGarbageFile"
                licenseClassificationsFile shouldBe "testLicenseClassificationsFile"
                resolutionsFile shouldBe "testResolutionsFile"
                evaluatorRules shouldBe "testEvaluatorRules"
            }
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

            with(service.loadAdminConfig(context).getRuleSet("customRuleSet1")) {
                copyrightGarbageFile shouldBe "testCopyrightGarbageFile1"
                licenseClassificationsFile shouldBe "testLicenseClassificationsFile1"
                resolutionsFile shouldBe "testResolutionsFile1"
                evaluatorRules shouldBe "testEvaluatorRules1"
            }
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
                service.loadAdminConfig(context).getRuleSet(nonExistentRuleSetName)
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
            val adminConfig = service.loadAdminConfig(context)

            with(adminConfig.getRuleSet("customRuleSet1")) {
                copyrightGarbageFile shouldBe "testCopyrightGarbageFile1"
                licenseClassificationsFile shouldBe "defaultLicenseClassificationsFile"
                resolutionsFile shouldBe "defaultResolutionsFile"
                evaluatorRules shouldBe "defaultEvaluatorRules"
            }

            with(adminConfig.getRuleSet("customRuleSet2")) {
                copyrightGarbageFile shouldBe "defaultCopyrightGarbageFile"
                licenseClassificationsFile shouldBe "testLicenseClassificationsFile2"
                resolutionsFile shouldBe "testResolutionsFile2"
                evaluatorRules shouldBe "testEvaluatorRules2"
            }
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
            val adminConfig = service.loadAdminConfig(context)

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
            val adminConfig = service.loadAdminConfig(context)

            adminConfig.ruleSetNames should containExactlyInAnyOrder("customRuleSet1", "customRuleSet2")
        }
    }

    "scannerConfig" should {
        "return a default configuration if nothing is configured" {
            val ortScannerConfig = ScannerConfiguration()
            val service = createServiceWithConfig("")

            with(service.loadAdminConfig(context).scannerConfig) {
                detectedLicenseMappings shouldBe ortScannerConfig.detectedLicenseMapping
                ignorePatterns shouldBe ortScannerConfig.ignorePatterns
                sourceCodeOrigins should containExactly(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)
            }
        }

        "return a configuration with default settings for an empty scanner section" {
            val config = """
                    scanner {
                    }
                """.trimIndent()
            val ortScannerConfig = ScannerConfiguration()
            val service = createServiceWithConfig(config)

            with(service.loadAdminConfig(context).scannerConfig) {
                detectedLicenseMappings shouldBe ortScannerConfig.detectedLicenseMapping
                ignorePatterns shouldBe ortScannerConfig.ignorePatterns
                sourceCodeOrigins should containExactly(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)
            }
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

            with(service.loadAdminConfig(context).scannerConfig) {
                detectedLicenseMappings shouldContainExactly mapOf(
                    "detectedLicense1" to "spdxLicense1",
                    "detectedLicense2" to "spdxLicense2"
                )
                ignorePatterns should containExactly("ignorePattern1", "ignorePattern2")
                sourceCodeOrigins should containExactly(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)
            }
        }
    }

    "notifierConfig" should {
        "return a default configuration if nothing is configured" {
            val service = createServiceWithConfig("")

            val notifierConfig = service.loadAdminConfig(context).notifierConfig

            notifierConfig shouldBe NotifierConfig()
        }

        "return a configuration with default settings for an empty notifier section" {
            val config = """
                    notifier {
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val notifierConfig = service.loadAdminConfig(context).notifierConfig

            notifierConfig shouldBe NotifierConfig()
        }

        "parse the notifier section from the config file" {
            val config = """
                    notifier {
                      notifierRules = "testNotifierRules"
                      mail {
                        host = "smtp.example.com"
                        port = 785
                        username = "mailUser"
                        password = "mailPassword"
                        fromAddress = "notifier@ort-server.example.com"
                        ssl = false
                      }
                      jira {
                        url = "https://jira.example.com"
                        username = "jiraUser"
                        password = "jiraPassword"
                      }
                      disableMailNotifications = true
                      disableJiraNotifications = true
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val notifierConfig = service.loadAdminConfig(context).notifierConfig

            notifierConfig.mail.shouldNotBeNull {
                hostName shouldBe "smtp.example.com"
                port shouldBe 785
                username shouldBe "mailUser"
                password shouldBe "mailPassword"
                useSsl shouldBe false
                fromAddress shouldBe "notifier@ort-server.example.com"
            }

            notifierConfig.jira.shouldNotBeNull {
                serverUrl shouldBe "https://jira.example.com"
                username shouldBe "jiraUser"
                password shouldBe "jiraPassword"
            }
        }

        "use default values for unspecified mail server properties" {
            val config = """
                    notifier {
                      mail {
                        fromAddress = "notifier@ort-server.example.com"
                      }
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val notifierConfig = service.loadAdminConfig(context).notifierConfig

            notifierConfig.mail.shouldNotBeNull {
                hostName shouldBe "localhost"
                port shouldBe 587
                username shouldBe ""
                password shouldBe ""
                useSsl shouldBe true
                fromAddress shouldBe "notifier@ort-server.example.com"
            }
        }
    }

    "reporterConfig" should {
        "return a default configuration if nothing is configured" {
            val service = createServiceWithConfig("")

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.howToFixTextProviderFile shouldBe ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME
            reporterConfig.customLicenseTextDir should beNull()
            reporterConfig shouldBe ReporterConfig()
            reporterConfig.reportDefinitionNames should containAll("WebApp", "PdfTemplate")
        }

        "use default values for unspecified properties" {
            val config = """
                    reporter {
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.howToFixTextProviderFile shouldBe ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME
            reporterConfig.customLicenseTextDir should beNull()
            reporterConfig.reportDefinitionNames should containAll("WebApp", "PdfTemplate")
        }

        "parse simple properties from the reporter section of the config file" {
            val config = """
                    reporter {
                      howToFixTextProviderFile = "testHowToFixTextProviderFile"
                      customLicenseTextDir = "testCustomLicenseTextDir"
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.howToFixTextProviderFile shouldBe "testHowToFixTextProviderFile"
            reporterConfig.customLicenseTextDir shouldBe "testCustomLicenseTextDir"
        }

        "parse report definitions from the reporter section of the config file" {
            val config = """
                    reporter {
                      reports {
                        disclosurePdf {
                          pluginId = "PdfTemplate"
                          assetFiles = [
                            {
                              sourcePath = "reporter/template/logo.png"
                              targetFolder = "images"
                              targetName = "report-logo.png"
                            },
                            {
                              sourcePath = "reporter/template/title.ttf"
                              targetFolder = "fonts"
                              targetName = "main-font.ftt"
                            }
                          ]
                          assetDirectories = [
                            {
                              sourcePath = "reporter/template/assets-files"
                              targetFolder = "assets"
                              targetName = "files"
                            },
                            {
                              sourcePath = "reporter/template/other-assets-files/"
                              targetFolder = "other-assets"
                              targetName = "more-files"
                            }
                          ]
                          nameMapping {
                            namePrefix = "disclosure-"
                            startIndex = 0
                            alwaysAppendIndex = true
                          }
                        }
                      }
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.getReportDefinition("disclosurePdf") shouldNotBeNull {
                pluginId shouldBe "PdfTemplate"
                assetFiles should containExactly(
                    ReporterAsset("reporter/template/logo.png", "images", "report-logo.png"),
                    ReporterAsset("reporter/template/title.ttf", "fonts", "main-font.ftt")
                )
                assetDirectories should containExactly(
                    ReporterAsset("reporter/template/assets-files/", "assets", "files"),
                    ReporterAsset("reporter/template/other-assets-files/", "other-assets", "more-files")
                )
                nameMapping shouldNotBeNull {
                    namePrefix shouldBe "disclosure-"
                    startIndex shouldBe 0
                    alwaysAppendIndex shouldBe true
                }
            }

            reporterConfig.reportDefinitionNames shouldContain "disclosurePdf"
        }

        "handle optional properties in report definitions" {
            val config = """
                    reporter {
                      reports {
                        disclosurePdf {
                          pluginId = "PdfTemplate"
                        }
                      }
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.getReportDefinition("DISCLOSUREPDF") shouldNotBeNull {
                pluginId shouldBe "PdfTemplate"
                assetFiles should beEmpty()
                assetDirectories should beEmpty()
                nameMapping should beNull()
            }
        }

        "handle optional properties in a reporter asset" {
            val config = """
                    reporter {
                      reports {
                        disclosurePdf {
                          pluginId = "PdfTemplate"
                          assetFiles = [
                            {
                              sourcePath = "reporter/template/logo.png"
                              targetName = "report-logo.png"
                            },
                            {
                              sourcePath = "reporter/template/title.ttf"
                              targetFolder = "fonts"
                            }
                          ]
                        }
                      }
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.getReportDefinition("disclosurepdf") shouldNotBeNull {
                assetFiles should containExactly(
                    ReporterAsset("reporter/template/logo.png", null, "report-logo.png"),
                    ReporterAsset("reporter/template/title.ttf", "fonts", null)
                )
            }
        }

        "handle optional properties in a name mapping" {
            val config = """
                    reporter {
                      reports {
                        disclosurePdf {
                          pluginId = "PdfTemplate"
                          nameMapping {
                            namePrefix = "disclosure-"
                          }
                        }
                      }
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.getReportDefinition("disclosurePdf") shouldNotBeNull {
                nameMapping shouldNotBeNull {
                    namePrefix shouldBe "disclosure-"
                    startIndex shouldBe 1
                    alwaysAppendIndex shouldBe false
                }
            }
        }

        "support asset definitions on top-level of the reporter section" {
            val config = """
                    reporter {
                      assets {
                        pdfAssets: [
                          {
                            sourcePath = "reporter/template/logo.png"
                            targetFolder = "images"
                            targetName = "report-logo.png"
                          },
                          {
                            sourcePath = "reporter/template/title.ttf"
                            targetFolder = "fonts"
                            targetName = "main-font.ftt"
                          }
                        ]
                        themeAssets: [
                          {
                            sourcePath = "reporter/template/themes"
                            targetFolder = "assets"
                            targetName = "themes"
                          }
                        ]
                        layoutAssets: [
                          {
                            sourcePath = "reporter/layout/tiles/"
                          }
                        ]
                        specialAssets: [
                          {
                            sourcePath = "special/layout/foo"
                          },
                          {
                            sourcePath = "special/layout/bar"
                          }
                        ]
                      }
                      reports {
                        disclosurePdf {
                          pluginId = "PdfTemplate"
                          assetFilesRefs = [ "pdfAssets" ]
                          assetDirectories = [
                            {
                              sourcePath = "reporter/template/assets-files"
                              targetFolder = "assets"
                              targetName = "files"
                            }
                          ]
                          assetDirectoriesRefs = [ "themeAssets", "layoutAssets" ]
                        }
                      }
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.getReportDefinition("disclosurePdf") shouldNotBeNull {
                pluginId shouldBe "PdfTemplate"
                assetFiles should containExactly(
                    ReporterAsset("reporter/template/logo.png", "images", "report-logo.png"),
                    ReporterAsset("reporter/template/title.ttf", "fonts", "main-font.ftt")
                )
                assetDirectories should containExactlyInAnyOrder(
                    ReporterAsset("reporter/template/assets-files/", "assets", "files"),
                    ReporterAsset("reporter/template/themes/", "assets", "themes"),
                    ReporterAsset("reporter/layout/tiles/")
                )
            }

            reporterConfig.globalAssets.keys should containExactlyInAnyOrder(
                "pdfAssets", "themeAssets", "layoutAssets", "specialAssets"
            )
            reporterConfig.globalAssets["specialAssets"].orEmpty() should containExactly(
                ReporterAsset("special/layout/foo"),
                ReporterAsset("special/layout/bar")
            )
        }

        "generate default report definitions for unreferenced reporter plugins" {
            val config = """
                    reporter {
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.getReportDefinition("HtmlTemplate") shouldNotBeNull {
                pluginId shouldBe "HtmlTemplate"
                assetFiles should beEmpty()
                assetDirectories should beEmpty()
                nameMapping should beNull()
            }
        }

        "allow overriding a report definition for a reporter plugin even if the case does not match" {
            val config = """
                    reporter {
                      reports {
                        disclosurePdf {
                          pluginId = "pdftemplate"
                          nameMapping {
                            namePrefix = "disclosure-"
                          }
                        }
                      }
                    }
                """.trimIndent()
            val service = createServiceWithConfig(config)

            val reporterConfig = service.loadAdminConfig(context).reporterConfig

            reporterConfig.getReportDefinition("disclosurePdf") shouldNotBeNull {
                nameMapping shouldNotBeNull {
                    namePrefix shouldBe "disclosure-"
                }
            }
        }
    }

    "mavenCentralMirror" should {
        "return null if no mirror is configured" {
            val service = createServiceWithConfig("")

            service.loadAdminConfig(context).mavenCentralMirror should beNull()
        }

        "return the configured Maven Central mirror" {
            val config = """
                mavenCentralMirror {
                    id = "testId"
                    name = "testName"
                    url = "https://test.url"
                    mirrorOf = "testMirrorOf"
                    username = "testUsername"
                    password = "testPassword"
                }
            """.trimIndent()

            val service = createServiceWithConfig(config)
            val mirror = service.loadAdminConfig(context).mavenCentralMirror

            mirror.shouldNotBeNull {
                id shouldBe "testId"
                name shouldBe "testName"
                url shouldBe "https://test.url"
                mirrorOf shouldBe "testMirrorOf"
                usernameSecret shouldBe "testUsername"
                passwordSecret shouldBe "testPassword"
            }
        }

        "return the configured Maven Central mirror without credentials" {
            val config = """
                mavenCentralMirror {
                    id = "testId"
                    name = "testName"
                    url = "https://test.url"
                    mirrorOf = "testMirrorOf"
                }
            """.trimIndent()
            val service = createServiceWithConfig(config)

            val mirror = service.loadAdminConfig(context).mavenCentralMirror

            mirror.shouldNotBeNull {
                id shouldBe "testId"
                name shouldBe "testName"
                url shouldBe "https://test.url"
                mirrorOf shouldBe "testMirrorOf"
                usernameSecret should beNull()
                passwordSecret should beNull()
            }
        }
    }

    "validateConfigFiles" should {
        "not return an issue if a default rule set config file is missing" {
            val config = AdminConfig()

            val service = createService {
                every { containsFile(context, Path(RuleSet.DEFAULT_COPYRIGHT_GARBAGE_FILE)) } returns false
            }

            service.validateConfigFiles(context, config) should beEmpty()
        }

        "return an issue if non-default rule set config files are missing" {
            val config = AdminConfig(
                defaultRuleSet = RuleSet(
                    copyrightGarbageFile = "non-default-garbage.yml",
                    licenseClassificationsFile = "non-default-license-classifications.yml"
                ),
                ruleSets = mapOf(
                    "first" to RuleSetTemplate(
                        resolutionsFile = "non-default-resolutions.yml",
                        evaluatorRules = "non-default.evaluator.rules.kts"
                    )
                )
            )

            val service = createService {
                every { containsFile(context, Path("non-default-garbage.yml")) } returns false
                every { containsFile(context, Path("non-default.evaluator.rules.kts")) } returns false
            }

            service.validateConfigFiles(context, config).shouldBeSingleton {
                it shouldContain "non-default-garbage.yml"
                it shouldContain "non-default.evaluator.rules.kts"
            }
        }

        "not return an issue if a default reporter config file is missing" {
            val config = AdminConfig()

            val service = createService {
                every { containsFile(context, Path(ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME)) } returns false
            }

            service.validateConfigFiles(context, config) should beEmpty()
        }

        "return an issue if non-default reporter config files are missing" {
            val config = AdminConfig(
                reporterConfig = ReporterConfig(
                    howToFixTextProviderFile = "non-default.how-to-fix-text-provider.kts",
                    customLicenseTextDir = "non-default-custom-license-text-dir"
                )
            )

            val service = createService {
                every { containsFile(context, Path("non-default.how-to-fix-text-provider.kts")) } returns false
                every { containsFile(context, Path("non-default-custom-license-text-dir")) } returns false
            }

            service.validateConfigFiles(context, config).shouldBeSingleton {
                it shouldContain "non-default.how-to-fix-text-provider.kts"
                it shouldContain "non-default-custom-license-text-dir"
            }
        }

        "return an issue if global asset files are missing" {
            val config = AdminConfig(
                reporterConfig = ReporterConfig(
                    globalAssets = mapOf(
                        "first" to listOf(
                            ReporterAsset(sourcePath = "existing-file-1"),
                            ReporterAsset(sourcePath = "missing-file-1")
                        ),
                        "second" to listOf(
                            ReporterAsset(sourcePath = "existing-file-2"),
                            ReporterAsset(sourcePath = "missing-file-2")
                        )
                    )
                )
            )

            val service = createService {
                every { containsFile(context, Path("missing-file-1")) } returns false
                every { containsFile(context, Path("missing-file-2")) } returns false
            }

            service.validateConfigFiles(context, config).shouldBeSingleton {
                it shouldContain "missing-file-1"
                it shouldContain "missing-file-2"
            }
        }

        "return an issue if report definition asset files are missing" {
            val config = AdminConfig(
                reporterConfig = ReporterConfig(
                    reportDefinitionsMap = mapOf(
                        "first" to ReportDefinitionTemplate(
                            pluginId = "PdfTemplate",
                            assetFiles = listOf(
                                ReporterAsset(sourcePath = "existing-file-1"),
                                ReporterAsset(sourcePath = "missing-file-1")
                            ),
                            assetDirectories = listOf(
                                ReporterAsset(sourcePath = "existing-dir-1/"),
                                ReporterAsset(sourcePath = "missing-dir-1/")
                            )
                        ),
                        "second" to ReportDefinitionTemplate(
                            pluginId = "PdfTemplate",
                            assetFiles = listOf(
                                ReporterAsset(sourcePath = "existing-file-2"),
                                ReporterAsset(sourcePath = "missing-file-2")
                            ),
                            assetDirectories = listOf(
                                ReporterAsset(sourcePath = "existing-dir-2/"),
                                ReporterAsset(sourcePath = "missing-dir-2/")
                            )
                        )
                    )
                )
            )

            val service = createService {
                every { containsFile(context, Path("missing-file-1")) } returns false
                every { containsFile(context, Path("missing-file-2")) } returns false
                every { containsFile(context, Path("missing-dir-1/")) } returns false
                every { containsFile(context, Path("missing-dir-2/")) } returns false
            }

            service.validateConfigFiles(context, config).shouldBeSingleton {
                it shouldContain "missing-file-1"
                it shouldContain "missing-file-2"
                it shouldContain "missing-dir-1/"
                it shouldContain "missing-dir-2/"
            }
        }

        "return no issue if all non-default config files and assets can be found" {
            val config = AdminConfig(
                defaultRuleSet = RuleSet(copyrightGarbageFile = "non-default-garbage.yml"),
                ruleSets = mapOf(
                    "first" to RuleSetTemplate(licenseClassificationsFile = "non-default-license-classifications.yml")
                ),
                reporterConfig = ReporterConfig(
                    howToFixTextProviderFile = "non-default.how-to-fix-text-provider.kts",
                    globalAssets = mapOf(
                        "first" to listOf(ReporterAsset("asset-file-1"))
                    ),
                    reportDefinitionsMap = mapOf(
                        "first" to ReportDefinitionTemplate(
                            pluginId = "PdfTemplate",
                            assetFiles = listOf(ReporterAsset("asset-file-2"))
                        )
                    )
                )
            )

            createService().validateConfigFiles(context, config) should beEmpty()
        }
    }
})

/** The context used by tests when querying the admin configuration. */
private val context = Context("testContext")

/** A path to the admin config used by tests per default. */
private const val ADMIN_CONFIG_PATH = "test-ort-server.conf"

/**
 * Create an [AdminConfigService] instance for testing that uses a spy [ConfigManager] returning the given
 * [adminConfigPath]. The given [block] can be used to further configure the [ConfigManager] spy. Return a [Pair]
 * with the service and the [ConfigManager] spy.
 */
private fun createServiceAndConfigManager(
    adminConfigPath: String? = ADMIN_CONFIG_PATH,
    block: ConfigManager.() -> Unit = {}
): Pair<AdminConfigService, ConfigManager> {
    val config = adminConfigPath?.let { ConfigFactory.parseMap(mapOf("adminConfigPath" to it)) }
        ?: ConfigFactory.empty()
    val configManager = spyk(ConfigManager.create(config)) {
        every { containsFile(any(), any()) } returns true
        block()
    }

    return AdminConfigService(configManager) to configManager
}

/**
 * Return an [AdminConfigService] instance for testing that uses a [ConfigManager] spy with the given
 * [adminConfigPath]. The given [block] can be used to further configure the [ConfigManager] spy.
 */
private fun createService(
    adminConfigPath: String? = ADMIN_CONFIG_PATH,
    block: ConfigManager.() -> Unit = {}
): AdminConfigService = createServiceAndConfigManager(adminConfigPath, block).first

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
