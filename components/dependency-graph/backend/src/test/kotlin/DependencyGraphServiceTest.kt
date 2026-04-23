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

package org.eclipse.apoapsis.ortserver.components.dependencygraph.backend

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraph
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphEdge
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphNode
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphProjectGroup
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphScope
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphs
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraph as ModelDependencyGraph
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphEdge as ModelDependencyGraphEdge
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphNode as ModelDependencyGraphNode
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphRoot as ModelDependencyGraphRoot
import org.eclipse.apoapsis.ortserver.model.runs.Identifier as ModelIdentifier
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField

import org.jetbrains.exposed.v1.jdbc.Database

class DependencyGraphServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures
    private lateinit var service: DependencyGraphService

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
            service = DependencyGraphService(db)
        }

        "getDependencyGraphs" should {
            "return an empty map when no analyzer run exists for the ORT run" {
                service.getDependencyGraphs(fixtures.ortRun.id) shouldBe DependencyGraphs(emptyMap())
            }

            "return an empty map when the analyzer run has no dependency graphs" {
                fixtures.createAnalyzerRun()

                service.getDependencyGraphs(fixtures.ortRun.id) shouldBe DependencyGraphs(emptyMap())
            }

            "return structured dependency graph metadata for the analyzer run" {
                val graph = createModelDependencyGraph()
                val libraryIdentifier = ModelIdentifier("Maven", "com.example", "library", "2.0")
                val project = fixtures.getProject(
                    identifier = ModelIdentifier("Maven", "com.example", "root", "1.0")
                ).copy(scopeNames = setOf("compile"))
                fixtures.createAnalyzerRun(
                    projects = setOf(project),
                    packages = setOf(fixtures.generatePackage(libraryIdentifier)),
                    dependencyGraphs = mapOf("Maven" to graph)
                )

                service.getDependencyGraphs(fixtures.ortRun.id) shouldBe DependencyGraphs(
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
                        )
                    )
                )
            }

            "return curated purls for package identifiers and null for project identifiers" {
                val graph = createModelDependencyGraph()
                val libraryIdentifier = ModelIdentifier("Maven", "com.example", "library", "2.0")
                val ortRun = fixtures.createOrtRun()
                val analyzerJob = fixtures.createAnalyzerJob(ortRun.id)

                fixtures.createAnalyzerRun(
                    analyzerJobId = analyzerJob.id,
                    packages = setOf(fixtures.generatePackage(libraryIdentifier)),
                    dependencyGraphs = mapOf("Maven" to graph)
                )

                val curatedPurl = "pkg:maven/com.example/library-curated@2.0"
                fixtures.resolvedConfigurationRepository.addPackageCurations(
                    ortRun.id,
                    listOf(
                        ResolvedPackageCurations(
                            provider = PackageCurationProviderConfig(name = "TestProvider"),
                            curations = listOf(
                                PackageCuration(
                                    id = libraryIdentifier,
                                    data = PackageCurationData(purl = curatedPurl)
                                )
                            )
                        )
                    )
                )

                service.getDependencyGraphs(ortRun.id).graphs["Maven"]?.purls shouldBe listOf(
                    null,
                    curatedPurl
                )
            }

            "skip unresolved roots and still return the remaining resolved roots" {
                val graph = createModelDependencyGraph(
                    scopes = mapOf(
                        "com.example:root:1.0:compile" to listOf(
                            ModelDependencyGraphRoot(root = 0, fragment = 0),
                            ModelDependencyGraphRoot(root = 0, fragment = 99)
                        )
                    )
                )
                fixtures.createAnalyzerRun(
                    projects = setOf(fixtures.getProject(ModelIdentifier("Maven", "com.example", "root", "1.0"))),
                    dependencyGraphs = mapOf("Maven" to graph)
                )

                service.getDependencyGraphs(fixtures.ortRun.id).graphs["Maven"]?.projectGroups shouldBe listOf(
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

            "deduplicate repeated package nodes when calculating package counts" {
                val graph = createModelDependencyGraph(
                    packages = listOf(
                        ModelIdentifier("Maven", "com.example", "root", "1.0"),
                        ModelIdentifier("Maven", "com.example", "library", "2.0")
                    ),
                    nodes = listOf(
                        ModelDependencyGraphNode(
                            pkg = 0,
                            fragment = 0,
                            linkage = "PROJECT_DYNAMIC",
                            issues = emptyList()
                        ),
                        ModelDependencyGraphNode(
                            pkg = 1,
                            fragment = 1,
                            linkage = "DYNAMIC",
                            issues = emptyList()
                        ),
                        ModelDependencyGraphNode(
                            pkg = 1,
                            fragment = 2,
                            linkage = "DYNAMIC",
                            issues = emptyList()
                        )
                    ),
                    edges = setOf(
                        ModelDependencyGraphEdge(from = 0, to = 1),
                        ModelDependencyGraphEdge(from = 1, to = 2)
                    )
                )
                fixtures.createAnalyzerRun(
                    projects = setOf(fixtures.getProject(ModelIdentifier("Maven", "com.example", "root", "1.0"))),
                    dependencyGraphs = mapOf("Maven" to graph)
                )

                service.getDependencyGraphs(fixtures.ortRun.id).graphs["Maven"]?.projectGroups shouldBe listOf(
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

            "handle cycles when calculating package counts" {
                val graph = createModelDependencyGraph(
                    edges = setOf(
                        ModelDependencyGraphEdge(from = 0, to = 1),
                        ModelDependencyGraphEdge(from = 1, to = 0)
                    )
                )
                fixtures.createAnalyzerRun(
                    projects = setOf(fixtures.getProject(ModelIdentifier("Maven", "com.example", "root", "1.0"))),
                    dependencyGraphs = mapOf("Maven" to graph)
                )

                service.getDependencyGraphs(fixtures.ortRun.id).graphs["Maven"]?.projectGroups shouldBe listOf(
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

            "fall back to the raw project part when no analyzer project matches the scope name" {
                val graph = createModelDependencyGraph()
                fixtures.createAnalyzerRun(dependencyGraphs = mapOf("Maven" to graph))

                service.getDependencyGraphs(fixtures.ortRun.id).graphs["Maven"]?.projectGroups shouldBe listOf(
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
            }

            "include analyzer projects that do not have scopes in the dependency graph" {
                val graph = createModelDependencyGraph()
                val matchingProject = fixtures.getProject(
                    identifier = ModelIdentifier("Maven", "com.example", "root", "1.0")
                )
                val projectWithoutScopes = fixtures.getProject(
                    identifier = ModelIdentifier("Maven", "com.example", "detached", "1.0")
                ).copy(scopeNames = emptySet())

                fixtures.createAnalyzerRun(
                    projects = setOf(matchingProject, projectWithoutScopes),
                    dependencyGraphs = mapOf("Maven" to graph)
                )

                service.getDependencyGraphs(fixtures.ortRun.id).graphs["Maven"]?.projectGroups shouldBe listOf(
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

            "calculate package counts for each dependency node subtree" {
                val graph = createModelDependencyGraph(
                    packages = listOf(
                        ModelIdentifier("Maven", "com.example", "root", "1.0"),
                        ModelIdentifier("Maven", "com.example", "library", "2.0"),
                        ModelIdentifier("Maven", "com.example", "leaf", "3.0")
                    ),
                    nodes = listOf(
                        ModelDependencyGraphNode(
                            pkg = 0,
                            fragment = 0,
                            linkage = "PROJECT_DYNAMIC",
                            issues = emptyList()
                        ),
                        ModelDependencyGraphNode(
                            pkg = 1,
                            fragment = 1,
                            linkage = "DYNAMIC",
                            issues = emptyList()
                        ),
                        ModelDependencyGraphNode(pkg = 2, fragment = 2, linkage = "STATIC", issues = emptyList())
                    ),
                    edges = setOf(
                        ModelDependencyGraphEdge(from = 0, to = 1),
                        ModelDependencyGraphEdge(from = 1, to = 2)
                    )
                )
                fixtures.createAnalyzerRun(
                    projects = setOf(fixtures.getProject(ModelIdentifier("Maven", "com.example", "root", "1.0"))),
                    dependencyGraphs = mapOf("Maven" to graph)
                )

                service.getDependencyGraphs(fixtures.ortRun.id).graphs["Maven"]?.nodes shouldBe listOf(
                    DependencyGraphNode(pkg = 0, fragment = 0, linkage = "PROJECT_DYNAMIC", packageCount = 3),
                    DependencyGraphNode(pkg = 1, fragment = 1, linkage = "DYNAMIC", packageCount = 2),
                    DependencyGraphNode(pkg = 2, fragment = 2, linkage = "STATIC", packageCount = 1)
                )
            }

            "omit aggregate package counts when no scope roots are available" {
                val graph = createModelDependencyGraph(scopes = emptyMap())
                val project = fixtures.getProject(
                    identifier = ModelIdentifier("Maven", "com.example", "root", "1.0")
                ).copy(scopeNames = emptySet())

                fixtures.createAnalyzerRun(
                    projects = setOf(project),
                    dependencyGraphs = mapOf("Maven" to graph)
                )

                service.getDependencyGraphs(fixtures.ortRun.id) shouldBe DependencyGraphs(
                    graphs = mapOf(
                        "Maven" to DependencyGraph(
                            packages = listOf(
                                Identifier("Maven", "com.example", "root", "1.0"),
                                Identifier("Maven", "com.example", "library", "2.0")
                            ),
                            purls = listOf(null, null),
                            packageCount = null,
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
                                    projectLabel = "Maven:com.example:root:1.0",
                                    packageCount = null,
                                    scopes = emptyList()
                                )
                            )
                        )
                    )
                )
            }

            "sort dependency graphs by name ascending by default" {
                fixtures.createAnalyzerRun(
                    projects = setOf(
                        fixtures.getProject(
                            identifier = ModelIdentifier("Maven", "com.example", "root", "1.0")
                        ).copy(scopeNames = setOf("compile", "test")),
                        fixtures.getProject(
                            identifier = ModelIdentifier("Maven", "com.example", "zeta", "1.0")
                        ).copy(scopeNames = setOf("runtime"))
                    ),
                    dependencyGraphs = mapOf("Maven" to createSortableModelDependencyGraph())
                )

                val graph = requireNotNull(service.getDependencyGraphs(fixtures.ortRun.id).graphs["Maven"])

                graph.projectGroups.map { it.projectLabel } shouldBe listOf(
                    "Maven:com.example:root:1.0",
                    "Maven:com.example:zeta:1.0"
                )
                graph.projectGroups[0].scopes.map { it.scopeName } shouldBe
                    listOf("com.example:root:1.0:compile", "com.example:root:1.0:test")
                graph.edges shouldBe listOf(
                    DependencyGraphEdge(from = 0, to = 1),
                    DependencyGraphEdge(from = 0, to = 3)
                )
            }

            "apply multi-sort globally to project groups and scopes only" {
                fixtures.createAnalyzerRun(
                    projects = setOf(
                        fixtures.getProject(
                            identifier = ModelIdentifier("Maven", "com.example", "root", "1.0")
                        ).copy(scopeNames = setOf("compile", "test")),
                        fixtures.getProject(
                            identifier = ModelIdentifier("Maven", "com.example", "zeta", "1.0")
                        ).copy(scopeNames = setOf("runtime"))
                    ),
                    dependencyGraphs = mapOf("Maven" to createSortableModelDependencyGraph())
                )

                val graph = requireNotNull(
                    service.getDependencyGraphs(
                        fixtures.ortRun.id,
                        listOf(
                            OrderField("packageCount", OrderDirection.DESCENDING),
                            OrderField("name", OrderDirection.DESCENDING)
                        )
                    ).graphs["Maven"]
                )

                graph.projectGroups.map { it.projectLabel } shouldBe listOf(
                    "Maven:com.example:root:1.0",
                    "Maven:com.example:zeta:1.0"
                )
                graph.projectGroups[0].scopes.map { it.scopeName } shouldBe listOf(
                    "com.example:root:1.0:test",
                    "com.example:root:1.0:compile"
                )
                graph.edges shouldBe listOf(
                    DependencyGraphEdge(from = 0, to = 1),
                    DependencyGraphEdge(from = 0, to = 3)
                )
            }

            "apply descending name sorting globally across all package managers" {
                fixtures.createAnalyzerRun(
                    projects = setOf(
                        fixtures.getProject(
                            identifier = ModelIdentifier("Maven", "com.example", "root", "1.0")
                        ).copy(scopeNames = setOf("compile", "test")),
                        fixtures.getProject(
                            identifier = ModelIdentifier("Maven", "com.example", "zeta", "1.0")
                        ).copy(scopeNames = setOf("runtime")),
                        fixtures.getProject(
                            identifier = ModelIdentifier("NPM", "example", "root", "1.0")
                        ).copy(scopeNames = setOf("compile", "test")),
                        fixtures.getProject(
                            identifier = ModelIdentifier("NPM", "example", "zeta", "1.0")
                        ).copy(scopeNames = setOf("runtime"))
                    ),
                    dependencyGraphs = mapOf(
                        "Maven" to createSortableModelDependencyGraph(type = "Maven", namespace = "com.example"),
                        "NPM" to createSortableModelDependencyGraph(type = "NPM", namespace = "example")
                    )
                )

                val graphs = service.getDependencyGraphs(
                    fixtures.ortRun.id,
                    listOf(OrderField("name", OrderDirection.DESCENDING))
                ).graphs

                requireNotNull(graphs["Maven"]).projectGroups.map { it.projectLabel } shouldBe listOf(
                    "Maven:com.example:zeta:1.0",
                    "Maven:com.example:root:1.0"
                )
                requireNotNull(graphs["NPM"]).projectGroups.map { it.projectLabel } shouldBe listOf(
                    "NPM:example:zeta:1.0",
                    "NPM:example:root:1.0"
                )
            }
        }
    }
}

private fun createModelDependencyGraph(
    packages: List<ModelIdentifier> = listOf(
        ModelIdentifier("Maven", "com.example", "root", "1.0"),
        ModelIdentifier("Maven", "com.example", "library", "2.0")
    ),
    nodes: List<ModelDependencyGraphNode> = listOf(
        ModelDependencyGraphNode(pkg = 0, fragment = 0, linkage = "PROJECT_DYNAMIC", issues = emptyList()),
        ModelDependencyGraphNode(pkg = 1, fragment = 1, linkage = "DYNAMIC", issues = emptyList())
    ),
    edges: Set<ModelDependencyGraphEdge> = setOf(
        ModelDependencyGraphEdge(from = 0, to = 1)
    ),
    scopes: Map<String, List<ModelDependencyGraphRoot>> = mapOf(
        "com.example:root:1.0:compile" to listOf(
            ModelDependencyGraphRoot(root = 0, fragment = 0)
        )
    )
) = ModelDependencyGraph(
    packages = packages,
    nodes = nodes,
    edges = edges,
    scopes = scopes
)

private fun createSortableModelDependencyGraph(
    type: String = "Maven",
    namespace: String = "com.example"
) = ModelDependencyGraph(
    packages = listOf(
        ModelIdentifier(type, namespace, "root", "1.0"),
        ModelIdentifier(type, namespace, "beta", "1.0"),
        ModelIdentifier(type, namespace, "zeta", "1.0"),
        ModelIdentifier(type, namespace, "alpha", "1.0")
    ),
    nodes = listOf(
        ModelDependencyGraphNode(pkg = 0, fragment = 0, linkage = "PROJECT_DYNAMIC", issues = emptyList()),
        ModelDependencyGraphNode(pkg = 1, fragment = 1, linkage = "DYNAMIC", issues = emptyList()),
        ModelDependencyGraphNode(pkg = 2, fragment = 2, linkage = "PROJECT_DYNAMIC", issues = emptyList()),
        ModelDependencyGraphNode(pkg = 3, fragment = 3, linkage = "STATIC", issues = emptyList())
    ),
    edges = linkedSetOf(
        ModelDependencyGraphEdge(from = 0, to = 1),
        ModelDependencyGraphEdge(from = 0, to = 3)
    ),
    scopes = linkedMapOf(
        "$namespace:zeta:1.0:runtime" to listOf(
            ModelDependencyGraphRoot(root = 2, fragment = 2)
        ),
        "$namespace:root:1.0:test" to listOf(
            ModelDependencyGraphRoot(root = 0, fragment = 0)
        ),
        "$namespace:root:1.0:compile" to listOf(
            ModelDependencyGraphRoot(root = 0, fragment = 0)
        )
    )
)
