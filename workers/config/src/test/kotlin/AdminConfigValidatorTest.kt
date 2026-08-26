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

package org.eclipse.apoapsis.ortserver.workers.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfig
import org.eclipse.apoapsis.ortserver.components.adminconfig.ReportDefinitionTemplate
import org.eclipse.apoapsis.ortserver.components.adminconfig.ReporterConfig
import org.eclipse.apoapsis.ortserver.components.adminconfig.RuleSetTemplate
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration

class AdminConfigValidatorTest : WordSpec({
    "validate" should {
        "create an issue for an invalid rule set" {
            val adminConfig = AdminConfig(ruleSets = mapOf("valid-rule-set" to RuleSetTemplate()))

            val jobConfigs = JobConfigurations(ruleSet = "invalid-rule-set")

            AdminConfigValidator.validate(adminConfig, jobConfigs).shouldBeSingleton {
                it.message shouldContain "invalid-rule-set"
            }
        }

        "create an issue for each invalid report format" {
            val adminConfig = AdminConfig()

            val jobConfigs = JobConfigurations(
                reporter = ReporterJobConfiguration(formats = listOf("invalid-format-1", "invalid-format-2"))
            )

            val issues = AdminConfigValidator.validate(adminConfig, jobConfigs)

            issues.size shouldBe 2
            issues.shouldHaveSingleElement { "invalid-format-1" in it.message }
            issues.shouldHaveSingleElement { "invalid-format-2" in it.message }
        }

        "create an issue for each invalid asset files group" {
            val adminConfig = AdminConfig(
                reporterConfig = ReporterConfig(
                    globalAssets = mapOf("valid-asset-group" to emptyList())
                )
            )

            val jobConfigs = JobConfigurations(
                reporter = ReporterJobConfiguration(
                    assetFilesGroups = listOf("invalid-group-1", "invalid-group-2")
                )
            )

            val issues = AdminConfigValidator.validate(adminConfig, jobConfigs)

            issues.size shouldBe 2
            issues.shouldHaveSingleElement { "invalid-group-1" in it.message }
            issues.shouldHaveSingleElement { "invalid-group-2" in it.message }
        }

        "create an issue for each invalid asset directories group" {
            val adminConfig = AdminConfig(
                reporterConfig = ReporterConfig(
                    globalAssets = mapOf("valid-asset-group" to emptyList())
                )
            )

            val jobConfigs = JobConfigurations(
                reporter = ReporterJobConfiguration(
                    assetDirectoriesGroups = listOf("invalid-group-1", "invalid-group-2")
                )
            )

            val issues = AdminConfigValidator.validate(adminConfig, jobConfigs)

            issues.size shouldBe 2
            issues.shouldHaveSingleElement { "invalid-group-1" in it.message }
            issues.shouldHaveSingleElement { "invalid-group-2" in it.message }
        }

        "create no issue if the config is valid" {
            val adminConfig = AdminConfig(
                ruleSets = mapOf("valid-rule-set" to RuleSetTemplate()),
                reporterConfig = ReporterConfig(
                    globalAssets = mapOf("valid-asset-group" to emptyList()),
                    reportDefinitionsMap = mapOf("valid-format" to ReportDefinitionTemplate(pluginId = "PdfTemplate"))
                )
            )

            val jobConfigs = JobConfigurations(
                ruleSet = "valid-rule-set",
                reporter = ReporterJobConfiguration(
                    formats = listOf("valid-format"),
                    assetFilesGroups = listOf("valid-asset-group"),
                    assetDirectoriesGroups = listOf("valid-asset-group")
                )
            )

            AdminConfigValidator.validate(adminConfig, jobConfigs) should beEmpty()
        }
    }
})
