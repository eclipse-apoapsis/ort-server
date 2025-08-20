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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.PluginConfig

class ReporterConfigTest : WordSpec({
    "pluginOptionForDefinition()" should {
        "return null if the definition does not exist" {
            reporterConfig.pluginOptionsForDefinition(
                "non-existing-definition",
                mapOf(PLUGIN_ID to templatePluginOptions)
            ) shouldBe null
        }

        "return null if no configuration is found" {
            reporterConfig.pluginOptionsForDefinition(
                REPORT_DEFINITION_NAME,
                emptyMap()
            ) shouldBe null
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
    }
})

private const val REPORT_DEFINITION_NAME = "disclosure-document"
private const val PLUGIN_ID = "PdfTemplateReporter"

/** A test [ReporterConfig] with a report definition that is used in the tests. */
private val reporterConfig = ReporterConfig(
    howToFixTextProviderFile = "how-to-fix.txt",
    reportDefinitionsMap = mapOf(
        REPORT_DEFINITION_NAME to ReportDefinition(pluginId = PLUGIN_ID)
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
