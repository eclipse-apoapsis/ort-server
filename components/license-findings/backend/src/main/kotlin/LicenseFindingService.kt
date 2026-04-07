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

package org.eclipse.apoapsis.ortserver.components.licensefindings

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.LicenseFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.utils.applyILike
import org.eclipse.apoapsis.ortserver.dao.utils.toSortOrder
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult

import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

/**
 * A service for querying detected licenses and their findings for ORT runs.
 */
class LicenseFindingService(private val db: Database) {
    /**
     * Return the detected licenses for the ORT run with the given [ortRunId].
     *
     * The result contains one entry per detected license together with the number of distinct packages that contain the
     * license. The result can be filtered by [licenseFilter] and paged and sorted according to [parameters].
     */
    fun getDetectedLicensesForRun(
        ortRunId: Long,
        parameters: ListQueryParameters,
        licenseFilter: String?
    ): ListQueryResult<DetectedLicense> = db.blockingQuery {
        val ctx = buildQueryContext(includeAnalyzerData = false)
        val packageCount = Count(IdentifiersTable.id, distinct = true)
        // Window function gives the total group count in the same query, avoiding running the query twice.
        val totalCount = Count(LicenseFindingsTable.license).over()

        val query = ctx.join
            .select(LicenseFindingsTable.license, packageCount, totalCount)
            .where { (ScannerJobsTable.ortRunId eq ortRunId) and ctx.provenanceCondition() }
            .groupBy(LicenseFindingsTable.license)

        licenseFilter?.let { query.andWhere { LicenseFindingsTable.license.applyILike(it) } }

        parameters.sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()

            when (orderField.name) {
                "license" -> query.orderBy(LicenseFindingsTable.license to sortOrder)
                "packageCount" -> query.orderBy(packageCount to sortOrder)
                else -> throw QueryParametersException("Unsupported sort field: '${orderField.name}'.")
            }
        }

        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        val rows = query.toList()

        ListQueryResult(
            data = rows.map { row ->
                DetectedLicense(
                    license = row[LicenseFindingsTable.license],
                    packageCount = row[packageCount]
                )
            },
            params = parameters,
            totalCount = rows.firstOrNull()?.get(totalCount) ?: 0L
        )
    }

    /**
     * Return the packages in the ORT run with the given [ortRunId] that contain the provided [license].
     *
     * The result can be filtered by [identifierFilter] and [purlFilter], and paged and sorted according to
     * [parameters].
     */
    fun getPackagesWithDetectedLicenseForRun(
        ortRunId: Long,
        license: String,
        parameters: ListQueryParameters,
        identifierFilter: String?,
        purlFilter: String?
    ): ListQueryResult<PackageIdentifier> = db.blockingQuery {
        val ctx = buildQueryContext()
        val purl = PackagesTable.purl.min().alias("purl")
        val totalCount = Count(IdentifiersTable.type).over()

        val query = ctx.join
            .select(
                IdentifiersTable.type,
                IdentifiersTable.namespace,
                IdentifiersTable.name,
                IdentifiersTable.version,
                purl,
                totalCount
            )
            .where {
                (ScannerJobsTable.ortRunId eq ortRunId) and
                    (LicenseFindingsTable.license eq license) and
                    ctx.provenanceCondition()
            }
            .groupBy(
                IdentifiersTable.type,
                IdentifiersTable.namespace,
                IdentifiersTable.name,
                IdentifiersTable.version
            )

        identifierFilter?.let { query.andWhere { identifierExpression().applyILike(it) } }
        purlFilter?.let { query.andWhere { PackagesTable.purl.applyILike(it) } }

        parameters.sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()

            when (orderField.name) {
                "identifier" -> {
                    query.orderBy(IdentifiersTable.type to sortOrder)
                    query.orderBy(IdentifiersTable.namespace to sortOrder)
                    query.orderBy(IdentifiersTable.name to sortOrder)
                    query.orderBy(IdentifiersTable.version to sortOrder)
                }

                "purl" -> query.orderBy(purl to sortOrder)

                else -> throw QueryParametersException("Unsupported sort field: '${orderField.name}'.")
            }
        }

        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        val rows = query.toList()

        ListQueryResult(
            data = rows.map { row ->
                PackageIdentifier(
                    identifier = buildIdentifier(row),
                    purl = row.getOrNull(purl)
                )
            },
            params = parameters,
            totalCount = rows.firstOrNull()?.get(totalCount) ?: 0L
        )
    }

    /**
     * Return the file-level license findings for the given [license] and package [identifier] in the ORT run with the
     * given [ortRunId].
     *
     * The result is paged and sorted according to [parameters].
     */
    fun getLicenseFindingsForRun(
        ortRunId: Long,
        license: String,
        identifier: String,
        parameters: ListQueryParameters
    ): ListQueryResult<LicenseFinding> = db.blockingQuery {
        val ctx = buildQueryContext(includeAnalyzerData = false)

        fun findingsQuery() = ctx.join
            .select(
                LicenseFindingsTable.path,
                LicenseFindingsTable.startLine,
                LicenseFindingsTable.endLine,
                LicenseFindingsTable.score,
                ScanResultsTable.scannerName,
                ScanResultsTable.scannerVersion
            )
            .where {
                (ScannerJobsTable.ortRunId eq ortRunId) and
                    (LicenseFindingsTable.license eq license) and
                    (identifierExpression() eq identifier) and
                    ctx.provenanceCondition()
            }
            .withDistinct()

        val totalCount = findingsQuery().count()
        val query = findingsQuery()

        parameters.sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()

            when (orderField.name) {
                "path" -> query.orderBy(LicenseFindingsTable.path to sortOrder)
                "startLine" -> query.orderBy(LicenseFindingsTable.startLine to sortOrder)
                "endLine" -> query.orderBy(LicenseFindingsTable.endLine to sortOrder)
                "score" -> query.orderBy(LicenseFindingsTable.score to sortOrder)
                else -> throw QueryParametersException("Unsupported sort field: '${orderField.name}'.")
            }
        }

        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        val rows = query.toList()

        ListQueryResult(
            data = rows.map { row ->
                LicenseFinding(
                    path = row[LicenseFindingsTable.path],
                    startLine = row[LicenseFindingsTable.startLine],
                    endLine = row[LicenseFindingsTable.endLine],
                    score = row.getOrNull(LicenseFindingsTable.score),
                    scanner = buildString {
                        append(row[ScanResultsTable.scannerName])

                        row[ScanResultsTable.scannerVersion].takeUnless(String::isBlank)?.let { version ->
                            append(' ')
                            append(version)
                        }
                    }
                )
            },
            params = parameters,
            totalCount = totalCount
        )
    }
}

private class QueryContext(
    val join: Join,
    private val artifactAlias: Alias<RemoteArtifactsTable>,
    private val vcsAlias: Alias<VcsInfoTable>
) {
    fun provenanceCondition(): Op<Boolean> =
        (
            ScanResultsTable.artifactUrl.isNotNull() and
                (ScanResultsTable.artifactUrl eq artifactAlias[RemoteArtifactsTable.url]) and
                (ScanResultsTable.artifactHash eq artifactAlias[RemoteArtifactsTable.hashValue]) and
                (ScanResultsTable.artifactHashAlgorithm eq artifactAlias[RemoteArtifactsTable.hashAlgorithm])
            ) or (
            ScanResultsTable.vcsUrl.isNotNull() and
                (ScanResultsTable.vcsType eq vcsAlias[VcsInfoTable.type]) and
                (ScanResultsTable.vcsUrl eq vcsAlias[VcsInfoTable.url]) and
                (ScanResultsTable.vcsRevision eq vcsAlias[VcsInfoTable.revision]) and
                (vcsAlias[VcsInfoTable.path] eq stringLiteral(""))
            )
}

/**
 * Build the common query context for license findings queries.
 *
 * The [includeAnalyzerData] flag controls whether the four `LEFT JOIN`s for purl lookup are included.
 * Set it to `false` when the purl is not needed, as `packages_analyzer_runs` is a junction table that
 * multiplies rows by the number of analyzer runs a package has been part of, forcing expensive
 * `COUNT(DISTINCT)` to compensate.
 */
private fun buildQueryContext(includeAnalyzerData: Boolean = true): QueryContext {
    val artifactAlias = RemoteArtifactsTable.alias("artifact")
    val vcsAlias = VcsInfoTable.alias("vcs")

    val baseJoin = LicenseFindingsTable
        .innerJoin(ScanSummariesTable)
        .join(ScanResultsTable, JoinType.INNER, ScanSummariesTable.id, ScanResultsTable.scanSummaryId)
        .join(
            ScannerRunsScanResultsTable,
            JoinType.INNER,
            ScanResultsTable.id,
            ScannerRunsScanResultsTable.scanResultId
        )
        .join(ScannerRunsTable, JoinType.INNER, ScannerRunsScanResultsTable.scannerRunId, ScannerRunsTable.id)
        .join(ScannerJobsTable, JoinType.INNER, ScannerRunsTable.scannerJobId, ScannerJobsTable.id)
        .join(
            ScannerRunsPackageProvenancesTable,
            JoinType.INNER,
            ScannerRunsTable.id,
            ScannerRunsPackageProvenancesTable.scannerRunId
        )
        .join(
            PackageProvenancesTable,
            JoinType.INNER,
            ScannerRunsPackageProvenancesTable.packageProvenanceId,
            PackageProvenancesTable.id
        )
        .join(IdentifiersTable, JoinType.INNER, PackageProvenancesTable.identifierId, IdentifiersTable.id)
        .join(artifactAlias, JoinType.LEFT, PackageProvenancesTable.artifactId, artifactAlias[RemoteArtifactsTable.id])
        .join(vcsAlias, JoinType.LEFT, PackageProvenancesTable.vcsId, vcsAlias[VcsInfoTable.id])

    val join = if (includeAnalyzerData) {
        baseJoin
            .join(AnalyzerJobsTable, JoinType.LEFT) {
                AnalyzerJobsTable.ortRunId eq ScannerJobsTable.ortRunId
            }
            .join(AnalyzerRunsTable, JoinType.LEFT) { AnalyzerRunsTable.analyzerJobId eq AnalyzerJobsTable.id }
            .join(PackagesAnalyzerRunsTable, JoinType.LEFT) {
                PackagesAnalyzerRunsTable.analyzerRunId eq AnalyzerRunsTable.id
            }
            .join(PackagesTable, JoinType.LEFT) {
                (PackagesTable.id eq PackagesAnalyzerRunsTable.packageId) and
                    (PackagesTable.identifierId eq PackageProvenancesTable.identifierId)
            }
    } else {
        baseJoin
    }

    return QueryContext(join, artifactAlias, vcsAlias)
}

private fun identifierExpression() = CustomFunction<String>(
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

private fun buildIdentifier(row: ResultRow): Identifier =
    Identifier(
        type = row[IdentifiersTable.type],
        namespace = row[IdentifiersTable.namespace],
        name = row[IdentifiersTable.name],
        version = row[IdentifiersTable.version]
    )
