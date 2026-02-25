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
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackageDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ShortestDependencyPathsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.CuratedPackagesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.applyILike
import org.eclipse.apoapsis.ortserver.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.model.runs.PackageFilters
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection

import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

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
        ortRunService.getOrtRun(ortRunId) ?: return ListQueryResult(emptyList(), parameters, 0)

        return db.blockingQuery {
            val query = PackagesTable.joinAnalyzerTables()
                .innerJoin(IdentifiersTable)
                .innerJoin(ProcessedDeclaredLicensesTable)
                .select(PackagesTable.id)
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }

            filters.identifier?.let { filter ->
                require(filter.operator == ComparisonOperator.ILIKE) {
                    "Unsupported operator for identifier filter: ${filter.operator}"
                }

                val identifierConcat = CustomFunction<String>(
                    "CONCAT",
                    TextColumnType(),
                    IdentifiersTable.type,
                    stringLiteral(":"),
                    IdentifiersTable.namespace,
                    stringLiteral(":"),
                    IdentifiersTable.name,
                    stringLiteral(":"),
                    IdentifiersTable.version
                )

                query.andWhere { identifierConcat.applyILike(filter.value) }
            }

            filters.purl?.let { filter ->
                require(filter.operator == ComparisonOperator.ILIKE) {
                    "Unsupported operator for identifier filter: ${filter.operator}"
                }

                query.andWhere { PackagesTable.purl.applyILike(filter.value) }
            }

            filters.processedDeclaredLicense?.let { filter ->
                require(filter.operator == ComparisonOperator.IN || filter.operator == ComparisonOperator.NOT_IN) {
                    "Unsupported operator for identifier filter: ${filter.operator}"
                }

                when (filter.operator) {
                    ComparisonOperator.IN ->
                        query.andWhere { ProcessedDeclaredLicensesTable.spdxExpression inList filter.value }

                    ComparisonOperator.NOT_IN ->
                        query.andWhere { ProcessedDeclaredLicensesTable.spdxExpression notInList filter.value }

                    else -> {}
                }
            }

            val totalCount = query.count()

            parameters.sortFields.forEach { orderField ->
                val sortOrder = when (orderField.direction) {
                    OrderDirection.ASCENDING -> SortOrder.ASC
                    OrderDirection.DESCENDING -> SortOrder.DESC
                }

                when (orderField.name) {
                    "identifier" -> {
                        query.orderBy(IdentifiersTable.type to sortOrder)
                        query.orderBy(IdentifiersTable.namespace to sortOrder)
                        query.orderBy(IdentifiersTable.name to sortOrder)
                        query.orderBy(IdentifiersTable.version to sortOrder)
                    }

                    "purl" -> query.orderBy(PackagesTable.purl to sortOrder)

                    "processedDeclaredLicense" ->
                        query.orderBy(ProcessedDeclaredLicensesTable.spdxExpression to sortOrder)

                    else -> throw QueryParametersException("Unsupported field for sorting: '${orderField.name}'.")
                }
            }

            val limit = parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT
            val offset = parameters.offset ?: 0L
            query.limit(limit).offset(offset)

            val packageIds = query.map { it[PackagesTable.id] }

            if (packageIds.isEmpty()) {
                return@blockingQuery ListQueryResult(emptyList(), parameters, totalCount)
            }

            val packagesById = PackageDao.find { PackagesTable.id inList packageIds }
                .associateBy { it.id }
            val packages = packageIds.map { packagesById.getValue(it) }

            val curationsMap = CuratedPackagesTable.getForOrtRunId(ortRunId)
            val shortestPathsByPackage = ShortestDependencyPathsTable.getForOrtRunId(ortRunId)

            val finalResult = packages.map { packageDao ->
                val modelPackage = packageDao.mapToModel()
                val shortestDependencyPaths = shortestPathsByPackage[modelPackage.identifier].orEmpty()

                val packageCurations = curationsMap[modelPackage.identifier].orEmpty()

                val ortPackage = modelPackage.mapToOrt()
                val curatedOrtPackage = packageCurations.fold(OrtCuratedPackage(ortPackage)) { acc, (_, curation) ->
                    curation.mapToOrt().apply(acc)
                }
                val curatedModelPackage = curatedOrtPackage.metadata.mapToModel()

                val apiCurations = packageCurations.map { (providerName, curation) ->
                    ApiPackageCuration(providerName, curation.data.mapToApi())
                }

                curatedModelPackage.mapToApi(shortestDependencyPaths, apiCurations)
            }

            ListQueryResult(
                data = finalResult,
                params = parameters,
                totalCount = totalCount
            )
        }
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
