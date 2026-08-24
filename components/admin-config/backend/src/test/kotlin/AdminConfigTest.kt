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

package org.eclipse.apoapsis.ortserver.components.adminconfig

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

class AdminConfigTest : WordSpec({
    "getNonDefaultConfigFiles()" should {
        "be empty if no config files are configured" {
            AdminConfig().getNonDefaultConfigFiles() should beEmpty()
        }

        "contain all non-default files" {
            AdminConfig(
                defaultRuleSet = RuleSet(copyrightGarbageFile = "non-default-1"),
                ruleSets = mapOf(
                    "first" to RuleSetTemplate(licenseClassificationsFile = "non-default-2"),
                    "second" to RuleSetTemplate(resolutionsFile = "non-default-3"),
                    "third" to RuleSetTemplate(evaluatorRules = "non-default-4")
                ),
                reporterConfig = ReporterConfig(
                    howToFixTextProviderFile = "non-default-5"
                )
            ).getNonDefaultConfigFiles() should containExactlyInAnyOrder(
                "non-default-1", "non-default-2", "non-default-3", "non-default-4", "non-default-5"
            )
        }
    }

    "validate()" should {
        "contain issues from the scanner config" {
            AdminConfig(scannerConfig = ScannerConfig(sourceCodeOrigins = emptyList())).validate() should haveSize(1)
        }

        "contain issues from the reporter config" {
            AdminConfig(
                reporterConfig = ReporterConfig(
                    reportDefinitionsMap = mapOf(
                        "first" to ReportDefinitionTemplate(pluginId = "invalid-plugin")
                    )
                )
            ).validate() should haveSize(1)
        }

        "be empty if the config is valid" {
            AdminConfig().validate() should beEmpty()
        }
    }
})
