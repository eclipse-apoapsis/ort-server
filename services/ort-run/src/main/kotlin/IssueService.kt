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
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.IssueResolutionsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.ResolvedIssuesTable
import org.eclipse.apoapsis.ortserver.model.CountByCategory
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.IssueFilter
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.services.ResourceNotFoundException
import org.eclipse.apoapsis.ortserver.services.utils.toSortOrder

import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

/**
 * A service to manage and get information about issues.
 */
class IssueService(private val db: Database, private val ortRunService: OrtRunService) {
    fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        issuesFilter: IssueFilter = IssueFilter()
    ): ListQueryResult<Issue> {
        ortRunService.getOrtRun(ortRunId) ?: throw ResourceNotFoundException(
            "ORT run with ID $ortRunId not found."
        )

        return db.blockingQuery {
            val query = buildListForOrtRunIdQuery(ortRunId, issuesFilter)

            val totalCount = query.count()
            val ortRunIssueIds = fetchPagedIssueIds(query, parameters)

            if (ortRunIssueIds.isEmpty()) {
                return@blockingQuery ListQueryResult(emptyList(), parameters, totalCount)
            }

            val issueRowsById = fetchIssueRowsById(ortRunIssueIds)
            val resolutionsByOrtRunIssueId = fetchResolutionsByOrtRunIssueId(ortRunIssueIds)

            val identifierIds = ortRunIssueIds
                .mapNotNull { issueRowsById[it]?.getOrNull(OrtRunsIssuesTable.identifierId)?.value }
                .distinct()

            val purlByIdentifierId = getPurlByIdentifierIdForOrtRun(ortRunId, identifierIds)
            val issues = assembleIssues(
                ortRunIssueIds,
                issueRowsById,
                resolutionsByOrtRunIssueId,
                purlByIdentifierId
            )

            ListQueryResult(data = issues, params = parameters, totalCount = totalCount)
        }
    }

    private fun buildListForOrtRunIdQuery(ortRunId: Long, issuesFilter: IssueFilter): Query {
        val baseJoin = OrtRunsIssuesTable
            .innerJoin(IssuesTable, { issueId }, { id })
            .join(IdentifiersTable, JoinType.LEFT, OrtRunsIssuesTable.identifierId, IdentifiersTable.id)

        val query = baseJoin
            .select(OrtRunsIssuesTable.id)
            .where { OrtRunsIssuesTable.ortRunId eq ortRunId }

        val resolvedIssueIdsSubquery = ResolvedIssuesTable
            .select(ResolvedIssuesTable.ortRunIssueId)
            .where { ResolvedIssuesTable.ortRunId eq ortRunId }

        when (issuesFilter.resolved) {
            true -> query.andWhere { OrtRunsIssuesTable.id inSubQuery resolvedIssueIdsSubquery }
            false -> query.andWhere { not(OrtRunsIssuesTable.id inSubQuery resolvedIssueIdsSubquery) }
            null -> {}
        }

        return query
    }

    private fun fetchPagedIssueIds(query: Query, parameters: ListQueryParameters): List<Long> {
        val sortFields = parameters.sortFields.ifEmpty {
            listOf(OrderField("timestamp", OrderDirection.DESCENDING))
        }

        sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()
            when (orderField.name) {
                "timestamp" -> query.orderBy(OrtRunsIssuesTable.timestamp to sortOrder)

                "source" -> query.orderBy(IssuesTable.issueSource to sortOrder)

                "message" -> query.orderBy(IssuesTable.message to sortOrder)

                "severity" -> query.orderBy(IssuesTable.severity to sortOrder)

                "affectedPath" -> query.orderBy(IssuesTable.affectedPath to sortOrder)

                "identifier" -> {
                    query.orderBy(IdentifiersTable.type to sortOrder)
                    query.orderBy(IdentifiersTable.namespace to sortOrder)
                    query.orderBy(IdentifiersTable.name to sortOrder)
                    query.orderBy(IdentifiersTable.version to sortOrder)
                }

                "worker" -> query.orderBy(OrtRunsIssuesTable.worker to sortOrder)

                else -> throw QueryParametersException("Unknown sort field '${orderField.name}'.")
            }
        }

        query.orderBy(OrtRunsIssuesTable.id to SortOrder.ASC)
        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        return query.map { it[OrtRunsIssuesTable.id].value }
    }

    private fun fetchIssueRowsById(ortRunIssueIds: List<Long>): Map<Long, ResultRow> =
        OrtRunsIssuesTable
            .innerJoin(IssuesTable, { issueId }, { id })
            .join(IdentifiersTable, JoinType.LEFT, OrtRunsIssuesTable.identifierId, IdentifiersTable.id)
            .select(
                OrtRunsIssuesTable.id,
                OrtRunsIssuesTable.timestamp,
                OrtRunsIssuesTable.worker,
                OrtRunsIssuesTable.identifierId,
                IssuesTable.issueSource,
                IssuesTable.message,
                IssuesTable.severity,
                IssuesTable.affectedPath,
                IdentifiersTable.type,
                IdentifiersTable.namespace,
                IdentifiersTable.name,
                IdentifiersTable.version
            )
            .where { OrtRunsIssuesTable.id inList ortRunIssueIds }
            .associateBy { it[OrtRunsIssuesTable.id].value }

    private fun fetchResolutionsByOrtRunIssueId(
        ortRunIssueIds: List<Long>
    ): Map<Long, List<IssueResolution>> =
        ResolvedIssuesTable
            .innerJoin(IssueResolutionsTable, { issueResolutionId }, { id })
            .select(
                ResolvedIssuesTable.ortRunIssueId,
                IssueResolutionsTable.message,
                IssueResolutionsTable.reason,
                IssueResolutionsTable.comment
            )
            .where { ResolvedIssuesTable.ortRunIssueId inList ortRunIssueIds }
            .groupBy(
                { it[ResolvedIssuesTable.ortRunIssueId].value },
                {
                    IssueResolution(
                        message = it[IssueResolutionsTable.message],
                        reason = it[IssueResolutionsTable.reason],
                        comment = it[IssueResolutionsTable.comment]
                    )
                }
            )

    private fun assembleIssues(
        ortRunIssueIds: List<Long>,
        issueRowsById: Map<Long, ResultRow>,
        resolutionsByOrtRunIssueId: Map<Long, List<IssueResolution>>,
        purlByIdentifierId: Map<Long, String>
    ): List<Issue> =
        ortRunIssueIds.map { ortRunIssueId ->
            val row = issueRowsById.getValue(ortRunIssueId)

            val type = row.getOrNull(IdentifiersTable.type)
            val namespace = row.getOrNull(IdentifiersTable.namespace)
            val name = row.getOrNull(IdentifiersTable.name)
            val version = row.getOrNull(IdentifiersTable.version)
            val identifier = type?.let { safeType ->
                namespace?.let { safeNamespace ->
                    name?.let { safeName ->
                        version?.let { safeVersion ->
                            Identifier(safeType, safeNamespace, safeName, safeVersion)
                        }
                    }
                }
            }

            val identifierId = row.getOrNull(OrtRunsIssuesTable.identifierId)?.value

            Issue(
                timestamp = row[OrtRunsIssuesTable.timestamp],
                source = row[IssuesTable.issueSource],
                message = row[IssuesTable.message],
                severity = row[IssuesTable.severity],
                affectedPath = row.getOrNull(IssuesTable.affectedPath),
                identifier = identifier,
                worker = row.getOrNull(OrtRunsIssuesTable.worker),
                resolutions = resolutionsByOrtRunIssueId[ortRunIssueId].orEmpty(),
                purl = identifierId?.let { purlByIdentifierId[it] }
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

    /** Count unresolved issues found in provided ORT runs. */
    suspend fun countUnresolvedForOrtRunIds(vararg ortRunIds: Long): Long = db.dbQuery {
        val resolvedIssueIdsSubquery = ResolvedIssuesTable
            .select(ResolvedIssuesTable.ortRunIssueId)
            .where { ResolvedIssuesTable.ortRunId inList ortRunIds.asList() }

        OrtRunsIssuesTable
            .select(OrtRunsIssuesTable.id)
            .where {
                (OrtRunsIssuesTable.ortRunId inList ortRunIds.asList()) and
                    not(OrtRunsIssuesTable.id inSubQuery resolvedIssueIdsSubquery)
            }
            .count()
    }

    /** Count unresolved issues by severity for provided ORT runs. */
    suspend fun countUnresolvedBySeverityForOrtRunIds(vararg ortRunIds: Long): CountByCategory<Severity> = db.dbQuery {
        val countAlias = Count(OrtRunsIssuesTable.id, true)

        val severityToCountMap = Severity.entries.associateWithTo(mutableMapOf()) { 0L }

        val resolvedIssueIdsSubquery = ResolvedIssuesTable
            .select(ResolvedIssuesTable.ortRunIssueId)
            .where { ResolvedIssuesTable.ortRunId inList ortRunIds.asList() }

        OrtRunsIssuesTable
            .innerJoin(IssuesTable)
            .select(IssuesTable.severity, countAlias)
            .where {
                (OrtRunsIssuesTable.ortRunId inList ortRunIds.asList()) and
                    not(OrtRunsIssuesTable.id inSubQuery resolvedIssueIdsSubquery)
            }
            .groupBy(IssuesTable.severity)
            .map { row ->
                severityToCountMap.put(row[IssuesTable.severity], row[countAlias])
            }

        CountByCategory(severityToCountMap)
    }
}
