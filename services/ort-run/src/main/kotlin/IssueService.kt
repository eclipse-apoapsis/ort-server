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

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.CountByCategory
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.services.ResourceNotFoundException

import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.innerJoin

import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Issue as OrtIssue
import org.ossreviewtoolkit.model.OrtResult

/**
 * A service to manage and get information about issues.
 */
class IssueService(private val db: Database, private val ortRunService: OrtRunService) {
    suspend fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<Issue> {
        val ortRun = ortRunService.getOrtRun(ortRunId) ?: throw ResourceNotFoundException(
            "ORT run with ID $ortRunId not found."
        )

        val ortResult = ortRunService.generateOrtResult(ortRun, failIfRepoInfoMissing = false)

        val issues = collectIssues(ortRunId, ortResult)

        val issuesWithResolutions = issues.map { issue ->
            val matchingResolutions = ortResult.getResolutions().issues.filter { resolution ->
                val resolutionPattern = resolution.message.toRegex()
                resolutionPattern.containsMatchIn(issue.message)
            }

            issue.copy(resolutions = matchingResolutions.map { it.mapToModel() })
        }

        val sortFields = parameters.sortFields.ifEmpty {
            listOf(OrderField("timestamp", OrderDirection.DESCENDING))
        }

        return ListQueryResult(
            issuesWithResolutions.sort(sortFields).paginate(parameters),
            parameters,
            issuesWithResolutions.size.toLong()
        )
    }

    /** Count issues found in provided ORT runs. */
    suspend fun countForOrtRunIds(vararg ortRunIds: Long): Long = db.dbQuery {
        OrtRunsIssuesTable
            .select(OrtRunsIssuesTable.id)
            .where { OrtRunsIssuesTable.ortRunId inList ortRunIds.asList() }
            .count()
    }

    /**
     * Count overall issues by severity for provided ORT runs.
     */
    suspend fun countBySeverityForOrtRunIds(vararg ortRunIds: Long): CountByCategory<Severity> = db.dbQuery {
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
     * Collect issues from the ORT result and the database for the given [ortRunId].
     */
    private suspend fun collectIssues(ortRunId: Long, ortResult: OrtResult): Set<Issue> {
        val ortWorkerIssues = listOf(
            ortResult.getAnalyzerIssues() to AnalyzerRunDao.ISSUE_WORKER_TYPE,
            ortResult.getAdvisorIssues() to AdvisorRunDao.ISSUE_WORKER_TYPE,
            ortResult.getScannerIssues() to ScannerRunDao.ISSUE_WORKER_TYPE
        ).flatMap { (issuesByIdentifier, workerType) ->
            // While the database contains almost all issues, some issues like dependency graph issues are only
            // available in the ORT result. To ensure that no issues are lost, also collect them from the ORT result.
            collectIssuesFromWorker(issuesByIdentifier, workerType)
        }.map { issue ->
            // Normalize timestamp to database precision to help with duplicate detection
            issue.copy(timestamp = issue.timestamp.toDatabasePrecision())
        }

        val dbIssues = db.dbQuery { IssueDao.createFromQuery(createOrtRunIssuesQuery(ortRunId)) }

        return (ortWorkerIssues + dbIssues).toSet()
    }

    private fun collectIssuesFromWorker(
        issueMap: Map<OrtIdentifier, Set<OrtIssue>>,
        workerType: String
    ) = issueMap.flatMap { (identifier, ortIssues) ->
        ortIssues.map { ortIssue ->
            ortIssue.mapToModel(identifier = identifier.mapToModel(), worker = workerType)
        }
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
}

internal fun Identifier.toConcatenatedString() = "$type $namespace $name $version"

/**
 * Sort the list of issues by the given [sortFields], also using the hash code of the issue as a second sort criterion
 * to get a stable sort order. Although the API supports having more than one sort order field, this implementation
 * only supports a single sort field.
 */
internal fun Collection<Issue>.sort(sortFields: List<OrderField>): List<Issue> {
    require(sortFields.isNotEmpty()) {
        "At least one sort field must be defined."
    }

    // Explicitly only support a single sort field
    val sortField = sortFields.first()

    val comparator: Comparator<Issue> = when (sortField.name) {
        "timestamp" -> compareBy { it.timestamp }
        "source" -> compareBy { it.source }
        "message" -> compareBy { it.message }
        "severity" -> compareBy { it.severity }
        "affectedPath" -> compareBy { it.affectedPath }
        "identifier" -> compareBy { it.identifier?.toConcatenatedString() }
        "worker" -> compareBy { it.worker }
        else -> throw QueryParametersException("Unknown sort field '${sortField.name}'.")
    }

    return sortedWith(comparator.thenBy { issue -> issue.hashCode() })
        .let { if (sortField.direction == OrderDirection.DESCENDING) it.reversed() else it }
}

/**
 * Paginate the list of issues by the given [parameters].
 */
internal fun List<Issue>.paginate(parameters: ListQueryParameters): List<Issue> {
    val offset = parameters.offset ?: 0L
    val limit = parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT

    return drop(offset.toInt()).take(limit)
}
