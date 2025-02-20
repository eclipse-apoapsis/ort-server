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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackageDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ShortestDependencyPathDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ShortestDependencyPathsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.listCustomQueryCustomOrders
import org.eclipse.apoapsis.ortserver.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.model.runs.PackageWithShortestDependencyPaths
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult

import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and

/**
 * A service to interact with packages.
 */
class PackageService(private val db: Database) {
    suspend fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<PackageWithShortestDependencyPaths> = db.dbQuery {
        val orders = mutableListOf<Pair<Expression<*>, SortOrder>>()

        parameters.sortFields.forEach {
            val sortOrder = it.direction.toSortOrder()
            when (it.name) {
                "identifier" -> {
                    orders += IdentifiersTable.type to sortOrder
                    orders += IdentifiersTable.namespace to sortOrder
                    orders += IdentifiersTable.name to sortOrder
                    orders += IdentifiersTable.version to sortOrder
                }
                "purl" -> orders += PackagesTable.purl to sortOrder
                "processedDeclaredLicense" -> orders += ProcessedDeclaredLicensesTable.spdxExpression to sortOrder
                else -> throw QueryParametersException("Unsupported field for sorting: '${it.name}'.")
            }
        }

        val listQueryResult =
            listCustomQueryCustomOrders(parameters, orders, ResultRow::toPackageWithShortestDependencyPaths) {
                PackagesTable.joinAnalyzerTables()
                    .innerJoin(IdentifiersTable)
                    .innerJoin(ProcessedDeclaredLicensesTable)
                    .select(PackagesTable.columns)
                    .where { AnalyzerJobsTable.ortRunId eq ortRunId }
            }

        val data = listQueryResult.data.map { pkg ->
            val shortestPaths = ShortestDependencyPathsTable
                .innerJoin(PackagesTable)
                .innerJoin(AnalyzerRunsTable)
                .innerJoin(AnalyzerJobsTable)
                .select(ShortestDependencyPathsTable.columns)
                .where { (AnalyzerJobsTable.ortRunId eq ortRunId) and (PackagesTable.id eq pkg.pkgId) }
                .map { ShortestDependencyPathDao.wrapRow(it).mapToModel() }

            pkg.copy(
                shortestDependencyPaths = shortestPaths
            )
        }

        ListQueryResult(data, parameters, listQueryResult.totalCount)
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
}

private fun ResultRow.toPackageWithShortestDependencyPaths(): PackageWithShortestDependencyPaths =
    PackageWithShortestDependencyPaths(
        pkg = PackageDao.wrapRow(this).mapToModel(),
        pkgId = get(PackagesTable.id).value,
        // Temporarily set the shortestDependencyPaths into an empty list, as they will be added in a subsequent step.
        shortestDependencyPaths = emptyList()
    )

private fun PackagesTable.joinAnalyzerTables() =
    innerJoin(PackagesAnalyzerRunsTable)
        .innerJoin(AnalyzerRunsTable)
        .innerJoin(AnalyzerJobsTable)
