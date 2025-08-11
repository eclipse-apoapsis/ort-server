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

package org.eclipse.apoapsis.ortserver.shared.plugininfo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.string.shouldContain

class PluginInfoTest : WordSpec({
    "pluginIds" should {
        "contain the IDs of ORT plugins" {
            val expectedPluginIds = setOf(
                TypedPluginId(
                    PluginId("ClearlyDefined"),
                    PluginType(
                        "org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory"
                    )
                ),
                TypedPluginId(
                    PluginId("Maven"),
                    PluginType("org.ossreviewtoolkit.analyzer.PackageManagerFactory")
                ),
                TypedPluginId(
                    PluginId("ORTConfig"),
                    PluginType(
                        "org.ossreviewtoolkit.plugins.packageconfigurationproviders.api." +
                                "PackageConfigurationProviderFactory"
                    )
                ),
                TypedPluginId(
                    PluginId("ScanCode"),
                    PluginType("org.ossreviewtoolkit.scanner.ScannerWrapperFactory")
                ),
                TypedPluginId(
                    PluginId("VulnerableCode"),
                    PluginType("org.ossreviewtoolkit.advisor.AdviceProviderFactory")
                ),
                TypedPluginId(
                    PluginId("WebApp"),
                    PluginType("org.ossreviewtoolkit.reporter.ReporterFactory")
                )
            )

            PluginInfo.pluginIds shouldContainAll expectedPluginIds
        }

        "contain the IDs of plugins defined in the ORT Server project" {
            val expectedPluginIds = setOf(
                TypedPluginId(
                    PluginId("Dir"),
                    PluginType(
                        "org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory"
                    )
                ),
                TypedPluginId(
                    PluginId("SourceCodeBundle"),
                    PluginType("org.ossreviewtoolkit.reporter.ReporterFactory")
                )
            )

            PluginInfo.pluginIds shouldContainAll expectedPluginIds
        }
    }

    "pluginTypes" should {
        "return a set with all known plugin types" {
            val expectedPluginTypes = setOf(
                PluginType("org.ossreviewtoolkit.advisor.AdviceProviderFactory"),
                PluginType("org.ossreviewtoolkit.analyzer.PackageManagerFactory"),
                PluginType(
                    "org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory"
                ),
                PluginType(
                    "org.ossreviewtoolkit.plugins.packageconfigurationproviders.api." +
                            "PackageConfigurationProviderFactory"
                ),
                PluginType("org.ossreviewtoolkit.reporter.ReporterFactory"),
                PluginType("org.ossreviewtoolkit.scanner.ScannerWrapperFactory")
            )

            PluginInfo.pluginTypes shouldContainExactlyInAnyOrder expectedPluginTypes
        }
    }

    "pluginsForType" should {
        "return the plugins of an existing type" {
            val type = PluginType("org.ossreviewtoolkit.advisor.AdviceProviderFactory")
            val expectedPluginIds = listOf(
                TypedPluginId(PluginId("OSV"), type),
                TypedPluginId(PluginId("VulnerableCode"), type)
            )

            PluginInfo.pluginsForType(type) shouldContainAll expectedPluginIds
        }

        "throw an exception for an unknown type" {
            val unknownType = PluginType("unknown.type")

            shouldThrow<NoSuchElementException> {
                PluginInfo.pluginsForType(unknownType)
            }.message shouldContain unknownType.type
        }
    }
})
