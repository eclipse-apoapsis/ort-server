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

package org.eclipse.apoapsis.ortserver.components.dependencygraph.routes

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraph
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphEdge
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphNode
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphProjectGroup
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphScope
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphs
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraph as ModelDependencyGraph
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphEdge as ModelDependencyGraphEdge
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphNode as ModelDependencyGraphNode
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphRoot as ModelDependencyGraphRoot
import org.eclipse.apoapsis.ortserver.model.runs.Identifier as ModelIdentifier

class GetDependencyGraphIntegrationTest : DependencyGraphIntegrationTest({
    "GetDependencyGraph" should {
        "return dependency graphs for a seeded run" {
            dbExtension.fixtures.createAnalyzerRun(
                packages = setOf(
                    dbExtension.fixtures.generatePackage(
                        ModelIdentifier("Maven", "com.example", "library", "2.0")
                    )
                ),
                dependencyGraphs = mapOf("Maven" to createModelDependencyGraph())
            )

            dependencyGraphTestApplication { client ->
                val response = client.get("/runs/${dbExtension.fixtures.ortRun.id}/dependency-graph")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<DependencyGraphs>() shouldBe DependencyGraphs(
                    graphs = mapOf(
                        "Maven" to DependencyGraph(
                            packages = listOf(
                                Identifier("Maven", "com.example", "root", "1.0"),
                                Identifier("Maven", "com.example", "library", "2.0")
                            ),
                            purls = listOf(
                                null,
                                "pkg:Maven/com.example/library@2.0"
                            ),
                            packageCount = 2,
                            nodes = listOf(
                                DependencyGraphNode(
                                    pkg = 0,
                                    fragment = 0,
                                    linkage = "PROJECT_DYNAMIC",
                                    packageCount = 2
                                ),
                                DependencyGraphNode(
                                    pkg = 1,
                                    fragment = 1,
                                    linkage = "DYNAMIC",
                                    packageCount = 1
                                )
                            ),
                            edges = listOf(
                                DependencyGraphEdge(from = 0, to = 1)
                            ),
                            projectGroups = listOf(
                                DependencyGraphProjectGroup(
                                    projectLabel = "com.example:root:1.0",
                                    packageCount = 2,
                                    scopes = listOf(
                                        DependencyGraphScope(
                                            scopeName = "com.example:root:1.0:compile",
                                            scopeLabel = "compile",
                                            rootNodeIndexes = listOf(0),
                                            packageCount = 2
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            }
        }

        "embed project metadata in the response when analyzer projects are available" {
            dbExtension.fixtures.createAnalyzerRun(
                projects = setOf(
                    dbExtension.fixtures.getProject(
                        identifier = ModelIdentifier("Maven", "com.example", "root", "1.0")
                    ).copy(scopeNames = setOf("compile")),
                    dbExtension.fixtures.getProject(
                        identifier = ModelIdentifier("Maven", "com.example", "detached", "1.0")
                    ).copy(scopeNames = emptySet())
                ),
                packages = setOf(
                    dbExtension.fixtures.generatePackage(
                        ModelIdentifier("Maven", "com.example", "library", "2.0")
                    )
                ),
                dependencyGraphs = mapOf("Maven" to createModelDependencyGraph())
            )

            dependencyGraphTestApplication { client ->
                val response = client.get("/runs/${dbExtension.fixtures.ortRun.id}/dependency-graph")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<DependencyGraphs>().graphs["Maven"]?.projectGroups shouldBe listOf(
                    DependencyGraphProjectGroup(
                        projectLabel = "Maven:com.example:detached:1.0",
                        packageCount = null,
                        scopes = emptyList()
                    ),
                    DependencyGraphProjectGroup(
                        projectLabel = "Maven:com.example:root:1.0",
                        packageCount = 2,
                        scopes = listOf(
                            DependencyGraphScope(
                                scopeName = "com.example:root:1.0:compile",
                                scopeLabel = "compile",
                                rootNodeIndexes = listOf(0),
                                packageCount = 2
                            )
                        )
                    )
                )
            }
        }

        "return an empty graphs map when analyzer run has no dependency graphs" {
            dbExtension.fixtures.createAnalyzerRun()

            dependencyGraphTestApplication { client ->
                val response = client.get("/runs/${dbExtension.fixtures.ortRun.id}/dependency-graph")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<DependencyGraphs>() shouldBe DependencyGraphs(emptyMap())
            }
        }

        "return an empty graphs map when no analyzer run exists" {
            dependencyGraphTestApplication { client ->
                val response = client.get("/runs/${dbExtension.fixtures.ortRun.id}/dependency-graph")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<DependencyGraphs>() shouldBe DependencyGraphs(emptyMap())
            }
        }
    }
})

private fun createModelDependencyGraph() = ModelDependencyGraph(
    packages = listOf(
        ModelIdentifier("Maven", "com.example", "root", "1.0"),
        ModelIdentifier("Maven", "com.example", "library", "2.0")
    ),
    nodes = listOf(
        ModelDependencyGraphNode(pkg = 0, fragment = 0, linkage = "PROJECT_DYNAMIC", issues = emptyList()),
        ModelDependencyGraphNode(pkg = 1, fragment = 1, linkage = "DYNAMIC", issues = emptyList())
    ),
    edges = setOf(
        ModelDependencyGraphEdge(from = 0, to = 1)
    ),
    scopes = mapOf(
        "com.example:root:1.0:compile" to listOf(
            ModelDependencyGraphRoot(root = 0, fragment = 0)
        )
    )
)
