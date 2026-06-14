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
import org.eclipse.apoapsis.ortserver.model.runs.PackageFilters
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection

import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
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

            var curationsMap: Map<Identifier, List<Pair<String, PackageCuration>>>? = null
            var overrides: List<DeclaredLicenseOverride>? = null

            fun getCurationsMap() = curationsMap
                ?: CuratedPackagesTable.getForOrtRunId(ortRunId).also { curationsMap = it }

            fun getOverrides() = overrides
                ?: computeDeclaredLicenseOverrides(ortRunId, getCurationsMap()).also { overrides = it }

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
                query.applyProcessedDeclaredLicenseFilter(filter, getOverrides())
            }

            filters.declaredLicense?.let { filter ->
                query.applyDeclaredLicenseFilter(ortRunId, filter, getOverrides())
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

                val packageCurations = getCurationsMap()[modelPackage.identifier].orEmpty()

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

    /** Return distinct processed declared SPDX expressions for the ORT run. */
    suspend fun getProcessedDeclaredLicenses(ortRunId: Long): List<String> =
        db.dbQuery {
            val curationsMap = CuratedPackagesTable.getForOrtRunId(ortRunId)
            val overrides = computeDeclaredLicenseOverrides(ortRunId, curationsMap)
            val affectedIds = overrides.map { it.packageId }

            val rawQuery = PackagesTable.joinAnalyzerTables()
                .innerJoin(ProcessedDeclaredLicensesTable)
                .select(ProcessedDeclaredLicensesTable.spdxExpression)
                .withDistinct()
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }

            if (affectedIds.isNotEmpty()) {
                rawQuery.andWhere { PackagesTable.id notInList affectedIds }
            }

            val licenses = rawQuery.mapNotNullTo(mutableSetOf()) {
                it[ProcessedDeclaredLicensesTable.spdxExpression]
            }

            overrides.mapNotNullTo(licenses) { it.curatedSpdxExpression }

            licenses.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

    /** Return distinct unmapped declared license strings for the ORT run. */
    suspend fun getUnmappedDeclaredLicenses(ortRunId: Long): List<String> =
        db.dbQuery {
            val curationsMap = CuratedPackagesTable.getForOrtRunId(ortRunId)
            val overrides = computeDeclaredLicenseOverrides(ortRunId, curationsMap)
            val affectedIds = overrides.map { it.packageId }

            val rawQuery = PackagesTable.joinAnalyzerTables()
                .innerJoin(ProcessedDeclaredLicensesTable)
                .innerJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
                .innerJoin(UnmappedDeclaredLicensesTable)
                .select(UnmappedDeclaredLicensesTable.unmappedLicense)
                .withDistinct()
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }

            if (affectedIds.isNotEmpty()) {
                rawQuery.andWhere { PackagesTable.id notInList affectedIds }
            }

            val licenses = rawQuery.mapTo(mutableSetOf()) { it[UnmappedDeclaredLicensesTable.unmappedLicense] }

            overrides.flatMapTo(licenses) { it.curatedUnmappedLicenses }

            licenses.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
}

/**
 * Layer the curation overlay on top of the SQL `processedDeclaredLicense` filter. Unaffected packages
 * stay on the SQL fast path (`spdxExpression IN/NOT IN`). Affected packages are matched against their
 * precomputed curated `spdxExpression`; for NOT_IN the SQL `NULL` semantics ("NULL drops out") are
 * preserved.
 */
private fun Query.applyProcessedDeclaredLicenseFilter(
    filter: FilterOperatorAndValue<Set<String>>,
    overrides: List<DeclaredLicenseOverride>
) {
    require(filter.operator == ComparisonOperator.IN || filter.operator == ComparisonOperator.NOT_IN) {
        "Unsupported operator for identifier filter: ${filter.operator}"
    }

    val affectedIds = overrides.map { it.packageId }
    val affectedMatchingIds = overrides.filter { o ->
        if (filter.operator == ComparisonOperator.IN) {
            o.curatedSpdxExpression in filter.value
        } else {
            o.curatedSpdxExpression != null && o.curatedSpdxExpression !in filter.value
        }
    }.map { it.packageId }

    andWhere {
        val sqlFilter = if (filter.operator == ComparisonOperator.IN) {
            ProcessedDeclaredLicensesTable.spdxExpression inList filter.value
        } else {
            ProcessedDeclaredLicensesTable.spdxExpression notInList filter.value
        }
        val unaffected = if (affectedIds.isEmpty()) {
            sqlFilter
        } else {
            (PackagesTable.id notInList affectedIds) and sqlFilter
        }
        if (affectedMatchingIds.isEmpty()) {
            unaffected
        } else {
            unaffected or (PackagesTable.id inList affectedMatchingIds)
        }
    }
}

/**
 * Layer the curation overlay on top of the SQL `declaredLicense` filter (which considers both the
 * processed SPDX expression and unmapped declared license strings). Unaffected packages use the
 * existing subquery; affected packages are matched against curated SPDX and curated unmapped values.
 */
private fun Query.applyDeclaredLicenseFilter(
    ortRunId: Long,
    filter: FilterOperatorAndValue<Set<String>>,
    overrides: List<DeclaredLicenseOverride>
) {
    require(filter.operator == ComparisonOperator.IN || filter.operator == ComparisonOperator.NOT_IN) {
        "Unsupported operator for declared license filter: ${filter.operator}"
    }

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

    val affectedIds = overrides.map { it.packageId }
    val affectedMatchingIds = overrides.filter { o ->
        val matches = o.curatedSpdxExpression in filter.value ||
                o.curatedUnmappedLicenses.any { it in filter.value }
        if (filter.operator == ComparisonOperator.IN) matches else !matches
    }.map { it.packageId }

    andWhere {
        val sqlFilter = if (filter.operator == ComparisonOperator.IN) {
            PackagesTable.id inSubQuery packageIdsSubquery
        } else {
            not(PackagesTable.id inSubQuery packageIdsSubquery)
        }
        val unaffected = if (affectedIds.isEmpty()) {
            sqlFilter
        } else {
            (PackagesTable.id notInList affectedIds) and sqlFilter
        }
        if (affectedMatchingIds.isEmpty()) {
            unaffected
        } else {
            unaffected or (PackagesTable.id inList affectedMatchingIds)
        }
    }
}

/**
 * Snapshot of the curated `processedDeclaredLicense` fields for a single package whose curations could
 * change the values that SQL holds. Used to reconcile filters and license-option lists with what the
 * package detail view actually shows, without materializing every package in the run.
 */
private data class DeclaredLicenseOverride(
    val packageId: EntityID<Long>,
    val curatedSpdxExpression: String?,
    val curatedUnmappedLicenses: Set<String>
)

/**
 * Compute curated `processedDeclaredLicense` overrides for the packages in [ortRunId] whose curations
 * may change the declared-license SPDX expression or unmapped licenses. Only packages with at least one
 * curation carrying a non-empty `declaredLicenseMapping` are materialized; everything else stays on the
 * SQL fast path.
 */
private fun computeDeclaredLicenseOverrides(
    ortRunId: Long,
    curationsMap: Map<Identifier, List<Pair<String, PackageCuration>>>
): List<DeclaredLicenseOverride> {
    val affectedIdentifierIds = curationsMap
        .filterValues { list -> list.any { (_, c) -> c.data.declaredLicenseMapping.isNotEmpty() } }
        .keys
        .mapNotNull { IdentifierDao.findByIdentifier(it)?.id }

    if (affectedIdentifierIds.isEmpty()) return emptyList()

    val affectedPackageIds = PackagesTable.joinAnalyzerTables()
        .select(PackagesTable.id)
        .where {
            (AnalyzerJobsTable.ortRunId eq ortRunId) and
                (PackagesTable.identifierId inList affectedIdentifierIds)
        }
        .map { it[PackagesTable.id] }

    return if (affectedPackageIds.isEmpty()) {
        emptyList()
    } else {
        PackageDao.find { PackagesTable.id inList affectedPackageIds }.map { dao ->
            val modelPackage = dao.mapToModel()
            val curations = curationsMap[modelPackage.identifier].orEmpty()
            val curatedOrt = curations.fold(OrtCuratedPackage(modelPackage.mapToOrt())) { acc, (_, c) ->
                c.mapToOrt().apply(acc)
            }
            val curatedDeclared = curatedOrt.metadata.mapToModel().processedDeclaredLicense
            DeclaredLicenseOverride(
                packageId = dao.id,
                curatedSpdxExpression = curatedDeclared.spdxExpression,
                curatedUnmappedLicenses = curatedDeclared.unmappedLicenses
            )
        }
    }
}

private fun PackagesTable.joinAnalyzerTables() =
    innerJoin(PackagesAnalyzerRunsTable)
        .innerJoin(AnalyzerRunsTable)
        .innerJoin(AnalyzerJobsTable)
