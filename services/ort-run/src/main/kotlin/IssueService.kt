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

import com.github.michaelbull.result.getOr

import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.IssueResolutionsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.ResolvedIssuesTable
import org.eclipse.apoapsis.ortserver.dao.utils.applyFilter
import org.eclipse.apoapsis.ortserver.dao.utils.applyILike
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.dao.utils.severitySortRank
import org.eclipse.apoapsis.ortserver.model.CountByCategory
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.IssueFilter
import org.eclipse.apoapsis.ortserver.model.runs.repository.AppliedIssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.services.ResourceNotFoundException
import org.eclipse.apoapsis.ortserver.services.utils.toSortOrder

import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

private class IssueQueryContext(
    val query: Query,
    val identifierExpression: ExpressionWithColumnType<String>,
    val purlColumn: ExpressionWithColumnType<String>,
    val statusExpression: ExpressionWithColumnType<String>
)

/**
 * A service to manage and get information about issues.
 */
class IssueService(
    private val db: Database,
    private val ortRunService: OrtRunService,
    private val issueResolutionService: IssueResolutionService
) {
    /** Return a page of issues for the given ORT run after applying the requested filters. */
    fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        issuesFilter: IssueFilter = IssueFilter()
    ): ListQueryResult<Issue> {
        val ortRun = ortRunService.getOrtRun(ortRunId) ?: throw ResourceNotFoundException(
            "ORT run with ID $ortRunId not found."
        )

        return db.blockingQuery {
            val context = buildListForOrtRunIdQueryContext(ortRunId, issuesFilter)

            val totalCount = context.query.count()
            val ortRunIssueIds = fetchPagedIssueIds(context, parameters)

            if (ortRunIssueIds.isEmpty()) {
                return@blockingQuery ListQueryResult(emptyList(), parameters, totalCount)
            }

            val issueRows = fetchIssueRows(ortRunIssueIds)
            val resolutionsByOrtRunIssueId = fetchResolutionsByOrtRunIssueId(ortRunIssueIds)
            val serverResolutions = getServerResolutions(ortRun.repositoryId)
            val unappliedResolutions = getUnappliedResolutions(serverResolutions, resolutionsByOrtRunIssueId)

            val identifierIds = issueRows
                .mapNotNullTo(mutableSetOf()) { it.getOrNull(OrtRunsIssuesTable.identifierId)?.value }

            val purlByIdentifierId = getPurlByIdentifierIdForOrtRun(ortRunId, identifierIds)
            val issues = assembleIssues(
                issueRows,
                resolutionsByOrtRunIssueId,
                serverResolutions,
                unappliedResolutions,
                purlByIdentifierId
            )

            ListQueryResult(data = issues, params = parameters, totalCount = totalCount)
        }
    }

    private fun buildListForOrtRunIdQueryContext(
        ortRunId: Long,
        issuesFilter: IssueFilter
    ): IssueQueryContext {
        val riOrtRunId = OrtRunsIssuesTable.ortRunId.alias("issue_ri_ort_run_id")

        // Null identifier IDs are excluded from the pairs query before this is passed to the shared PURL builder.
        @Suppress("UNCHECKED_CAST")
        val identifierId = OrtRunsIssuesTable.identifierId as ExpressionWithColumnType<EntityID<Long>>
        val riIdentifierId = identifierId.alias("issue_ri_identifier_id")
        val runIdPairs = OrtRunsIssuesTable
            .select(riOrtRunId, riIdentifierId)
            .where {
                (OrtRunsIssuesTable.ortRunId eq ortRunId) and OrtRunsIssuesTable.identifierId.isNotNull()
            }
            .withDistinct()
            .alias("issue_run_identifier_pairs")

        val purls = buildPurlByRunIdentifier(
            runIdPairs,
            runIdPairs[riOrtRunId],
            runIdPairs[riIdentifierId]
        )
        val purlColumn = purls.alias[purls.purl]

        val identifierExpression = concat(
            IdentifiersTable.type,
            stringLiteral(":"),
            IdentifiersTable.namespace,
            stringLiteral(":"),
            IdentifiersTable.name,
            stringLiteral(":"),
            IdentifiersTable.version
        )

        val resolvedIssueIdsSubquery = ResolvedIssuesTable
            .select(ResolvedIssuesTable.ortRunIssueId)
            .where { ResolvedIssuesTable.ortRunId eq ortRunId }
        val statusExpression = Case()
            .When(OrtRunsIssuesTable.id inSubQuery resolvedIssueIdsSubquery, stringLiteral("Resolved"))
            .Else(stringLiteral("Unresolved"))

        val query = OrtRunsIssuesTable
            .innerJoin(IssuesTable, { issueId }, { id })
            .join(IdentifiersTable, JoinType.LEFT, OrtRunsIssuesTable.identifierId, IdentifiersTable.id)
            .join(purls.alias, JoinType.LEFT) {
                (OrtRunsIssuesTable.ortRunId eq purls.alias[purls.ortRunId]) and
                    (OrtRunsIssuesTable.identifierId eq purls.alias[purls.identifierId])
            }
            .select(OrtRunsIssuesTable.id)
            .where { OrtRunsIssuesTable.ortRunId eq ortRunId }

        when (issuesFilter.resolved) {
            true -> query.andWhere { OrtRunsIssuesTable.id inSubQuery resolvedIssueIdsSubquery }
            false -> query.andWhere { not(OrtRunsIssuesTable.id inSubQuery resolvedIssueIdsSubquery) }
            null -> {}
        }

        issuesFilter.identifier?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            query.andWhere { identifierExpression.applyILike(filter.value) }
        }

        issuesFilter.purl?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for PURL filter: ${filter.operator}"
            }

            query.andWhere { purlColumn.applyILike(filter.value) }
        }

        issuesFilter.severity?.let { filter ->
            query.andWhere { IssuesTable.severity.applyFilter(filter.operator, filter.value) }
        }

        return IssueQueryContext(query, identifierExpression, purlColumn, statusExpression)
    }

    private fun fetchPagedIssueIds(context: IssueQueryContext, parameters: ListQueryParameters): List<Long> {
        val query = context.query
        val sortFields = parameters.sortFields.ifEmpty {
            listOf(OrderField("timestamp", OrderDirection.DESCENDING))
        }

        sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()
            when (orderField.name) {
                "timestamp" -> query.orderBy(OrtRunsIssuesTable.timestamp to sortOrder)

                "source" -> query.orderBy(IssuesTable.issueSource to sortOrder)

                "message" -> query.orderBy(IssuesTable.message to sortOrder)

                "severity" -> query.orderBy(IssuesTable.severity.severitySortRank() to sortOrder)

                "affectedPath" -> query.orderBy(IssuesTable.affectedPath to sortOrder)

                "identifier" -> {
                    query.orderBy(IdentifiersTable.type to sortOrder)
                    query.orderBy(IdentifiersTable.namespace to sortOrder)
                    query.orderBy(IdentifiersTable.name to sortOrder)
                    query.orderBy(IdentifiersTable.version to sortOrder)
                }

                "purl" -> {
                    val purlWithIdentifierFallback = Case()
                        .When(
                            context.purlColumn.isNull() or (context.purlColumn eq ""),
                            context.identifierExpression
                        )
                        .Else(context.purlColumn)

                    query.orderBy(purlWithIdentifierFallback to sortOrder)
                }

                "status" -> query.orderBy(context.statusExpression to sortOrder)

                "worker" -> query.orderBy(OrtRunsIssuesTable.worker to sortOrder)

                else -> throw QueryParametersException("Unknown sort field '${orderField.name}'.")
            }
        }

        query.orderBy(OrtRunsIssuesTable.id to SortOrder.ASC)
        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        return query.map { it[OrtRunsIssuesTable.id].value }
    }

    private fun fetchIssueRows(ortRunIssueIds: List<Long>): List<ResultRow> =
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
            .sortedBy { ortRunIssueIds.indexOf(it[OrtRunsIssuesTable.id].value) }

    private fun fetchResolutionsByOrtRunIssueId(
        ortRunIssueIds: List<Long>
    ): Map<Long, List<AppliedIssueResolution>> =
        ResolvedIssuesTable
            .innerJoin(IssueResolutionsTable, { issueResolutionId }, { id })
            .select(
                ResolvedIssuesTable.ortRunIssueId,
                IssueResolutionsTable.message,
                IssueResolutionsTable.messageHash,
                IssueResolutionsTable.reason,
                IssueResolutionsTable.comment,
                IssueResolutionsTable.resolutionSource
            )
            .where { ResolvedIssuesTable.ortRunIssueId inList ortRunIssueIds }
            .groupBy(
                { it[ResolvedIssuesTable.ortRunIssueId].value },
                {
                    AppliedIssueResolution(
                        message = it[IssueResolutionsTable.message],
                        messageHash = if (it[IssueResolutionsTable.resolutionSource] == ResolutionSource.SERVER) {
                            it[IssueResolutionsTable.messageHash]
                                ?: calculateResolutionMessageHash(it[IssueResolutionsTable.message])
                        } else {
                            null
                        },
                        reason = it[IssueResolutionsTable.reason],
                        comment = it[IssueResolutionsTable.comment],
                        source = it[IssueResolutionsTable.resolutionSource],
                        isDeleted = false
                    )
                }
            )

    private fun getServerResolutions(repositoryId: Long): List<IssueResolution> =
        issueResolutionService.getResolutionsForRepository(RepositoryId(repositoryId))
            .getOr(emptyList())

    private fun getUnappliedResolutions(
        serverResolutions: List<IssueResolution>,
        resolutionsByOrtRunIssueId: Map<Long, List<AppliedIssueResolution>>
    ): List<IssueResolution> {
        val appliedServerResolutions = resolutionsByOrtRunIssueId.values.flatten()
            .mapNotNullTo(mutableSetOf()) { resolution ->
                resolution.takeIf { it.source == ResolutionSource.SERVER }?.let {
                    IssueResolution(
                        message = it.message,
                        messageHash = it.messageHash,
                        reason = it.reason,
                        comment = it.comment,
                        source = it.source
                    )
                }
            }

        return serverResolutions - appliedServerResolutions
    }

    private fun assembleIssues(
        issueRows: List<ResultRow>,
        resolutionsByOrtRunIssueId: Map<Long, List<AppliedIssueResolution>>,
        serverResolutions: List<IssueResolution>,
        unappliedResolutions: List<IssueResolution>,
        purlByIdentifierId: Map<Long, String>
    ): List<Issue> =
        issueRows.map { row ->
            val ortRunIssueId = row[OrtRunsIssuesTable.id].value
            val identifierId = row[OrtRunsIssuesTable.identifierId]?.value

            val identifier = identifierId?.let {
                Identifier(
                    type = row[IdentifiersTable.type],
                    namespace = row[IdentifiersTable.namespace],
                    name = row[IdentifiersTable.name],
                    version = row[IdentifiersTable.version]
                )
            }

            Issue(
                timestamp = row[OrtRunsIssuesTable.timestamp],
                source = row[IssuesTable.issueSource],
                message = row[IssuesTable.message],
                severity = row[IssuesTable.severity],
                affectedPath = row.getOrNull(IssuesTable.affectedPath),
                identifier = identifier,
                worker = row.getOrNull(OrtRunsIssuesTable.worker),
                resolutions = resolutionsByOrtRunIssueId[ortRunIssueId].orEmpty().map { resolution ->
                    resolution.copy(
                        isDeleted = resolution.source == ResolutionSource.SERVER &&
                            IssueResolution(
                                message = resolution.message,
                                messageHash = resolution.messageHash,
                                reason = resolution.reason,
                                comment = resolution.comment,
                                source = resolution.source
                            ) !in serverResolutions
                    )
                },
                unappliedResolutions = unappliedResolutions,
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
