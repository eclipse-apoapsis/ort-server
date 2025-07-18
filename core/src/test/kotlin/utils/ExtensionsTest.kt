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

package org.eclipse.apoapsis.ortserver.core.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.should

import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.PluginConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType

class ExtensionsTest : WordSpec({
    "CreateOrtRun.getPluginConfigs()" should {
        "return an empty result when no plugins are configured" {
            val createOrtRun = CreateOrtRun(
                revision = "revision",
                jobConfigs = JobConfigurations()
            )

            createOrtRun.getPluginConfigs() should beEmpty()
        }

        "return all plugin configurations" {
            val createOrtRun = CreateOrtRun(
                revision = "revision",
                jobConfigs = JobConfigurations(
                    analyzer = AnalyzerJobConfiguration(
                        packageManagerOptions = mapOf(
                            "npm" to PackageManagerConfiguration(
                                options = mapOf("npmOption" to "npmValue")
                            )
                        ),
                        packageCurationProviders = listOf(
                            ProviderPluginConfiguration(
                                type = "ort-config",
                                options = mapOf("ortConfigOption" to "ortConfigValue"),
                                secrets = mapOf("ortConfigSecret" to "ortConfigSecretValue")
                            )
                        )
                    ),
                    advisor = AdvisorJobConfiguration(
                        config = mapOf(
                            "OSSIndex" to PluginConfig(
                                options = mapOf("ossIndexOption" to "ossIndexValue"),
                                secrets = mapOf("ossIndexSecret" to "ossIndexSecretValue")
                            )
                        )
                    ),
                    scanner = ScannerJobConfiguration(
                        config = mapOf(
                            "ScanCode" to PluginConfig(
                                options = mapOf("scanCodeOption" to "scanCodeValue"),
                                secrets = mapOf("scanCodeSecret" to "scanCodeSecretValue")
                            )
                        )
                    ),
                    evaluator = EvaluatorJobConfiguration(
                        packageConfigurationProviders = listOf(
                            ProviderPluginConfiguration(
                                type = "ort-config",
                                options = mapOf("ortConfigOption2" to "ortConfigValue2"),
                                secrets = mapOf("ortConfigSecret2" to "ortConfigSecretValue2")
                            )
                        )
                    ),
                    reporter = ReporterJobConfiguration(
                        config = mapOf(
                            "WebApp" to PluginConfig(
                                options = mapOf("webAppOption" to "webAppValue"),
                                secrets = mapOf("webAppSecret" to "webAppSecretValue")
                            )
                        )
                    )
                )
            )

            createOrtRun.getPluginConfigs() should containExactly(
                PluginType.PACKAGE_MANAGER to mapOf(
                    "npm" to PluginConfig(
                        options = mapOf("npmOption" to "npmValue"),
                        secrets = emptyMap()
                    )
                ),
                PluginType.PACKAGE_CURATION_PROVIDER to mapOf(
                    "ort-config" to PluginConfig(
                        options = mapOf("ortConfigOption" to "ortConfigValue"),
                        secrets = mapOf("ortConfigSecret" to "ortConfigSecretValue")
                    )
                ),
                PluginType.ADVISOR to mapOf(
                    "OSSIndex" to PluginConfig(
                        options = mapOf("ossIndexOption" to "ossIndexValue"),
                        secrets = mapOf("ossIndexSecret" to "ossIndexSecretValue")
                    )
                ),
                PluginType.SCANNER to mapOf(
                    "ScanCode" to PluginConfig(
                        options = mapOf("scanCodeOption" to "scanCodeValue"),
                        secrets = mapOf("scanCodeSecret" to "scanCodeSecretValue")
                    )
                ),
                PluginType.PACKAGE_CONFIGURATION_PROVIDER to mapOf(
                    "ort-config" to PluginConfig(
                        options = mapOf("ortConfigOption2" to "ortConfigValue2"),
                        secrets = mapOf("ortConfigSecret2" to "ortConfigSecretValue2")
                    )
                ),
                PluginType.REPORTER to mapOf(
                    "WebApp" to PluginConfig(
                        options = mapOf("webAppOption" to "webAppValue"),
                        secrets = mapOf("webAppSecret" to "webAppSecretValue")
                    )
                )
            )
        }

        "return empty configuration for plugins which are enabled but have no plugin config" {
            val createOrtRun = CreateOrtRun(
                revision = "revision",
                jobConfigs = JobConfigurations(
                    analyzer = AnalyzerJobConfiguration(
                        enabledPackageManagers = listOf("npm"),
                        disabledPackageManagers = listOf("yarn")
                    ),
                    advisor = AdvisorJobConfiguration(
                        advisors = listOf("OSSIndex")
                    ),
                    scanner = ScannerJobConfiguration(
                        scanners = listOf("ScanCode"),
                        projectScanners = listOf("FossID")
                    ),
                    reporter = ReporterJobConfiguration(
                        formats = listOf("WebApp")
                    )
                )
            )

            createOrtRun.getPluginConfigs() should containExactly(
                PluginType.PACKAGE_MANAGER to mapOf(
                    "npm" to PluginConfig(emptyMap(), emptyMap())
                ),
                PluginType.ADVISOR to mapOf(
                    "OSSIndex" to PluginConfig(emptyMap(), emptyMap())
                ),
                PluginType.SCANNER to mapOf(
                    "ScanCode" to PluginConfig(emptyMap(), emptyMap()),
                    "FossID" to PluginConfig(emptyMap(), emptyMap())
                ),
                PluginType.REPORTER to mapOf(
                    "WebApp" to PluginConfig(emptyMap(), emptyMap())
                )
            )
        }

        "ignore package configuration providers from reporter job if evaluator job is configured" {
            val createOrtRun = CreateOrtRun(
                revision = "revision",
                jobConfigs = JobConfigurations(
                    evaluator = EvaluatorJobConfiguration(
                        packageConfigurationProviders = listOf(
                            ProviderPluginConfiguration(
                                type = "ort-config",
                                options = mapOf("ortConfigOption" to "ortConfigValue"),
                                secrets = mapOf("ortConfigSecret" to "ortConfigSecretValue")
                            )
                        )
                    ),
                    reporter = ReporterJobConfiguration(
                        packageConfigurationProviders = listOf(
                            ProviderPluginConfiguration(
                                type = "ort-config",
                                options = mapOf("reporterOrtConfigOption" to "reporterOrtConfigValue"),
                                secrets = mapOf("reporterOrtConfigSecret" to "reporterOrtConfigSecretValue")
                            )
                        )
                    )
                )
            )

            createOrtRun.getPluginConfigs() should containExactly(
                PluginType.PACKAGE_CONFIGURATION_PROVIDER to mapOf(
                    "ort-config" to PluginConfig(
                        options = mapOf("ortConfigOption" to "ortConfigValue"),
                        secrets = mapOf("ortConfigSecret" to "ortConfigSecretValue")
                    )
                )
            )
        }

        "return package configuration providers from reporter job if evaluator job is not configured" {
            val createOrtRun = CreateOrtRun(
                revision = "revision",
                jobConfigs = JobConfigurations(
                    reporter = ReporterJobConfiguration(
                        packageConfigurationProviders = listOf(
                            ProviderPluginConfiguration(
                                type = "ort-config",
                                options = mapOf("ortConfigOption" to "ortConfigValue"),
                                secrets = mapOf("ortConfigSecret" to "ortConfigSecretValue")
                            )
                        )
                    )
                )
            )

            createOrtRun.getPluginConfigs() should containExactly(
                PluginType.PACKAGE_CONFIGURATION_PROVIDER to mapOf(
                    "ort-config" to PluginConfig(
                        options = mapOf("ortConfigOption" to "ortConfigValue"),
                        secrets = mapOf("ortConfigSecret" to "ortConfigSecretValue")
                    )
                )
            )
        }
    }
})
