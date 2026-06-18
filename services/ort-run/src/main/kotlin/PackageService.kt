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
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ShortestDependencyPathsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.UnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.CuratedPackagesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.applyILike
import org.eclipse.apoapsis.ortserver.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package as ModelPackage
import org.eclipse.apoapsis.ortserver.model.runs.PackageFilters
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection

import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

import org.ossreviewtoolkit.model.CuratedPackage as OrtCuratedPackage

/**
 * A service to interact with packages.
 */
class PackageService(private val db: Database, private val ortRunService: OrtRunService) {
    @Suppress("LongMethod")
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

            val curationsMap by lazy { CuratedPackagesTable.getForOrtRunId(ortRunId) }

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

            filters.declaredLicense?.let { filter ->
                require(filter.operator == ComparisonOperator.IN || filter.operator == ComparisonOperator.NOT_IN) {
                    "Unsupported operator for declared license filter: ${filter.operator}"
                }

                // The SQL query uses the declared license values stored by the analyzer.
                // This is sufficient for packages whose declared license is not changed by curations.
                val packageIdsSubquery = PackagesTable.joinAnalyzerTables()
                    .innerJoin(ProcessedDeclaredLicensesTable)
                    .leftJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
                    .leftJoin(UnmappedDeclaredLicensesTable)
                    .select(PackagesTable.id)
                    .where {
                        (AnalyzerJobsTable.ortRunId eq ortRunId) and
                            (
                                (ProcessedDeclaredLicensesTable.spdxExpression inList filter.value) or
                                    (UnmappedDeclaredLicensesTable.unmappedLicense inList filter.value)
                            )
                    }

                // Only curations with a declared license mapping can change the processed declared license.
                val affectedIdentifierIds = curationsMap
                    .filterValues { list ->
                        list.any { (_, curation) ->
                            curation.data.declaredLicenseMapping.isNotEmpty()
                        }
                    }
                    .keys
                    .mapNotNull { IdentifierDao.findByIdentifier(it)?.id }

                // For packages with such curations, apply the curations and use the resulting declared license.
                val curatedDeclaredLicenses = if (affectedIdentifierIds.isEmpty()) {
                    emptyList()
                } else {
                    val affectedPackageIds = PackagesTable.joinAnalyzerTables()
                        .select(PackagesTable.id)
                        .where {
                            (AnalyzerJobsTable.ortRunId eq ortRunId) and
                                (PackagesTable.identifierId inList affectedIdentifierIds)
                        }
                        .map { it[PackagesTable.id] }

                    if (affectedPackageIds.isEmpty()) {
                        emptyList()
                    } else {
                        PackageDao.find { PackagesTable.id inList affectedPackageIds }.map { dao ->
                            val modelPackage = dao.mapToModel()
                            val curatedModelPackage = modelPackage.applyCurations(curationsMap).metadata.mapToModel()

                            dao.id to curatedModelPackage.processedDeclaredLicense
                        }
                    }
                }

                val packageIdsWithDeclaredLicenseCurations = curatedDeclaredLicenses.map { it.first }
                val curatedPackageIdsMatchingFilter = curatedDeclaredLicenses.filter { (_, curatedDeclared) ->
                    val matches = curatedDeclared.spdxExpression in filter.value ||
                            curatedDeclared.unmappedLicenses.any { it in filter.value }
                    if (filter.operator == ComparisonOperator.IN) matches else !matches
                }.map { it.first }

                // Combine the SQL result for packages without declared license curations with the curated values
                // calculated above. This prevents matching a package by an analyzer value that was changed by a
                // curation.
                query.andWhere {
                    val sqlFilter = if (filter.operator == ComparisonOperator.IN) {
                        PackagesTable.id inSubQuery packageIdsSubquery
                    } else {
                        not(PackagesTable.id inSubQuery packageIdsSubquery)
                    }

                    val unaffected = if (packageIdsWithDeclaredLicenseCurations.isEmpty()) {
                        sqlFilter
                    } else {
                        (PackagesTable.id notInList packageIdsWithDeclaredLicenseCurations) and sqlFilter
                    }

                    if (curatedPackageIdsMatchingFilter.isEmpty()) {
                        unaffected
                    } else {
                        unaffected or (PackagesTable.id inList curatedPackageIdsMatchingFilter)
                    }
                }
            }

            filters.isDirectDependency?.let { filter ->
                 // The subquery keeps the existing query shape unchanged and applies the new filter as an
                 // existence check, rather than joining a one-to-many-per-package table into the main query,
                 // because extending the main join tree would duplicate package rows and make counting,
                 // pagination, and sorting more fragile.
                val directPackageIdsSubquery = ShortestDependencyPathsTable
                    .innerJoin(AnalyzerRunsTable)
                    .innerJoin(AnalyzerJobsTable)
                    .select(ShortestDependencyPathsTable.packageId)
                    .where {
                        (AnalyzerJobsTable.ortRunId eq ortRunId) and
                            (ShortestDependencyPathsTable.path eq emptyList())
                    }

                when (filter) {
                    true -> query.andWhere { PackagesTable.id inSubQuery directPackageIdsSubquery }
                    false -> query.andWhere { not(PackagesTable.id inSubQuery directPackageIdsSubquery) }
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

            val shortestPathsByPackage = ShortestDependencyPathsTable.getForOrtRunId(ortRunId)

            val finalResult = packages.map { packageDao ->
                val modelPackage = packageDao.mapToModel()
                val shortestDependencyPaths = shortestPathsByPackage[modelPackage.identifier].orEmpty()

                val packageCurations = curationsMap[modelPackage.identifier].orEmpty()
                val curatedModelPackage = modelPackage.applyCurations(curationsMap).metadata.mapToModel()

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

    /** Return distinct processed declared SPDX license expressions for the ORT run. */
    suspend fun getProcessedDeclaredLicenses(ortRunId: Long): List<String> =
        db.dbQuery {
            val curationsMap = CuratedPackagesTable.getForOrtRunId(ortRunId)
            val affectedIdentifierIds = curationsMap
                .filterValues { list ->
                    list.any { (_, curation) ->
                        curation.data.declaredLicenseMapping.isNotEmpty()
                    }
                }
                .keys
                .mapNotNull { IdentifierDao.findByIdentifier(it)?.id }

            val curatedDeclaredLicenses = if (affectedIdentifierIds.isEmpty()) {
                emptyList()
            } else {
                val affectedPackageIds = PackagesTable.joinAnalyzerTables()
                    .select(PackagesTable.id)
                    .where {
                        (AnalyzerJobsTable.ortRunId eq ortRunId) and
                            (PackagesTable.identifierId inList affectedIdentifierIds)
                    }
                    .map { it[PackagesTable.id] }

                if (affectedPackageIds.isEmpty()) {
                    emptyList()
                } else {
                    PackageDao.find { PackagesTable.id inList affectedPackageIds }.map { dao ->
                        val modelPackage = dao.mapToModel()
                        val curatedModelPackage = modelPackage.applyCurations(curationsMap).metadata.mapToModel()

                        dao.id to curatedModelPackage.processedDeclaredLicense
                    }
                }
            }
            val packageIdsWithDeclaredLicenseCurations = curatedDeclaredLicenses.map { it.first }

            val rawQuery = PackagesTable.joinAnalyzerTables()
                .innerJoin(ProcessedDeclaredLicensesTable)
                .select(ProcessedDeclaredLicensesTable.spdxExpression)
                .withDistinct()
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }

            if (packageIdsWithDeclaredLicenseCurations.isNotEmpty()) {
                rawQuery.andWhere { PackagesTable.id notInList packageIdsWithDeclaredLicenseCurations }
            }

            val licenses = rawQuery.mapNotNullTo(mutableSetOf()) {
                it[ProcessedDeclaredLicensesTable.spdxExpression]
            }

            curatedDeclaredLicenses.mapNotNullTo(licenses) { it.second.spdxExpression }

            licenses.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

    /** Return distinct unmapped declared license strings for the ORT run. */
    suspend fun getUnmappedDeclaredLicenses(ortRunId: Long): List<String> =
        db.dbQuery {
            val curationsMap = CuratedPackagesTable.getForOrtRunId(ortRunId)
            val affectedIdentifierIds = curationsMap
                .filterValues { list ->
                    list.any { (_, curation) ->
                        curation.data.declaredLicenseMapping.isNotEmpty()
                    }
                }
                .keys
                .mapNotNull { IdentifierDao.findByIdentifier(it)?.id }

            val curatedDeclaredLicenses = if (affectedIdentifierIds.isEmpty()) {
                emptyList()
            } else {
                val affectedPackageIds = PackagesTable.joinAnalyzerTables()
                    .select(PackagesTable.id)
                    .where {
                        (AnalyzerJobsTable.ortRunId eq ortRunId) and
                            (PackagesTable.identifierId inList affectedIdentifierIds)
                    }
                    .map { it[PackagesTable.id] }

                if (affectedPackageIds.isEmpty()) {
                    emptyList()
                } else {
                    PackageDao.find { PackagesTable.id inList affectedPackageIds }.map { dao ->
                        val modelPackage = dao.mapToModel()
                        val curatedModelPackage = modelPackage.applyCurations(curationsMap).metadata.mapToModel()

                        dao.id to curatedModelPackage.processedDeclaredLicense
                    }
                }
            }
            val packageIdsWithDeclaredLicenseCurations = curatedDeclaredLicenses.map { it.first }

            val rawQuery = PackagesTable.joinAnalyzerTables()
                .innerJoin(ProcessedDeclaredLicensesTable)
                .innerJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
                .innerJoin(UnmappedDeclaredLicensesTable)
                .select(UnmappedDeclaredLicensesTable.unmappedLicense)
                .withDistinct()
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }

            if (packageIdsWithDeclaredLicenseCurations.isNotEmpty()) {
                rawQuery.andWhere { PackagesTable.id notInList packageIdsWithDeclaredLicenseCurations }
            }

            val licenses = rawQuery.mapTo(mutableSetOf()) { it[UnmappedDeclaredLicensesTable.unmappedLicense] }

            curatedDeclaredLicenses.flatMapTo(licenses) { it.second.unmappedLicenses }

            licenses.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
}

private fun ModelPackage.applyCurations(
    curationsByIdentifier: Map<Identifier, List<Pair<String, PackageCuration>>>
): OrtCuratedPackage =
    curationsByIdentifier[identifier].orEmpty().fold(OrtCuratedPackage(mapToOrt())) { curatedPackage, (_, curation) ->
        curation.mapToOrt().apply(curatedPackage)
    }

private fun PackagesTable.joinAnalyzerTables() =
    innerJoin(PackagesAnalyzerRunsTable)
        .innerJoin(AnalyzerRunsTable)
        .innerJoin(AnalyzerJobsTable)
