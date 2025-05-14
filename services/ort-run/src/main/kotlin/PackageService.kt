/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services.ortrun

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ShortestDependencyPathsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.model.runs.PackageFilters
import org.eclipse.apoapsis.ortserver.model.runs.PackageWithShortestDependencyPaths
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection

import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database

/**
 * A service to interact with packages.
 */
class PackageService(private val db: Database, private val ortRunService: OrtRunService) {
    fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filters: PackageFilters = PackageFilters()
    ): ListQueryResult<PackageWithShortestDependencyPaths> {
        val ortRun = ortRunService.getOrtRun(ortRunId)

        if (ortRun == null) {
            return ListQueryResult(emptyList(), parameters, 0)
        }

        var comparator = compareBy<PackageWithShortestDependencyPaths> { 0 }

        parameters.sortFields.forEach { orderField ->
            when (orderField.name) {
                "identifier" -> {
                    comparator = when (orderField.direction) {
                        OrderDirection.ASCENDING -> {
                            comparator.thenBy { it.pkg.identifier.type }
                                .thenBy { it.pkg.identifier.namespace }
                                .thenBy { it.pkg.identifier.name }
                                .thenBy { it.pkg.identifier.version }
                        }

                        OrderDirection.DESCENDING -> {
                            comparator.thenByDescending { it.pkg.identifier.type }
                                .thenByDescending { it.pkg.identifier.namespace }
                                .thenByDescending { it.pkg.identifier.name }
                                .thenByDescending { it.pkg.identifier.version }
                        }
                    }
                }

                "purl" -> {
                    comparator = when (orderField.direction) {
                        OrderDirection.ASCENDING -> comparator.thenBy { it.pkg.purl.substringBefore('@') }
                            .thenBy { it.pkg.purl.substringAfter('@') }

                        OrderDirection.DESCENDING -> comparator.thenByDescending { it.pkg.purl.substringBefore('@') }
                            .thenByDescending { it.pkg.purl.substringAfter('@') }
                    }
                }

                "processedDeclaredLicense" -> {
                    comparator = when (orderField.direction) {
                        OrderDirection.ASCENDING -> comparator.thenBy { it.pkg.processedDeclaredLicense.spdxExpression }
                        OrderDirection.DESCENDING ->
                            comparator.thenByDescending { it.pkg.processedDeclaredLicense.spdxExpression }
                    }
                }

                else -> throw QueryParametersException("Unsupported field for sorting: '${orderField.name}'.")
            }
        }

        val ortResult = ortRunService.generateOrtResult(ortRun, failIfRepoInfoMissing = false)
        val packages = ortResult.getPackages()

        val result = packages.map { pkg ->
            pkg.metadata.mapToModel()
            PackageWithShortestDependencyPaths(
                pkg = pkg.metadata.mapToModel(),
                pkgId = 0L,
                shortestDependencyPaths = emptyList()
            )
        }

        var filteredResult = result

        filters.identifier?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            filteredResult = filteredResult.filter { pkg ->
                val identifierString = buildString {
                    append(pkg.pkg.identifier.type)
                    append(":")
                    if (pkg.pkg.identifier.namespace.isNotEmpty()) {
                        append(pkg.pkg.identifier.namespace)
                        append("/")
                    }
                    append(pkg.pkg.identifier.name)
                    append("@")
                    append(pkg.pkg.identifier.version)
                }
                identifierString.contains(Regex(filter.value, RegexOption.IGNORE_CASE))
            }
        }

        filters.purl?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            filteredResult = filteredResult.filter { pkg ->
                pkg.pkg.purl.contains(Regex(filter.value, RegexOption.IGNORE_CASE))
            }
        }

        filters.processedDeclaredLicense?.let { filter ->
            require(filter.operator == ComparisonOperator.IN || filter.operator == ComparisonOperator.NOT_IN) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            filteredResult = filteredResult.filter { pkg ->
                when (filter.operator) {
                    ComparisonOperator.IN -> pkg.pkg.processedDeclaredLicense.spdxExpression in filter.value
                    ComparisonOperator.NOT_IN -> pkg.pkg.processedDeclaredLicense.spdxExpression !in filter.value
                    else -> false
                }
            }
        }

        val sortedResult = filteredResult.sortedWith(comparator)

        val limitedResult = sortedResult
            .drop(parameters.offset?.toInt() ?: 0)
            .take(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT)

        // The shortest paths could also be requested from the dependency navigator of the ORT result, but loading the
        // precalculated shortest paths from the database is a lot faster.
        val shortestPathsByPackage = db.blockingQuery {
            ShortestDependencyPathsTable.getForOrtRunId(ortRunId)
        }

        val finalResult = limitedResult.map {
            it.copy(shortestDependencyPaths = shortestPathsByPackage[it.pkg.identifier].orEmpty())
        }

        return ListQueryResult(
            data = finalResult,
            params = parameters,
            totalCount = filteredResult.size.toLong()
        )
    }

    /** Count packages found in provided ORT runs. */
    suspend fun countForOrtRunIds(vararg ortRunIds: Long): Long = db.dbQuery {
        PackagesTable.joinAnalyzerTables()
            .select(PackagesTable.id)
            .where { AnalyzerJobsTable.ortRunId inList ortRunIds.asList() }
            .withDistinct()
            .count()
    }

    /** Count packages by ecosystem found in provided ORT runs. */
    suspend fun countEcosystemsForOrtRunIds(vararg ortRunIds: Long): List<EcosystemStats> =
        db.dbQuery {
            val countAlias = Count(PackagesTable.id, true)
            PackagesTable.joinAnalyzerTables()
                .innerJoin(IdentifiersTable)
                .select(IdentifiersTable.type, countAlias)
                .where { AnalyzerJobsTable.ortRunId inList ortRunIds.asList() }
                .groupBy(IdentifiersTable.type)
                .map { row ->
                    EcosystemStats(
                        row[IdentifiersTable.type],
                        row[countAlias]
                    )
                }
        }

    /** Get all distinct processed declared license expressions found in packages in an ORT run. */
    suspend fun getProcessedDeclaredLicenses(ortRunId: Long): List<String> =
        db.dbQuery {
            PackagesTable.joinAnalyzerTables()
                .innerJoin(ProcessedDeclaredLicensesTable)
                .select(ProcessedDeclaredLicensesTable.spdxExpression)
                .withDistinct()
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }
                .orderBy(ProcessedDeclaredLicensesTable.spdxExpression)
                .mapNotNull { it[ProcessedDeclaredLicensesTable.spdxExpression] }
        }
}

private fun PackagesTable.joinAnalyzerTables() =
    innerJoin(PackagesAnalyzerRunsTable)
        .innerJoin(AnalyzerRunsTable)
        .innerJoin(AnalyzerJobsTable)
