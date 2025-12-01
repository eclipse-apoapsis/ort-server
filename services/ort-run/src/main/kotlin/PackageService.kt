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

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.Package as ApiPackage
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageCuration as ApiPackageCuration
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
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection

import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.CuratedPackage as OrtCuratedPackage

/**
 * A service to interact with packages.
 */
class PackageService(private val db: Database, private val ortRunService: OrtRunService) {
    fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filters: PackageFilters = PackageFilters()
    ): ListQueryResult<ApiPackage> {
        val ortRun = ortRunService.getOrtRun(ortRunId) ?: return ListQueryResult(emptyList(), parameters, 0)

        var comparator = compareBy<OrtCuratedPackage> { 0 }

        parameters.sortFields.forEach { orderField ->
            when (orderField.name) {
                "identifier" -> {
                    comparator = when (orderField.direction) {
                        OrderDirection.ASCENDING ->
                            comparator
                                .thenBy { it.metadata.id.type }
                                .thenBy { it.metadata.id.namespace }
                                .thenBy { it.metadata.id.name }
                                .thenBy { it.metadata.id.version }

                        OrderDirection.DESCENDING ->
                            comparator
                                .thenByDescending { it.metadata.id.type }
                                .thenByDescending { it.metadata.id.namespace }
                                .thenByDescending { it.metadata.id.name }
                                .thenByDescending { it.metadata.id.version }
                    }
                }

                "purl" -> {
                    comparator = when (orderField.direction) {
                        OrderDirection.ASCENDING ->
                            comparator
                                .thenBy { it.metadata.purl.substringBefore('@') }
                                .thenBy { it.metadata.purl.substringAfter('@') }

                        OrderDirection.DESCENDING ->
                            comparator
                                .thenByDescending { it.metadata.purl.substringBefore('@') }
                                .thenByDescending { it.metadata.purl.substringAfter('@') }
                    }
                }

                "processedDeclaredLicense" -> {
                    comparator = when (orderField.direction) {
                        OrderDirection.ASCENDING ->
                            comparator
                                .thenBy { it.metadata.declaredLicensesProcessed.spdxExpression.toString() }

                        OrderDirection.DESCENDING ->
                            comparator
                                .thenByDescending { it.metadata.declaredLicensesProcessed.spdxExpression.toString() }
                    }
                }

                else -> throw QueryParametersException("Unsupported field for sorting: '${orderField.name}'.")
            }
        }

        val ortResult = ortRunService.generateOrtResult(
            ortRun,
            loadAdvisorRun = false,
            loadScannerRun = false,
            loadEvaluatorRun = false,
            failIfRepoInfoMissing = false
        )

        var filteredResult = ortResult.getPackages().toList()

        filters.identifier?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            val idFilterRegex = Regex(filter.value, RegexOption.IGNORE_CASE)

            filteredResult = filteredResult.filter { pkg ->
                idFilterRegex.containsMatchIn(pkg.metadata.id.toCoordinates())
            }
        }

        filters.purl?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            val purlFilterRegex = Regex(filter.value, RegexOption.IGNORE_CASE)

            filteredResult = filteredResult.filter { pkg ->
                purlFilterRegex.containsMatchIn(pkg.metadata.purl)
            }
        }

        filters.processedDeclaredLicense?.let { filter ->
            require(filter.operator == ComparisonOperator.IN || filter.operator == ComparisonOperator.NOT_IN) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            filteredResult = filteredResult.filter { pkg ->
                val declaredLicenseExpression = pkg.metadata.declaredLicensesProcessed.spdxExpression.toString()
                when (filter.operator) {
                    ComparisonOperator.IN -> declaredLicenseExpression in filter.value
                    ComparisonOperator.NOT_IN -> declaredLicenseExpression !in filter.value
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

        val finalResult = limitedResult.map { ortPkg ->
            val shortestDependencyPaths = shortestPathsByPackage[ortPkg.metadata.id.mapToModel()].orEmpty()
            val curations = ortPkg.curations.map { ApiPackageCuration(data = it.mapToModel().mapToApi()) }
            ortPkg.metadata.mapToModel().mapToApi(shortestDependencyPaths, curations)
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
