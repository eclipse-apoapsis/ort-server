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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.model.runs.Identifier

import org.ossreviewtoolkit.model.Identifier as OrtIdentifier

class UtilsTest : WordSpec({
    "getIdentifierToShortestPathsMap()" should {
        "extract the shortest path across scopes for each identifier" {
            val shortestPathsByScope: Map<String, Map<OrtIdentifier, List<OrtIdentifier>>> =
                mapOf(
                    "dependencies" to mapOf(
                        OrtIdentifier("NPM", "", "acorn", "1.0") to listOf(
                            OrtIdentifier("NPM", "", "react-scripts", "1.0"),
                            OrtIdentifier("NPM", "", "eslint", "1.0"),
                            OrtIdentifier("NPM", "", "espree", "1.0")
                        ),
                        OrtIdentifier("NPM", "", "cookie", "1.0") to listOf(
                            OrtIdentifier("NPM", "", "react-scripts", "1.0"),
                            OrtIdentifier("NPM", "", "webpack-dev-server", "1.0"),
                            OrtIdentifier("NPM", "", "express", "1.0")
                        )
                    ),
                    "devDependencies" to mapOf(
                        OrtIdentifier("NPM", "", "acorn", "1.0") to listOf(
                            OrtIdentifier("NPM", "", "eslint", "1.0"),
                            OrtIdentifier("NPM", "", "espree", "1.0")
                        )
                    )
                )

            val identifierToShortestPathMap = getIdentifierToShortestPathsMap(
                Identifier("NPM", "com.example", "example", "1.0"),
                shortestPathsByScope
            )

            identifierToShortestPathMap.size shouldBe 2

            val shortestPath1 = identifierToShortestPathMap[Identifier("NPM", "", "acorn", "1.0")]

            shortestPath1.shouldNotBeNull {
                scope shouldBe "devDependencies"
                path.size shouldBe 2
            }

            val shortestPath2 = identifierToShortestPathMap[Identifier("NPM", "", "cookie", "1.0")]

            shortestPath2.shouldNotBeNull {
                scope shouldBe "dependencies"
                path.size shouldBe 3
            }
        }
    }

    "getDefaultPackageManagers()" should {
        "return all enabled package managers" {
            val pluginService = mockk<PluginService> {
                every { getPlugins() } returns listOf(
                    PluginDescriptor("Maven", PluginType.PACKAGE_MANAGER, "Maven", "description", enabled = false),
                    PluginDescriptor("NPM", PluginType.PACKAGE_MANAGER, "NPM", "description", enabled = true),
                    PluginDescriptor("OSV", PluginType.ADVISOR, "OSV", "description", enabled = true)
                )
            }

            val defaultPackageManagers = getDefaultPackageManagers(pluginService)

            defaultPackageManagers should containExactlyInAnyOrder("NPM")
        }
    }
})
