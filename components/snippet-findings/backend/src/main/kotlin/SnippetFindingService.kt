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

package org.eclipse.apoapsis.ortserver.components.snippetfindings

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceSubRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsSnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.utils.toSortOrder
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection

import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select

/**
 * A service for querying snippet findings and their snippet sources for ORT runs.
 */
class SnippetFindingService(private val db: Database) {
    /**
     * Return all package provenances associated with the scanner run for the ORT run with the given [ortRunId].
     *
     * Each entry carries the package identifier and provenance details (artifact or VCS). The result is paged and
     * sorted according to [parameters].
     */
    fun getProvenancesForRun(
        ortRunId: Long,
        parameters: ListQueryParameters
    ): ListQueryResult<SnippetFindingProvenance> = db.blockingQuery {
        // Two separate queries are needed because sub-repository scan results are not linked via
        // ScanResultPackageProvenancesTable — they can only be reached through NestedProvenancesTable.
        val directProvenances = queryProvenances(buildDirectProvenancesJoin(), ortRunId)
        val nestedProvenances = queryProvenances(buildNestedProvenancesJoin(), ortRunId)

        // Merge, giving direct matches priority (a scan result should not appear on both paths,
        // but deduplicate by id just to be safe).
        val mergedProvenances = mutableMapOf<Long, SnippetFindingProvenance>()
        (directProvenances + nestedProvenances).forEach { mergedProvenances.putIfAbsent(it.id, it) }

        // Attach snippet finding counts so callers can see how many findings each provenance has.
        val counts = countSnippetFindingsByProvenanceId(mergedProvenances.keys.toList())
        var result = mergedProvenances.values.map { it.copy(snippetFindingCount = counts[it.id] ?: 0L) }

        // Sort in Kotlin because the two query paths cannot share a single ORDER BY clause.
        // TODO: Improve this to sort and page in the database because loading all rows to sort and limit them in
        // memory is inefficient.
        for (orderField in parameters.sortFields.reversed()) {
            val comparator: Comparator<SnippetFindingProvenance> = when (orderField.name) {
                "name" -> compareBy { it.identifier.name }
                "namespace" -> compareBy { it.identifier.namespace }
                "version" -> compareBy { it.identifier.version }
                "type" -> compareBy { it.identifier.type }
                else -> throw QueryParametersException("Unsupported sort field: '${orderField.name}'.")
            }
            result = result.sortedWith(
                if (orderField.direction == OrderDirection.ASCENDING) comparator else comparator.reversed()
            )
        }

        val totalCount = result.size.toLong()
        val offset = (parameters.offset ?: 0L).toInt()
        val limit = parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT

        ListQueryResult(
            data = result.drop(offset).take(limit),
            params = parameters,
            totalCount = totalCount
        )
    }

    /**
     * Return the snippet findings for the given [provenanceId] within the ORT run with the given [ortRunId].
     *
     * The result contains one entry per snippet finding together with the number of matching upstream snippets. The
     * result is paged and sorted according to [parameters].
     */
    fun getSnippetFindingsForRun(
        ortRunId: Long,
        provenanceId: Long,
        parameters: ListQueryParameters
    ): ListQueryResult<SnippetFinding> = db.blockingQuery {
        val snippetCount = Count(SnippetFindingsSnippetsTable.snippetId)
        val totalCount = Count(SnippetFindingsTable.id).over()

        val query = buildQueryContext()
            .join(
                SnippetFindingsSnippetsTable,
                JoinType.LEFT,
                SnippetFindingsTable.id,
                SnippetFindingsSnippetsTable.snippetFindingId
            )
            .select(
                SnippetFindingsTable.id,
                SnippetFindingsTable.path,
                SnippetFindingsTable.startLine,
                SnippetFindingsTable.endLine,
                snippetCount,
                totalCount
            )
            .where {
                (ScannerJobsTable.ortRunId eq ortRunId) and
                        (ScanResultsTable.id eq provenanceId)
            }
            .groupBy(
                SnippetFindingsTable.id,
                SnippetFindingsTable.path,
                SnippetFindingsTable.startLine,
                SnippetFindingsTable.endLine
            )

        parameters.sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()

            when (orderField.name) {
                "path" -> query.orderBy(SnippetFindingsTable.path to sortOrder)
                "snippetCount" -> query.orderBy(snippetCount to sortOrder)
                else -> throw QueryParametersException("Unsupported sort field: '${orderField.name}'.")
            }
        }

        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        val rows = query.toList()

        ListQueryResult(
            data = rows.map { row ->
                SnippetFinding(
                    id = row[SnippetFindingsTable.id].value,
                    path = row[SnippetFindingsTable.path],
                    startLine = row[SnippetFindingsTable.startLine],
                    endLine = row[SnippetFindingsTable.endLine],
                    snippetCount = row[snippetCount]
                )
            },
            params = parameters,
            totalCount = rows.firstOrNull()?.get(totalCount) ?: 0L
        )
    }

    /**
     * Return the upstream snippets for the snippet finding with the given [snippetFindingId] in the ORT run with the
     * given [ortRunId].
     *
     * The result is paged and sorted according to [parameters].
     */
    fun getSnippetsForSnippetFinding(
        ortRunId: Long,
        snippetFindingId: Long,
        parameters: ListQueryParameters
    ): ListQueryResult<SnippetSource> = db.blockingQuery {
        val totalCount = Count(SnippetsTable.id).over()

        val query = SnippetFindingsSnippetsTable
            .join(SnippetsTable, JoinType.INNER, SnippetFindingsSnippetsTable.snippetId, SnippetsTable.id)
            .join(
                buildQueryContext(),
                JoinType.INNER,
                SnippetFindingsSnippetsTable.snippetFindingId,
                SnippetFindingsTable.id
            )
            .join(RemoteArtifactsTable, JoinType.LEFT, SnippetsTable.artifactId, RemoteArtifactsTable.id)
            .join(VcsInfoTable, JoinType.LEFT, SnippetsTable.vcsId, VcsInfoTable.id)
            .select(
                SnippetsTable.purl,
                SnippetsTable.path,
                SnippetsTable.startLine,
                SnippetsTable.endLine,
                SnippetsTable.license,
                SnippetsTable.score,
                RemoteArtifactsTable.url,
                VcsInfoTable.type,
                VcsInfoTable.url,
                VcsInfoTable.revision,
                VcsInfoTable.path,
                totalCount
            )
            .where {
                (SnippetFindingsTable.id eq snippetFindingId) and
                        (ScannerJobsTable.ortRunId eq ortRunId)
            }

        parameters.sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()

            when (orderField.name) {
                "purl" -> query.orderBy(SnippetsTable.purl to sortOrder)
                "score" -> query.orderBy(SnippetsTable.score to sortOrder)
                "license" -> query.orderBy(SnippetsTable.license to sortOrder)
                else -> throw QueryParametersException("Unsupported sort field: '${orderField.name}'.")
            }
        }

        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        val rows = query.toList()

        ListQueryResult(
            data = rows.map { row ->
                SnippetSource(
                    purl = row[SnippetsTable.purl],
                    path = row[SnippetsTable.path],
                    startLine = row[SnippetsTable.startLine],
                    endLine = row[SnippetsTable.endLine],
                    license = row[SnippetsTable.license],
                    score = row[SnippetsTable.score],
                    artifactUrl = row.getOrNull(RemoteArtifactsTable.url),
                    vcsType = row.getOrNull(VcsInfoTable.type),
                    vcsUrl = row.getOrNull(VcsInfoTable.url),
                    vcsRevision = row.getOrNull(VcsInfoTable.revision),
                    vcsPath = row.getOrNull(VcsInfoTable.path)
                )
            },
            params = parameters,
            totalCount = rows.firstOrNull()?.get(totalCount) ?: 0L
        )
    }

    /**
     * Return whether the package provenance with the given [provenanceId] belongs to the ORT run with the given
     * [ortRunId].
     */
    fun hasProvenanceForRun(ortRunId: Long, provenanceId: Long): Boolean = db.blockingQuery {
        val condition = { (ScannerJobsTable.ortRunId eq ortRunId) and (ScanResultsTable.id eq provenanceId) }
        buildDirectProvenancesJoin().select(ScanResultsTable.id).where(condition).limit(1).toList().isNotEmpty() ||
                buildNestedProvenancesJoin().select(ScanResultsTable.id).where(condition).limit(1).toList().isNotEmpty()
    }

    /**
     * Return whether the snippet finding with the given [snippetFindingId] exists in the ORT run with the given
     * [ortRunId].
     */
    fun hasSnippetFindingForRun(ortRunId: Long, snippetFindingId: Long): Boolean = db.blockingQuery {
        buildQueryContext()
            .select(SnippetFindingsTable.id)
            .where {
                (SnippetFindingsTable.id eq snippetFindingId) and
                        (ScannerJobsTable.ortRunId eq ortRunId)
            }
            .limit(1)
            .toList()
            .isNotEmpty()
    }
}

/**
 * Return the number of snippet findings per scan result ID for the given [scanResultIds].
 *
 * Scan result IDs with no findings are absent from the map (callers should default to 0).
 */
private fun countSnippetFindingsByProvenanceId(scanResultIds: List<Long>): Map<Long, Long> {
    if (scanResultIds.isEmpty()) return emptyMap()
    val count = Count(SnippetFindingsTable.id)
    return SnippetFindingsTable
        .innerJoin(ScanSummariesTable)
        .join(ScanResultsTable, JoinType.INNER, ScanSummariesTable.id, ScanResultsTable.scanSummaryId)
        .select(ScanResultsTable.id, count)
        .where { scanResultIds.map { ScanResultsTable.id eq it }.reduce(Op<Boolean>::or) }
        .groupBy(ScanResultsTable.id)
        .associate { it[ScanResultsTable.id].value to it[count] }
}

/**
 * Build the common join for snippet findings queries.
 *
 * Goes directly from the snippet finding's scan result to the scanner run via [ScannerRunsScanResultsTable]. This
 * works for both root-package scan results and nested sub-repository scan results.
 */
private fun buildQueryContext(): Join = SnippetFindingsTable
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

private val provenanceColumns = listOf(
    ScanResultsTable.id,
    IdentifiersTable.type,
    IdentifiersTable.namespace,
    IdentifiersTable.name,
    IdentifiersTable.version,
    ScanResultsTable.artifactUrl,
    ScanResultsTable.vcsType,
    ScanResultsTable.vcsUrl,
    ScanResultsTable.vcsRevision
)

private fun queryProvenances(join: Join, ortRunId: Long): List<SnippetFindingProvenance> =
    join.select(provenanceColumns)
        .where { ScannerJobsTable.ortRunId eq ortRunId }
        .map { row ->
            val artifactUrl = row.getOrNull(ScanResultsTable.artifactUrl)
            val vcsUrl = row.getOrNull(ScanResultsTable.vcsUrl)
            SnippetFindingProvenance(
                id = row[ScanResultsTable.id].value,
                identifier = Identifier(
                    type = row[IdentifiersTable.type],
                    namespace = row[IdentifiersTable.namespace],
                    name = row[IdentifiersTable.name],
                    version = row[IdentifiersTable.version]
                ),
                provenanceType = when {
                    artifactUrl != null -> "ARTIFACT"
                    vcsUrl != null -> "REPOSITORY"
                    else -> "UNKNOWN"
                },
                snippetFindingCount = 0L, // filled in by getProvenancesForRun via countSnippetFindingsByProvenanceId
                artifactUrl = artifactUrl,
                vcsType = row.getOrNull(ScanResultsTable.vcsType),
                vcsUrl = vcsUrl,
                vcsRevision = row.getOrNull(ScanResultsTable.vcsRevision)
            )
        }

/**
 * Build the join for scan results directly linked to a package provenance via [ScanResultPackageProvenancesTable].
 * This covers the root package VCS or artifact — i.e. `ScannerRun.ScanResult[].provenance` entries that match the
 * package's own `vcsId` or `artifactId`.
 */
private fun buildDirectProvenancesJoin() =
    ScannerJobsTable
        .join(ScannerRunsTable, JoinType.INNER, ScannerJobsTable.id, ScannerRunsTable.scannerJobId)
        .join(
            ScannerRunsScanResultsTable,
            JoinType.INNER,
            ScannerRunsTable.id,
            ScannerRunsScanResultsTable.scannerRunId
        )
        .join(ScanResultsTable, JoinType.INNER, ScannerRunsScanResultsTable.scanResultId, ScanResultsTable.id)
        .join(
            ScanResultPackageProvenancesTable,
            JoinType.INNER,
            ScanResultsTable.id,
            ScanResultPackageProvenancesTable.scanResultId
        )
        .join(
            PackageProvenancesTable,
            JoinType.INNER,
            ScanResultPackageProvenancesTable.packageProvenanceId,
            PackageProvenancesTable.id
        )
        .join(IdentifiersTable, JoinType.INNER, PackageProvenancesTable.identifierId, IdentifiersTable.id)

/**
 * Build the join for scan results that correspond to **nested sub-repositories** (git submodules).
 *
 * `linkScanResultToPackageProvenances` in the scanner worker only links scan results to the package provenance
 * when the scan result's VCS matches the package's own `vcsId`. Sub-repository scan results have a different URL
 * and are therefore not in [ScanResultPackageProvenancesTable]. To find them, we match the scan result's VCS fields
 * against [NestedProvenanceSubRepositoriesTable], then navigate back to the package provenance and identifier.
 */
private fun buildNestedProvenancesJoin() =
    ScannerJobsTable
        .join(ScannerRunsTable, JoinType.INNER, ScannerJobsTable.id, ScannerRunsTable.scannerJobId)
        .join(
            ScannerRunsScanResultsTable,
            JoinType.INNER,
            ScannerRunsTable.id,
            ScannerRunsScanResultsTable.scannerRunId
        )
        .join(ScanResultsTable, JoinType.INNER, ScannerRunsScanResultsTable.scanResultId, ScanResultsTable.id)
        // Match the scan result's VCS info to a sub-repository entry by value (no FK exists).
        .join(VcsInfoTable, JoinType.INNER, null, null) {
            (VcsInfoTable.type eq ScanResultsTable.vcsType) and
                    (VcsInfoTable.url eq ScanResultsTable.vcsUrl) and
                    (VcsInfoTable.revision eq ScanResultsTable.vcsRevision) and
                    (VcsInfoTable.path eq "")
        }
        .join(
            NestedProvenanceSubRepositoriesTable,
            JoinType.INNER,
            NestedProvenanceSubRepositoriesTable.vcsId,
            VcsInfoTable.id
        )
        .join(
            NestedProvenancesTable,
            JoinType.INNER,
            NestedProvenanceSubRepositoriesTable.nestedProvenanceId,
            NestedProvenancesTable.id
        )
        .join(
            PackageProvenancesTable,
            JoinType.INNER,
            PackageProvenancesTable.nestedProvenanceId,
            NestedProvenancesTable.id
        )
        // Scope the package provenance to the current scanner run.
        .join(
            ScannerRunsPackageProvenancesTable,
            JoinType.INNER,
            ScannerRunsPackageProvenancesTable.packageProvenanceId,
            PackageProvenancesTable.id
        ) { ScannerRunsPackageProvenancesTable.scannerRunId eq ScannerRunsTable.id }
        .join(IdentifiersTable, JoinType.INNER, PackageProvenancesTable.identifierId, IdentifiersTable.id)
