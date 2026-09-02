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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.eclipse.apoapsis.ortserver.model.PluginConfig

class ReporterConfigTest : WordSpec({
    "pluginOptionForDefinition()" should {
        "return null if the definition does not exist" {
            reporterConfig.pluginOptionsForDefinition(
                "non-existing-definition",
                mapOf(PLUGIN_ID to templatePluginOptions)
            ) should beNull()
        }

        "return null if no configuration is found" {
            reporterConfig.pluginOptionsForDefinition(
                REPORT_DEFINITION_NAME,
                emptyMap()
            ) should beNull()
        }

        "return the configuration for the reporter plugin" {
            reporterConfig.pluginOptionsForDefinition(
                REPORT_DEFINITION_NAME,
                mapOf(PLUGIN_ID to templatePluginOptions)
            ) shouldBe templatePluginOptions
        }

        "return the the configuration for the report definition" {
            reporterConfig.pluginOptionsForDefinition(
                REPORT_DEFINITION_NAME,
                mapOf("$PLUGIN_ID:$REPORT_DEFINITION_NAME" to templatePluginOptions)
            ) shouldBe templatePluginOptions
        }

        "use case-insensitive comparison for the report definition name" {
            reporterConfig.pluginOptionsForDefinition(
                REPORT_DEFINITION_NAME.uppercase(),
                mapOf("$PLUGIN_ID:$REPORT_DEFINITION_NAME" to templatePluginOptions)
            ) shouldBe templatePluginOptions
        }

        "use case-insensitive comparison for the plugin ID" {
            reporterConfig.pluginOptionsForDefinition(
                REPORT_DEFINITION_NAME,
                mapOf(PLUGIN_ID.lowercase() to templatePluginOptions)
            ) shouldBe templatePluginOptions
        }

        "merge the configurations from the plugin and the report definition" {
            val definitionConfig = PluginConfig(
                options = mapOf("mode" to "fast", "template" to "disclosure-document.ftl"),
                secrets = mapOf("testSecret1" to "overriddenSecretValue", "testSecret3" to "testSecretValue3")
            )

            reporterConfig.pluginOptionsForDefinition(
                REPORT_DEFINITION_NAME,
                mapOf(
                    PLUGIN_ID to templatePluginOptions,
                    "$PLUGIN_ID:$REPORT_DEFINITION_NAME" to definitionConfig
                )
            ) shouldNotBeNull {
                options["style"] shouldBe "nice"
                options["branding"] shouldBe "special"
                options["template"] shouldBe "disclosure-document.ftl"
                options["mode"] shouldBe "fast"

                secrets["testSecret1"] shouldBe "overriddenSecretValue"
                secrets["testSecret2"] shouldBe "testSecretValue2"
                secrets["testSecret3"] shouldBe "testSecretValue3"
            }
        }

        "use case-insensitive comparison when merging configurations" {
            val definitionConfig = PluginConfig(
                options = mapOf("mode" to "fast", "template" to "disclosure-document.ftl"),
                secrets = mapOf("testSecret1" to "overriddenSecretValue", "testSecret3" to "testSecretValue3")
            )

            reporterConfig.pluginOptionsForDefinition(
                REPORT_DEFINITION_NAME,
                mapOf(
                    PLUGIN_ID.lowercase() to templatePluginOptions,
                    "${PLUGIN_ID.lowercase()}:${REPORT_DEFINITION_NAME.uppercase()}" to definitionConfig
                )
            ) shouldNotBeNull {
                options["style"] shouldBe "nice"
                options["branding"] shouldBe "special"
                options["template"] shouldBe "disclosure-document.ftl"
                options["mode"] shouldBe "fast"

                secrets["testSecret1"] shouldBe "overriddenSecretValue"
                secrets["testSecret2"] shouldBe "testSecretValue2"
                secrets["testSecret3"] shouldBe "testSecretValue3"
            }
        }
    }

    "getAllAssets()" should {
        "be empty if no assets are configured" {
            ReporterConfig().getAllAssets() should beEmpty()
        }

        "contain all global assets" {
            ReporterConfig(
                globalAssets = mapOf(
                    "first" to listOf(
                        ReporterAsset("asset-1"),
                        ReporterAsset("asset-2")
                    ),
                    "second" to listOf(
                        ReporterAsset("asset-3"),
                        ReporterAsset("asset-4")
                    )
                )
            ).getAllAssets() should containExactlyInAnyOrder("asset-1", "asset-2", "asset-3", "asset-4")
        }

        "contain all assets from report definitions" {
            ReporterConfig(
                reportDefinitionsMap = mapOf(
                    "first" to ReportDefinitionTemplate(
                        pluginId = PLUGIN_ID,
                        assetFiles = listOf(
                            ReporterAsset("asset-file-1"),
                            ReporterAsset("asset-file-2")
                        ),
                        assetDirectories = listOf(
                            ReporterAsset("asset-dir-1/"),
                            ReporterAsset("asset-dir-2/")
                        )
                    ),
                    "second" to ReportDefinitionTemplate(
                        pluginId = PLUGIN_ID,
                        assetFiles = listOf(
                            ReporterAsset("asset-file-3"),
                            ReporterAsset("asset-file-4")
                        ),
                        assetDirectories = listOf(
                            ReporterAsset("asset-dir-3/"),
                            ReporterAsset("asset-dir-4/")
                        )
                    )
                )
            ).getAllAssets() should containExactlyInAnyOrder(
                "asset-file-1",
                "asset-file-2",
                "asset-file-3",
                "asset-file-4",
                "asset-dir-1/",
                "asset-dir-2/",
                "asset-dir-3/",
                "asset-dir-4/"
            )
        }
    }

    "getNonDefaultConfigFiles()" should {
        "be empty if only default files are used" {
            ReporterConfig().getNonDefaultConfigFiles() should beEmpty()
        }

        "contain all non-default files" {
            ReporterConfig(
                howToFixTextProviderFile = "non-default.how-to-fix-text-provider.kts",
                customLicenseTextDir = "customLicenseTextDir"
            ).getNonDefaultConfigFiles() should containExactlyInAnyOrder(
                "non-default.how-to-fix-text-provider.kts",
                "customLicenseTextDir"
            )
        }
    }

    "validate()" should {
        "return an issue if a report definition contains an invalid asset files reference" {
            ReporterConfig(
                reportDefinitionsMap = mapOf(
                    "definition" to ReportDefinitionTemplate(
                        pluginId = PLUGIN_ID,
                        assetFilesRefs = listOf("non-existing")
                    )
                ),
                globalAssets = mapOf("existing" to listOf(ReporterAsset(sourcePath = "path")))
            ).validate().shouldBeSingleton {
                it shouldContain "non-existing"
            }
        }

        "return an issue if a report definition contains an invalid asset directories reference" {
            ReporterConfig(
                reportDefinitionsMap = mapOf(
                    "definition" to ReportDefinitionTemplate(
                        pluginId = PLUGIN_ID,
                        assetDirectoriesRefs = listOf("non-existing")
                    )
                ),
                globalAssets = mapOf("existing" to listOf(ReporterAsset(sourcePath = "path")))
            ).validate().shouldBeSingleton {
                it shouldContain "non-existing"
            }
        }

        "return an issue if a report definition uses the name of a different existing reporter plugin" {
            ReporterConfig(
                reportDefinitionsMap = mapOf(PLUGIN_ID to ReportDefinitionTemplate(pluginId = "WebApp"))
            ).validate().shouldBeSingleton {
                it shouldContain PLUGIN_ID
            }
        }

        "not return an issue if a report definition uses the name of the plugin it references" {
            ReporterConfig(
                reportDefinitionsMap = mapOf(PLUGIN_ID to ReportDefinitionTemplate(pluginId = PLUGIN_ID))
            ).validate() should beEmpty()
        }

        "return an issue if a report definition references a non-existing plugin" {
            ReporterConfig(
                reportDefinitionsMap = mapOf("definition" to ReportDefinitionTemplate(pluginId = "non-existing"))
            ).validate().shouldBeSingleton {
                it shouldContain "non-existing"
            }
        }
    }
})

private const val REPORT_DEFINITION_NAME = "disclosure-document"
private const val PLUGIN_ID = "PdfTemplate"

/** A test [ReporterConfig] with a report definition that is used in the tests. */
private val reporterConfig = ReporterConfig(
    howToFixTextProviderFile = "how-to-fix.txt",
    reportDefinitionsMap = mapOf(
        REPORT_DEFINITION_NAME to ReportDefinitionTemplate(pluginId = PLUGIN_ID)
    )
)

/** A test [PluginConfig] with some options that allow testing the merging functionality. */
private val templatePluginOptions = PluginConfig(
    options = mapOf(
        "style" to "nice",
        "branding" to "special",
        "template" to "template.ftl"
    ),
    secrets = mapOf("testSecret1" to "testSecretValue1", "testSecret2" to "testSecretValue2")
)
