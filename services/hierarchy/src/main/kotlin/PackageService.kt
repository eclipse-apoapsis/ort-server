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
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RepositoryConfigurationsPackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RepositoryConfigurationsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.PackageFilters
import org.eclipse.apoapsis.ortserver.model.runs.PackageRunData
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField

import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere

/**
 * A service to interact with packages.
 */
class PackageService(private val db: Database) {
    suspend fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filters: PackageFilters = PackageFilters()
    ): ListQueryResult<PackageRunData> = db.dbQuery {
        val packages = PackagesTable.joinAnalyzerTables()
            .innerJoin(IdentifiersTable)
            .innerJoin(ProcessedDeclaredLicensesTable)
            .select(PackagesTable.columns)
            .where { AnalyzerJobsTable.ortRunId eq ortRunId }

        val curations = RepositoryConfigurationsTable
            .innerJoin(RepositoryConfigurationsPackageCurationsTable)
            .innerJoin(PackageCurationsTable)
            .innerJoin(PackageCurationDataTable)
            .innerJoin(IdentifiersTable)
            .innerJoin(PackagesTable)
            .select(listOf(PackagesTable.id, PackageCurationsTable.id) + PackageCurationDataTable.columns)
            .where(RepositoryConfigurationsTable.ortRunId eq ortRunId)
            .andWhere { PackagesTable.id inList (packages.map { it[PackagesTable.id].value }) }
            .distinct()
            .groupBy { it[PackagesTable.id].value }
            .mapValues { rows -> rows.value.map { PackageCurationDataDao.wrapRow(it).mapToModel() } }

        val shortestPaths = ShortestDependencyPathsTable
            .innerJoin(PackagesTable)
            .innerJoin(AnalyzerRunsTable)
            .innerJoin(AnalyzerJobsTable)
            .select(ShortestDependencyPathsTable.columns.plus(PackagesTable.id))
            .where(AnalyzerJobsTable.ortRunId eq ortRunId)
            .andWhere { PackagesTable.id inList (packages.map { it[PackagesTable.id].value }) }
            .groupBy { it[PackagesTable.id].value }
            .mapValues { rows -> rows.value.map { ShortestDependencyPathDao.wrapRow(it).mapToModel() } }

        val packageResults = packages.map { pkg ->
            val pkgId = pkg[PackagesTable.id].value
            PackageRunData(
                pkgId = pkgId,
                pkg = applyCuration(
                    pkg = PackageDao.wrapRow(pkg).mapToModel(),
                    curationData = curations.getOrDefault(pkgId, emptyList()).firstOrNull() ?: PackageCurationData()
                ),
                shortestDependencyPaths = shortestPaths.getOrDefault(pkgId, emptyList())
            )
        }.filter(filters).sort(parameters.sortFields)

        ListQueryResult(
            limitPackageResults(parameters.limit, parameters.offset, packageResults),
            parameters,
            packageResults.size.toLong()
        )
    }

    private fun applyCuration(pkg: Package, curationData: PackageCurationData): Package {
        return Package(
            purl = curationData.purl ?: pkg.purl,
            cpe = curationData.cpe ?: pkg.cpe,
            authors = curationData.authors ?: pkg.authors,
            declaredLicenses = pkg.declaredLicenses,
            description = curationData.description ?: pkg.description,
            homepageUrl = curationData.homepageUrl ?: pkg.homepageUrl,
            binaryArtifact = curationData.binaryArtifact ?: pkg.binaryArtifact,
            sourceArtifact = curationData.sourceArtifact ?: pkg.sourceArtifact,
            vcs = pkg.vcs,
            vcsProcessed = pkg.vcsProcessed,
            isMetadataOnly = curationData.isMetadataOnly ?: pkg.isMetadataOnly,
            isModified = curationData.isModified ?: pkg.isModified,
            identifier = pkg.identifier,
            processedDeclaredLicense = pkg.processedDeclaredLicense
        )
    }

    private fun List<PackageRunData>.filter(filters: PackageFilters): List<PackageRunData> {
        var filtered = this

        filters.purl?.let {
            filtered = filtered.filter{ pkg ->
                pkg.pkg.purl.contains(filters.purl?.value?.trim().toString(), ignoreCase = true)
            }
        }

        filters.identifier?.let {
            filtered = filtered.filter { pkg ->
                pkg.pkg.identifier
                    .concatenate()
                    .contains(filters.identifier?.value?.trim().toString(), ignoreCase = true)
            }
        }

        filters.processedDeclaredLicense?.let {
            filtered = filtered.filter { pkg ->
                filters.processedDeclaredLicense?.value?.joinToString(prefix = "(?i)", separator = "|")?.toRegex()
                    ?.let { regex ->
                        pkg.pkg.processedDeclaredLicense.spdxExpression?.contains(regex)?.or(false)
                    } == true
            }
        }
        return filtered
    }

    private fun List<PackageRunData>.sort(sortFields: List<OrderField>): List<PackageRunData> {
        val comparators = mutableListOf<Comparator<PackageRunData>>()
        sortFields.forEach { sortParam ->
            when (sortParam.name) {
                "purl" -> comparators.add(getPurlComparator(sortParam.direction))
                "identifier" -> comparators.addAll(getIdentifierComparators(sortParam.direction))
                "processedDeclaredLicense" -> comparators.add(
                    getProcessedDeclaredLicenseComparator(sortParam.direction)
                )
                else -> throw QueryParametersException("Unsupported field for sorting: '${sortParam.name}'.")
            }
        }
        return this.sortedWith(getMultistageComparator(comparators))
    }

    private fun limitPackageResults(
        limit: Int?,
        offset: Long?,
        packageResults: List<PackageRunData>
    ): List<PackageRunData> {
        val listOffset = (offset ?: 0).toInt()
        val listLimit = (limit ?: packageResults.size).toInt() + listOffset
        return packageResults.subList(listOffset, listLimit)
    }

    private fun Identifier.concatenate(): String =
        "${type}:${if (namespace.isEmpty()) "" else "$namespace/"}" +
            "${name}@${version}"

    private fun getNullComparator(): Comparator<PackageRunData> {
        return Comparator { a, b -> 0 }
    }

    private fun getPurlComparator(dir: OrderDirection): Comparator<PackageRunData> {
        return Comparator { a, b ->
            when (dir) {
                OrderDirection.ASCENDING -> a.pkg.purl.compareTo(b.pkg.purl, ignoreCase = true)
                OrderDirection.DESCENDING -> b.pkg.purl.compareTo(a.pkg.purl, ignoreCase = true)
            }
        }
    }

    private fun getIdentifierComparators(dir: OrderDirection): List<Comparator<PackageRunData>> {
        when (dir) {
            OrderDirection.ASCENDING -> {
                return listOf(
                    Comparator { a, b ->
                        a.pkg.identifier.type.compareTo(b.pkg.identifier.type, ignoreCase = true)
                    },
                    Comparator { a, b ->
                        a.pkg.identifier.namespace.compareTo(b.pkg.identifier.namespace, ignoreCase = true)
                    },
                    Comparator { a, b ->
                        a.pkg.identifier.name.compareTo(b.pkg.identifier.name, ignoreCase = true)
                    },
                    Comparator { a, b ->
                        a.pkg.identifier.version.compareTo(b.pkg.identifier.version, ignoreCase = true)
                    }
                )
            }

            OrderDirection.DESCENDING -> {
                return listOf(
                    Comparator { a, b ->
                        b.pkg.identifier.type.compareTo(a.pkg.identifier.type, ignoreCase = true)
                    },
                    Comparator { a, b ->
                       b.pkg.identifier.namespace.compareTo(a.pkg.identifier.namespace, ignoreCase = true)
                    },
                    Comparator { a, b ->
                        b.pkg.identifier.name.compareTo(a.pkg.identifier.name, ignoreCase = true)
                    },
                    Comparator { a, b ->
                        b.pkg.identifier.version.compareTo(a.pkg.identifier.version, ignoreCase = true)
                    }
                )
            }
        }
    }

    private fun getProcessedDeclaredLicenseComparator(dir: OrderDirection): Comparator<PackageRunData> {
        return Comparator { a, b ->
            when (dir) {
                OrderDirection.ASCENDING -> a.pkg.processedDeclaredLicense.spdxExpression.toString()
                    .compareTo(b.pkg.processedDeclaredLicense.spdxExpression.toString(), ignoreCase = true)
                OrderDirection.DESCENDING -> b.pkg.processedDeclaredLicense.spdxExpression.toString()
                    .compareTo(a.pkg.processedDeclaredLicense.spdxExpression.toString(), ignoreCase = true)
            }
        }
    }

    private fun getMultistageComparator(comparators: List<Comparator<PackageRunData>>): Comparator<PackageRunData> {
        if (comparators.isEmpty()) {
            return getNullComparator()
        } else {
            val multiStageComparator = comparators[0]
            for (comparator in comparators.drop(1)) {
                multiStageComparator.then(comparator)
            }

            return multiStageComparator
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
