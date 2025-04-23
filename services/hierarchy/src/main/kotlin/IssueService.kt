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
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.model.CountByCategory
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection.DESCENDING
import org.eclipse.apoapsis.ortserver.model.util.OrderField

import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.innerJoin

/**
 * A service to manage and get information about issues.
 */
class IssueService(private val db: Database) {
    suspend fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<Issue> = db.dbQuery {
        val ortRunIssuesQuery = createOrtRunIssuesQuery(ortRunId)
        val scanSummaryIssuesQuery = createScanSummaryIssuesQuery(ortRunId)

        val totalCount = ortRunIssuesQuery.count() + scanSummaryIssuesQuery.count()
        val totalIssues =
            IssueDao.createFromQuery(ortRunIssuesQuery) +
            IssueDao.createFromQuery(scanSummaryIssuesQuery)

        // There always has to be some sort order defined, else the rows would be returned in random order
        // and tests that rely on a deterministic order would fail.
        val sortFields = parameters.sortFields.ifEmpty {
            listOf(OrderField("timestamp", DESCENDING))
        }

        ListQueryResult(
            totalIssues.sort(sortFields).paginate(parameters),
            parameters,
            totalCount
        )
    }

    /** Count issues found in provided ORT runs. */
    suspend fun countForOrtRunIds(vararg ortRunIds: Long): Long = db.dbQuery {
        val cntOrtRunsIssues = OrtRunsIssuesTable
            .select(OrtRunsIssuesTable.id)
            .where { OrtRunsIssuesTable.ortRunId inList ortRunIds.asList() }
            .count()

        val cntScanSummariesIssues = createScanSummaryIssuesJoin()
            .select(ScanSummariesIssuesTable.id)
            .where { ScannerJobsTable.ortRunId inList ortRunIds.asList() }
            .count()

        cntOrtRunsIssues + cntScanSummariesIssues
    }

    /**
     * Count issues by severity for provided ORT runs, based on the [OrtRunsIssuesTable].
     */
    private suspend fun countOrtRunsIssuesBySeverity(vararg ortRunIds: Long): CountByCategory<Severity> = db.dbQuery {
        val countAlias = Count(OrtRunsIssuesTable.id, true)

        val severityToCountMap = Severity.entries.associateWithTo(mutableMapOf()) { 0L }

        OrtRunsIssuesTable
            .innerJoin(IssuesTable)
            .select(IssuesTable.severity, countAlias)
            .where { OrtRunsIssuesTable.ortRunId inList ortRunIds.asList() }
            .groupBy(IssuesTable.severity)
            .map { row ->
                severityToCountMap.put(row[IssuesTable.severity], row[countAlias])
            }

        CountByCategory(severityToCountMap)
    }

    /**
     * Count issues by severity for provided ORT runs, based on the [ScanSummariesIssuesTable].
     */
    private suspend fun countScanSummariesIssuesBySeverity(vararg ortRunIds: Long): CountByCategory<Severity> =
        db.dbQuery {
            val countAlias = Count(ScanSummariesIssuesTable.id, true)

            val severityToCountMap = Severity.entries.associateWithTo(mutableMapOf()) { 0L }

            createScanSummaryIssuesJoin()
                .select(IssuesTable.severity, countAlias)
                .where { ScannerJobsTable.ortRunId inList ortRunIds.asList() }
                .groupBy(IssuesTable.severity)
                .map { row ->
                    severityToCountMap.put(row[IssuesTable.severity], row[countAlias])
                }

            CountByCategory(severityToCountMap)
        }

    /**
     * Count overall issues by severity for provided ORT runs.
     */
    suspend fun countBySeverityForOrtRunIds(vararg ortRunIds: Long): CountByCategory<Severity> {
        val countByCategoryForOrtRunsIssues = countOrtRunsIssuesBySeverity(*ortRunIds)
        val countByCategoryForScanSummariesIssues = countScanSummariesIssuesBySeverity(*ortRunIds)

        val mergedSeverityCounts = countByCategoryForOrtRunsIssues.map.toMutableMap()

        countByCategoryForScanSummariesIssues.map.forEach { (severity, count) ->
            mergedSeverityCounts[severity] = mergedSeverityCounts.getOrDefault(severity, 0L) + count
        }

        return CountByCategory(mergedSeverityCounts)
    }

    private fun createOrtRunIssuesQuery(ortRunId: Long): Query {
        val issuesIdentifiersJoin = OrtRunsIssuesTable
            .innerJoin(IssuesTable, { issueId }, { id })
            .join(IdentifiersTable, JoinType.LEFT, OrtRunsIssuesTable.identifierId, IdentifiersTable.id)
        return issuesIdentifiersJoin.select(
            OrtRunsIssuesTable.timestamp,
            IssuesTable.issueSource,
            IssuesTable.message,
            IssuesTable.severity,
            IssuesTable.affectedPath,
            IdentifiersTable.type,
            IdentifiersTable.name,
            IdentifiersTable.namespace,
            IdentifiersTable.version,
            OrtRunsIssuesTable.worker
        ).where { OrtRunsIssuesTable.ortRunId eq ortRunId }
    }

    /**
     * Create a [Join][org.jetbrains.exposed.sql.Join] relation to get the issues from the scan summaries.
     */
    private fun createScanSummaryIssuesJoin() =
        ScannerJobsTable
            .innerJoin(ScannerRunsTable, { ScannerJobsTable.id }, { scannerJobId })
            .innerJoin(ScannerRunsScanResultsTable, { ScannerRunsTable.id }, { scannerRunId })
            .innerJoin(ScanResultsTable, { ScannerRunsScanResultsTable.scanResultId }, { ScanResultsTable.id })
            .innerJoin(ScanSummariesTable, { ScanResultsTable.scanSummaryId }, { ScanSummariesTable.id })
            .innerJoin(ScanSummariesIssuesTable, { ScanSummariesTable.id }, { scanSummaryId })
            .innerJoin(IssuesTable, { ScanSummariesIssuesTable.issueId }, { IssuesTable.id })

    /**
     * Create a [Query] to get the issues from the scan summaries. In order to be able to directly merge the results
     * to the issues return of the query created by [createOrtRunIssuesQuery], the columns have to be the same,
     * and for this reason some colum values are set to NULL.
     */
    private fun createScanSummaryIssuesQuery(ortRunId: Long) =
        createScanSummaryIssuesJoin().select(
            ScanSummariesIssuesTable.timestamp,
            IssuesTable.issueSource,
            IssuesTable.message,
            IssuesTable.severity,
            IssuesTable.affectedPath,
            Op.nullOp<Unit>().alias("identifier_type"),
            Op.nullOp<Unit>().alias("identifier_name"),
            Op.nullOp<Unit>().alias("identifier_namespace"),
            Op.nullOp<Unit>().alias("identifier_version"),
            Op.nullOp<Unit>().alias("worker")
        ).where { ScannerJobsTable.ortRunId eq ortRunId }
    }

/**
 * Convert this [OrderDirection] constant to the corresponding [SortOrder].
 */
fun OrderDirection.toSortOrder(): SortOrder =
    when (this) {
        OrderDirection.ASCENDING -> SortOrder.ASC
        OrderDirection.DESCENDING -> SortOrder.DESC
    }

internal fun Identifier.toConcatenatedString() = "$type $namespace $name $version"

/**
 * Sort the list of issues by the given [sortFields], also using the hash code of the issue as a second sort criterion
 * to get a stable sort order. Although the API supports to have more than one sort order field, this implementation
 * only supports a single sort field.
 */
internal fun List<Issue>.sort(sortFields: List<OrderField>): List<Issue> {
    require(sortFields.isNotEmpty()) {
        "At least one sort field must be defined."
    }

    // Explicitly only support a single sort field
    val sortField = sortFields.first()

    when (sortField.name) {
        "timestamp" -> compareBy<Issue> { it.timestamp }
        "source" -> compareBy<Issue> { it.source }
        "message" -> compareBy<Issue> { it.message }
        "severity" -> compareBy<Issue> { it.severity }
        "affectedPath" -> compareBy<Issue> { it.affectedPath }
        "identifier" -> compareBy<Issue> { it.identifier?.toConcatenatedString() }
        "worker" -> compareBy<Issue> { it.worker }
        else -> throw QueryParametersException("Unknown sort field '${sortField.name}'.")
    }.let {
        sortedWith(it.thenBy { issue -> issue.hashCode() })
    }.let {
        return if (sortField.direction == DESCENDING) it.reversed() else it
    }
}

/**
 * Paginate the list of issues by the given [parameters].
 */
internal fun List<Issue>.paginate(parameters: ListQueryParameters): List<Issue> {
    val offset = parameters.offset ?: 0L
    val limit = parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT

    return drop(offset.toInt()).take(limit)
}
