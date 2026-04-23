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

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraph
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphEdge
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphNode
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphProjectGroup
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphScope
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphs
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedConfigurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationProvidersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraph as ModelDependencyGraph
import org.eclipse.apoapsis.ortserver.model.runs.Identifier as ModelIdentifier
import org.eclipse.apoapsis.ortserver.model.runs.Project as ModelProject
import org.eclipse.apoapsis.ortserver.model.util.OrderField

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select

class DependencyGraphService(private val db: Database) {
    fun getDependencyGraphs(
        ortRunId: Long,
        sortFields: List<OrderField> = DEFAULT_DEPENDENCY_GRAPH_SORT_FIELDS
    ): DependencyGraphs = db.blockingQuery {
        val analyzerRun = AnalyzerRunDao.find {
            AnalyzerRunsTable.analyzerJobId inSubQuery AnalyzerJobsTable
                .select(AnalyzerJobsTable.id)
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }
        }.firstOrNull() ?: return@blockingQuery DependencyGraphs(emptyMap())

        val purlByIdentifier = getPurlByIdentifierForOrtRun(ortRunId)

        DependencyGraphs(
            graphs = analyzerRun.dependencyGraphsWrapper.dependencyGraphs.mapValues { (managerName, graph) ->
                graph.toApiModel(
                    projects = analyzerRun.projects
                        .filter { it.identifier.type == managerName }
                        .map { it.mapToModel() },
                    purlByIdentifier = purlByIdentifier,
                    sortFields = sortFields
                )
            }
        )
    }
}

internal fun ModelDependencyGraph.toApiModel(
    projects: Collection<ModelProject>,
    purlByIdentifier: Map<ModelIdentifier, String>,
    sortFields: List<OrderField>
): DependencyGraph {
    val adjacency = buildAdjacencyMap()
    val packageCounts = PackageCountCalculator(this, adjacency)
    val projectGroups = createProjectGroups(projects, packageCounts, sortFields)
    val allRootNodeIndexes = projectGroups
        .flatMap { it.scopes }
        .flatMap { it.rootNodeIndexes }
        .distinct()

    return DependencyGraph(
        packages = packages.map(ModelIdentifier::toApiModel),
        purls = packages.map { purlByIdentifier[it] },
        nodes = nodes.mapIndexed { index, node ->
            DependencyGraphNode(
                pkg = node.pkg,
                fragment = node.fragment,
                linkage = node.linkage,
                packageCount = packageCounts.countForNode(index)
            )
        },
        edges = edges.map { edge -> DependencyGraphEdge(edge.from, edge.to) },
        projectGroups = projectGroups,
        packageCount = packageCounts.countForRoots(allRootNodeIndexes).takeIf { it > 0 }
    )
}

private fun ModelIdentifier.toApiModel() = Identifier(
    type = type,
    namespace = namespace,
    name = name,
    version = version
)

internal fun getPurlByIdentifierForOrtRun(ortRunId: Long): Map<ModelIdentifier, String> {
    val basePurlsByIdentifierId = PackagesTable
        .innerJoin(PackagesAnalyzerRunsTable)
        .innerJoin(AnalyzerRunsTable)
        .innerJoin(AnalyzerJobsTable)
        .select(PackagesTable.identifierId, PackagesTable.purl)
        .where { AnalyzerJobsTable.ortRunId eq ortRunId }
        .associate { row ->
            row[PackagesTable.identifierId].value to row[PackagesTable.purl]
        }

    val curatedPurlsByIdentifierId = PackageCurationDataTable
        .innerJoin(PackageCurationsTable)
        .innerJoin(ResolvedPackageCurationsTable)
        .innerJoin(ResolvedPackageCurationProvidersTable)
        .innerJoin(ResolvedConfigurationsTable)
        .select(PackageCurationsTable.identifierId, PackageCurationDataTable.purl)
        .where {
            (ResolvedConfigurationsTable.ortRunId eq ortRunId) and
                PackageCurationDataTable.purl.isNotNull()
        }
        .orderBy(ResolvedPackageCurationProvidersTable.rank)
        .orderBy(ResolvedPackageCurationsTable.rank)
        .groupBy { row -> row[PackageCurationsTable.identifierId].value }
        .mapValues { (_, rows) ->
            requireNotNull(rows.first()[PackageCurationDataTable.purl]) {
                "Curated purl was unexpectedly null after filtering."
            }
        }

    val purlsByIdentifierId = basePurlsByIdentifierId + curatedPurlsByIdentifierId
    if (purlsByIdentifierId.isEmpty()) return emptyMap()

    val identifiersById = IdentifiersTable
        .select(
            IdentifiersTable.id,
            IdentifiersTable.type,
            IdentifiersTable.namespace,
            IdentifiersTable.name,
            IdentifiersTable.version
        )
        .where { IdentifiersTable.id inList purlsByIdentifierId.keys.toList() }
        .associate { row ->
            row[IdentifiersTable.id].value to row.toModelIdentifier()
        }

    return purlsByIdentifierId.mapKeys { (identifierId, _) ->
        requireNotNull(identifiersById[identifierId]) {
            "Could not find identifier with ID $identifierId while resolving purls for ORT run $ortRunId."
        }
    }
}

private fun ResultRow.toModelIdentifier() = ModelIdentifier(
    type = this[IdentifiersTable.type],
    namespace = this[IdentifiersTable.namespace],
    name = this[IdentifiersTable.name],
    version = this[IdentifiersTable.version]
)

private fun ModelDependencyGraph.createProjectGroups(
    projects: Collection<ModelProject>,
    packageCounts: PackageCountCalculator,
    sortFields: List<OrderField>
): List<DependencyGraphProjectGroup> {
    val packageNodeIndexMap = buildPackageNodeIndexMap()
    val projectLabelsByQualifiedScopeProject = projects.associate { project ->
        project.identifier.toQualifiedScopeProjectLabel() to project.identifier.toCoordinates()
    }
    val groupsByProjectLabel = scopes.entries
        .groupBy { (scopeName, _) ->
            val parsedScope = parseScopeName(scopeName)
            projectLabelsByQualifiedScopeProject[parsedScope.rawProjectLabel]
                ?: parsedScope.rawProjectLabel
                ?: scopeName
        }
        .mapValues { (_, projectScopes) ->
            projectScopes
                .map { (scopeName, roots) ->
                    val parsedScope = parseScopeName(scopeName)
                    val rootNodeIndexes = roots.mapNotNull { root ->
                        packageNodeIndexMap[root.root]
                            ?.firstOrNull { nodeIndex -> nodes[nodeIndex].fragment == root.fragment }
                    }

                    DependencyGraphScope(
                        scopeName = scopeName,
                        scopeLabel = parsedScope.scopeLabel,
                        rootNodeIndexes = rootNodeIndexes,
                        packageCount = packageCounts.countForRoots(rootNodeIndexes).takeIf { it > 0 }
                    )
                }
                .sortScopes(sortFields)
        }

    return (groupsByProjectLabel.keys + projects.map { it.identifier.toCoordinates() })
        .distinct()
        .map { projectLabel ->
            val scopes = groupsByProjectLabel[projectLabel].orEmpty()

            DependencyGraphProjectGroup(
                projectLabel = projectLabel,
                scopes = scopes,
                packageCount = packageCounts.countForRoots(scopes.flatMap { it.rootNodeIndexes }).takeIf { it > 0 }
            )
        }
        .sortProjectGroups(sortFields)
}

private fun ModelDependencyGraph.buildAdjacencyMap(): Map<Int, List<Int>> =
    edges.groupBy(keySelector = { it.from }, valueTransform = { it.to })

private fun ModelDependencyGraph.buildPackageNodeIndexMap(): Map<Int, List<Int>> =
    nodes.withIndex().groupBy(keySelector = { it.value.pkg }, valueTransform = { it.index })

internal class PackageCountCalculator(
    private val graph: ModelDependencyGraph,
    private val adjacency: Map<Int, List<Int>>
) {
    private val nodePackageCounts = mutableMapOf<Int, Int>()

    fun countForRoots(rootNodeIndexes: List<Int>): Int = countForRoots(rootNodeIndexes, emptySet())

    fun countForNode(nodeIndex: Int): Int = nodePackageCounts.getOrPut(nodeIndex) {
        countForRoots(rootNodeIndexes = listOf(nodeIndex), excludedNodeIndexes = emptySet())
    }

    private fun countForRoots(
        rootNodeIndexes: List<Int>,
        excludedNodeIndexes: Set<Int>
    ): Int {
        val visitedNodes = mutableSetOf<Int>()
        val visitedPackages = mutableSetOf<Int>()

        visitedNodes += excludedNodeIndexes
        rootNodeIndexes.forEach { visit(it, visitedNodes, visitedPackages) }

        return visitedPackages.size
    }

    private fun visit(nodeIndex: Int, visitedNodes: MutableSet<Int>, visitedPackages: MutableSet<Int>) {
        if (!visitedNodes.add(nodeIndex)) return

        val node = graph.nodes.getOrNull(nodeIndex) ?: return
        visitedPackages += node.pkg

        adjacency[nodeIndex].orEmpty().forEach { childNodeIndex ->
            visit(childNodeIndex, visitedNodes, visitedPackages)
        }
    }
}

private data class ParsedScopeName(
    val rawProjectLabel: String?,
    val scopeLabel: String?
)

private fun parseScopeName(scopeName: String): ParsedScopeName {
    val parts = scopeName.split(':', limit = 4)

    return if (parts.size < 4) {
        ParsedScopeName(rawProjectLabel = null, scopeLabel = null)
    } else {
        ParsedScopeName(
            rawProjectLabel = parts.take(3).joinToString(":"),
            scopeLabel = parts[3]
        )
    }
}

private fun ModelIdentifier.toQualifiedScopeProjectLabel(): String = "$namespace:$name:$version"
