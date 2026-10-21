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

package org.eclipse.apoapsis.ortserver.core.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.PluginConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.PostRepositoryRun
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJobConfiguration

class PluginConfigUtilsTest : WordSpec({
    "PostRepositoryRun.getAmbiguousPluginConfigErrors()" should {
        "return no errors if all plugin config keys are distinct" {
            val config = PluginConfig(options = emptyMap(), secrets = emptyMap())
            val createOrtRun = PostRepositoryRun(
                revision = "revision",
                jobConfigs = JobConfigurations(
                    advisor = AdvisorJobConfiguration(config = mapOf("OSV" to config, "VulnerableCode" to config)),
                    scanner = ScannerJobConfiguration(config = mapOf("ScanCode" to config))
                )
            )

            createOrtRun.getAmbiguousPluginConfigErrors().shouldBeEmpty()
        }

        "return errors for plugin config keys that differ only in case" {
            val config = PluginConfig(options = emptyMap(), secrets = emptyMap())
            val createOrtRun = PostRepositoryRun(
                revision = "revision",
                jobConfigs = JobConfigurations(
                    analyzer = AnalyzerJobConfiguration(
                        packageManagerOptions = mapOf(
                            "NPM" to PackageManagerConfiguration(),
                            "npm" to PackageManagerConfiguration()
                        )
                    ),
                    advisor = AdvisorJobConfiguration(config = mapOf("OSV" to config, "osv" to config)),
                    scanner = ScannerJobConfiguration(config = mapOf("ScanCode" to config, "SCANCODE" to config)),
                    reporter = ReporterJobConfiguration(config = mapOf("WebApp" to config, "webapp" to config))
                )
            )

            createOrtRun.getAmbiguousPluginConfigErrors() shouldContainExactlyInAnyOrder listOf(
                "The configuration for the plugin with type 'ADVISOR' and ID 'OSV' is defined multiple times with " +
                        "different spellings of the plugin ID: 'OSV', 'osv'.",
                "The configuration for the plugin with type 'PACKAGE_MANAGER' and ID 'NPM' is defined multiple " +
                        "times with different spellings of the plugin ID: 'NPM', 'npm'.",
                "The configuration for the plugin with type 'REPORTER' and ID 'WebApp' is defined multiple times " +
                        "with different spellings of the plugin ID: 'WebApp', 'webapp'.",
                "The configuration for the plugin with type 'SCANNER' and ID 'ScanCode' is defined multiple times " +
                        "with different spellings of the plugin ID: 'ScanCode', 'SCANCODE'."
            )
        }

        "return errors for plugin config keys that only match ignoring case due to special Unicode characters" {
            val config = PluginConfig(options = emptyMap(), secrets = emptyMap())
            val createOrtRun = PostRepositoryRun(
                revision = "revision",
                jobConfigs = JobConfigurations(
                    // Use a long s (U+017F), a dotless i (U+0131), and a dotted capital I (U+0130), which only match
                    // their ASCII counterparts when ignoring case, but not when comparing lowercase strings.
                    advisor = AdvisorJobConfiguration(
                        config = mapOf("OSV" to config, "O\u017FV" to config)
                    ),
                    scanner = ScannerJobConfiguration(
                        config = mapOf("Licensee" to config, "L\u0131censee" to config, "L\u0130censee" to config)
                    )
                )
            )

            createOrtRun.getAmbiguousPluginConfigErrors() shouldContainExactlyInAnyOrder listOf(
                "The configuration for the plugin with type 'ADVISOR' and ID 'OSV' is defined multiple times with " +
                        "different spellings of the plugin ID: 'OSV', 'O\u017FV'.",
                "The configuration for the plugin with type 'SCANNER' and ID 'Licensee' is defined multiple times " +
                        "with different spellings of the plugin ID: 'Licensee', 'L\u0131censee', 'L\u0130censee'."
            )
        }
    }
})
