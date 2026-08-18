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
import org.eclipse.apoapsis.ortserver.shared.apimodel.Identifier

import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.union

/**
 * A service for querying snippet findings and their snippet sources for ORT runs.
 */
class SnippetFindingService(private val db: Database) {
    /**
     * Return package provenances with snippet findings for the ORT run with the given [ortRunId].
     *
     * Each entry carries the package identifier, provenance details (artifact or VCS), and the number of snippet
     * findings. The result is paged and sorted according to [parameters].
     */
    fun getProvenancesForRun(
        ortRunId: Long,
        parameters: ListQueryParameters
    ): ListQueryResult<SnippetFindingProvenance> = db.blockingQuery {
        val provenances = buildProvenancesQuery(ortRunId).alias("provenances")

        val provenanceId = provenances[provenanceIdAlias]
        val identifierType = provenances[identifierTypeAlias]
        val identifierNamespace = provenances[identifierNamespaceAlias]
        val identifierName = provenances[identifierNameAlias]
        val identifierVersion = provenances[identifierVersionAlias]
        val artifactUrl = provenances[artifactUrlAlias]
        val vcsType = provenances[vcsTypeAlias]
        val vcsUrl = provenances[vcsUrlAlias]
        val vcsRevision = provenances[vcsRevisionAlias]

        val query = provenances
            .join(ScanResultsTable, JoinType.INNER, provenanceId, ScanResultsTable.id)
            .join(ScanSummariesTable, JoinType.INNER, ScanResultsTable.scanSummaryId, ScanSummariesTable.id)
            .join(SnippetFindingsTable, JoinType.INNER, ScanSummariesTable.id, SnippetFindingsTable.scanSummaryId)
            .select(
                provenanceId,
                identifierType,
                identifierNamespace,
                identifierName,
                identifierVersion,
                artifactUrl,
                vcsType,
                vcsUrl,
                vcsRevision,
                snippetFindingCountAlias
            )
            .groupBy(
                provenanceId,
                identifierType,
                identifierNamespace,
                identifierName,
                identifierVersion,
                artifactUrl,
                vcsType,
                vcsUrl,
                vcsRevision
            )

        val sortExpressions = parameters.sortFields.map { orderField ->
            val sortExpression = when (orderField.name) {
                "name" -> identifierName
                "namespace" -> identifierNamespace
                "version" -> identifierVersion
                "type" -> identifierType
                else -> throw QueryParametersException("Unsupported sort field: '${orderField.name}'.")
            }

            sortExpression to orderField.direction.toSortOrder()
        }

        val totalCount = query.count()

        sortExpressions.forEach(query::orderBy)
        query.orderBy(provenanceId to SortOrder.ASC)
        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        ListQueryResult(
            data = query.map { row ->
                val rowArtifactUrl = row.getOrNull(artifactUrl)
                val rowVcsUrl = row.getOrNull(vcsUrl)

                SnippetFindingProvenance(
                    id = row[provenanceId].value,
                    identifier = Identifier(
                        type = row[identifierType],
                        namespace = row[identifierNamespace],
                        name = row[identifierName],
                        version = row[identifierVersion]
                    ),
                    provenanceType = when {
                        rowArtifactUrl != null -> "ARTIFACT"
                        rowVcsUrl != null -> "REPOSITORY"
                        else -> "UNKNOWN"
                    },
                    snippetFindingCount = row[snippetFindingCountAlias],
                    artifactUrl = rowArtifactUrl,
                    vcsType = row.getOrNull(vcsType),
                    vcsUrl = rowVcsUrl,
                    vcsRevision = row.getOrNull(vcsRevision)
                )
            },
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

private val provenanceIdAlias = ScanResultsTable.id.alias("provenance_id")
private val identifierTypeAlias = IdentifiersTable.type.alias("identifier_type")
private val identifierNamespaceAlias = IdentifiersTable.namespace.alias("identifier_namespace")
private val identifierNameAlias = IdentifiersTable.name.alias("identifier_name")
private val identifierVersionAlias = IdentifiersTable.version.alias("identifier_version")
private val artifactUrlAlias = ScanResultsTable.artifactUrl.alias("artifact_url")
private val vcsTypeAlias = ScanResultsTable.vcsType.alias("vcs_type")
private val vcsUrlAlias = ScanResultsTable.vcsUrl.alias("vcs_url")
private val vcsRevisionAlias = ScanResultsTable.vcsRevision.alias("vcs_revision")
private val snippetFindingCountAlias = Count(SnippetFindingsTable.id).alias("snippet_finding_count")

private val provenanceColumns = listOf(
    provenanceIdAlias,
    identifierTypeAlias,
    identifierNamespaceAlias,
    identifierNameAlias,
    identifierVersionAlias,
    artifactUrlAlias,
    vcsTypeAlias,
    vcsUrlAlias,
    vcsRevisionAlias
)

private fun buildProvenancesQuery(ortRunId: Long) =
    buildProvenancesQuery(buildDirectProvenancesJoin(), ortRunId)
        .union(buildProvenancesQuery(buildNestedProvenancesJoin(), ortRunId))

private fun buildProvenancesQuery(join: Join, ortRunId: Long) =
    join.select(provenanceColumns).where { ScannerJobsTable.ortRunId eq ortRunId }

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
