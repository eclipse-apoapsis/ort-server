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

import org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations.RuleViolationResolutionService
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.EvaluatorRunsRuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.EvaluatorRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.ResolvedRuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.RuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RuleViolationResolutionsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.applyFilter
import org.eclipse.apoapsis.ortserver.dao.utils.applyILike
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.dao.utils.severitySortRank
import org.eclipse.apoapsis.ortserver.model.CountByCategory
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.LicenseSource
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolationFilters
import org.eclipse.apoapsis.ortserver.model.runs.repository.AppliedRuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
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

private class RuleViolationQueryContext(
    val query: Query,
    val identifierExpression: ExpressionWithColumnType<String>,
    val purlColumn: ExpressionWithColumnType<String>,
    val statusExpression: ExpressionWithColumnType<String>
)

/**
 * A service to interact with rule violations.
 */
class RuleViolationService(
    private val db: Database,
    private val ortRunService: OrtRunService,
    private val ruleViolationResolutionService: RuleViolationResolutionService
) {
    /** Return the distinct rule names found in the given ORT run, sorted case-insensitively. */
    fun getRulesForOrtRunId(ortRunId: Long): List<String> {
        if (ortRunService.getOrtRun(ortRunId) == null) {
            throw ResourceNotFoundException("ORT run with ID $ortRunId not found.")
        }

        return db.blockingQuery {
            RuleViolationsTable
                .innerJoin(EvaluatorRunsRuleViolationsTable)
                .innerJoin(EvaluatorRunsTable)
                .innerJoin(EvaluatorJobsTable)
                .select(RuleViolationsTable.rule)
                .where { EvaluatorJobsTable.ortRunId eq ortRunId }
                .withDistinct()
                .map { it[RuleViolationsTable.rule] }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }

    /** Return a page of rule violations for the given ORT run after applying the requested filters. */
    fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        ruleViolationFilter: RuleViolationFilters = RuleViolationFilters()
    ): ListQueryResult<RuleViolation> {
        val ortRun = ortRunService.getOrtRun(ortRunId) ?: throw ResourceNotFoundException(
            "ORT run with ID $ortRunId not found."
        )

        return db.blockingQuery {
            val context = buildListForOrtRunIdQueryContext(ortRunId, ruleViolationFilter)

            val totalCount = context.query.count()
            val ruleViolationIds = fetchPagedRuleViolationIds(context, parameters)

            if (ruleViolationIds.isEmpty()) {
                return@blockingQuery ListQueryResult(emptyList(), parameters, totalCount)
            }

            val ruleViolationRows = fetchRuleViolationRows(ruleViolationIds)
            val resolutionsByRuleViolationId = fetchResolutionsByRuleViolationId(ortRunId, ruleViolationIds)
            val serverResolutions = getServerResolutions(ortRun.repositoryId)
            val unappliedResolutions = getUnappliedResolutions(serverResolutions, resolutionsByRuleViolationId)

            val identifierIds = ruleViolationRows
                .mapNotNullTo(mutableSetOf()) { it.getOrNull(RuleViolationsTable.identifierId)?.value }

            val purlByIdentifierId = getPurlByIdentifierIdForOrtRun(ortRunId, identifierIds)
            val ruleViolations = assembleRuleViolations(
                ruleViolationRows,
                resolutionsByRuleViolationId,
                serverResolutions,
                unappliedResolutions,
                purlByIdentifierId
            )

            ListQueryResult(data = ruleViolations, params = parameters, totalCount = totalCount)
        }
    }

    private fun buildListForOrtRunIdQueryContext(
        ortRunId: Long,
        ruleViolationFilter: RuleViolationFilters
    ): RuleViolationQueryContext {
        val riOrtRunId = EvaluatorJobsTable.ortRunId.alias("rule_violation_ri_ort_run_id")

        // Null identifier IDs are excluded from the pairs query before this is passed to the shared PURL builder.
        @Suppress("UNCHECKED_CAST")
        val identifierId = RuleViolationsTable.identifierId as ExpressionWithColumnType<EntityID<Long>>
        val riIdentifierId = identifierId.alias("rule_violation_ri_identifier_id")
        val runIdPairs = RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(riOrtRunId, riIdentifierId)
            .where {
                (EvaluatorJobsTable.ortRunId eq ortRunId) and RuleViolationsTable.identifierId.isNotNull()
            }
            .withDistinct()
            .alias("rule_violation_run_identifier_pairs")

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

        val resolvedRuleViolationIdsSubquery = ResolvedRuleViolationsTable
            .select(ResolvedRuleViolationsTable.ruleViolationId)
            .where { ResolvedRuleViolationsTable.ortRunId eq ortRunId }
        val statusExpression = Case()
            .When(RuleViolationsTable.id inSubQuery resolvedRuleViolationIdsSubquery, stringLiteral("Resolved"))
            .Else(stringLiteral("Unresolved"))

        val query = RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .join(IdentifiersTable, JoinType.LEFT, RuleViolationsTable.identifierId, IdentifiersTable.id)
            .join(purls.alias, JoinType.LEFT) {
                (EvaluatorJobsTable.ortRunId eq purls.alias[purls.ortRunId]) and
                    (RuleViolationsTable.identifierId eq purls.alias[purls.identifierId])
            }
            .select(RuleViolationsTable.id)
            .where { EvaluatorJobsTable.ortRunId eq ortRunId }

        when (ruleViolationFilter.resolved) {
            true -> query.andWhere { RuleViolationsTable.id inSubQuery resolvedRuleViolationIdsSubquery }
            false -> query.andWhere { not(RuleViolationsTable.id inSubQuery resolvedRuleViolationIdsSubquery) }
            null -> {}
        }

        ruleViolationFilter.identifier?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            query.andWhere { identifierExpression.applyILike(filter.value) }
        }

        ruleViolationFilter.purl?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for PURL filter: ${filter.operator}"
            }

            query.andWhere { purlColumn.applyILike(filter.value) }
        }

        ruleViolationFilter.severity?.let { filter ->
            query.andWhere { RuleViolationsTable.severity.applyFilter(filter.operator, filter.value) }
        }

        ruleViolationFilter.rule?.let { filter ->
            query.andWhere { RuleViolationsTable.rule.applyFilter(filter.operator, filter.value) }
        }

        return RuleViolationQueryContext(query, identifierExpression, purlColumn, statusExpression)
    }

    private fun fetchPagedRuleViolationIds(
        context: RuleViolationQueryContext,
        parameters: ListQueryParameters
    ): List<Long> {
        val query = context.query
        val sortFields = parameters.sortFields.ifEmpty {
            listOf(OrderField("rule", OrderDirection.ASCENDING))
        }

        sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()

            when (orderField.name) {
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

                "severity" -> query.orderBy(RuleViolationsTable.severity.severitySortRank() to sortOrder)

                "rule" -> query.orderBy(RuleViolationsTable.rule to sortOrder)

                else -> throw QueryParametersException("Unknown sort field '${orderField.name}'.")
            }
        }

        query.orderBy(RuleViolationsTable.id to SortOrder.ASC)
        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        return query.map { it[RuleViolationsTable.id].value }
    }

    private fun fetchRuleViolationRows(ruleViolationIds: List<Long>): List<ResultRow> =
        RuleViolationsTable
            .join(IdentifiersTable, JoinType.LEFT, RuleViolationsTable.identifierId, IdentifiersTable.id)
            .select(
                RuleViolationsTable.id,
                RuleViolationsTable.rule,
                RuleViolationsTable.identifierId,
                RuleViolationsTable.license,
                RuleViolationsTable.licenseSources,
                RuleViolationsTable.severity,
                RuleViolationsTable.message,
                RuleViolationsTable.howToFix,
                IdentifiersTable.type,
                IdentifiersTable.namespace,
                IdentifiersTable.name,
                IdentifiersTable.version
            )
            .where { RuleViolationsTable.id inList ruleViolationIds }
            .sortedBy { ruleViolationIds.indexOf(it[RuleViolationsTable.id].value) }

    private fun fetchResolutionsByRuleViolationId(
        ortRunId: Long,
        ruleViolationIds: List<Long>
    ): Map<Long, List<AppliedRuleViolationResolution>> =
        ResolvedRuleViolationsTable
            .innerJoin(RuleViolationResolutionsTable, { ruleViolationResolutionId }, { id })
            .select(
                ResolvedRuleViolationsTable.ruleViolationId,
                RuleViolationResolutionsTable.message,
                RuleViolationResolutionsTable.messageHash,
                RuleViolationResolutionsTable.reason,
                RuleViolationResolutionsTable.comment,
                RuleViolationResolutionsTable.resolutionSource
            )
            .where {
                (ResolvedRuleViolationsTable.ortRunId eq ortRunId) and
                    (ResolvedRuleViolationsTable.ruleViolationId inList ruleViolationIds)
            }
            .groupBy(
                { it[ResolvedRuleViolationsTable.ruleViolationId].value },
                {
                    AppliedRuleViolationResolution(
                        message = it[RuleViolationResolutionsTable.message],
                        messageHash = if (
                            it[RuleViolationResolutionsTable.resolutionSource] == ResolutionSource.SERVER
                        ) {
                            it[RuleViolationResolutionsTable.messageHash]
                                ?: calculateResolutionMessageHash(it[RuleViolationResolutionsTable.message])
                        } else {
                            null
                        },
                        reason = it[RuleViolationResolutionsTable.reason],
                        comment = it[RuleViolationResolutionsTable.comment],
                        source = it[RuleViolationResolutionsTable.resolutionSource],
                        isDeleted = false
                    )
                }
            )

    private fun getServerResolutions(repositoryId: Long): List<RuleViolationResolution> =
        ruleViolationResolutionService.getResolutionsForRepository(RepositoryId(repositoryId))
            .getOr(emptyList())

    private fun getUnappliedResolutions(
        serverResolutions: List<RuleViolationResolution>,
        resolutionsByRuleViolationId: Map<Long, List<AppliedRuleViolationResolution>>
    ): List<RuleViolationResolution> {
        val appliedServerResolutions = resolutionsByRuleViolationId.values.flatten()
            .mapNotNullTo(mutableSetOf()) { resolution ->
                resolution.takeIf { it.source == ResolutionSource.SERVER }?.let {
                    RuleViolationResolution(
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

    private fun assembleRuleViolations(
        ruleViolationRows: List<ResultRow>,
        resolutionsByRuleViolationId: Map<Long, List<AppliedRuleViolationResolution>>,
        serverResolutions: List<RuleViolationResolution>,
        unappliedResolutions: List<RuleViolationResolution>,
        purlByIdentifierId: Map<Long, String>
    ): List<RuleViolation> =
        ruleViolationRows.map { row ->
            val ruleViolationId = row[RuleViolationsTable.id].value
            val identifierId = row[RuleViolationsTable.identifierId]?.value

            val identifier = identifierId?.let {
                Identifier(
                    type = row[IdentifiersTable.type],
                    namespace = row[IdentifiersTable.namespace],
                    name = row[IdentifiersTable.name],
                    version = row[IdentifiersTable.version]
                )
            }

            RuleViolation(
                rule = row[RuleViolationsTable.rule],
                id = identifier,
                license = row.getOrNull(RuleViolationsTable.license),
                licenseSources = row[RuleViolationsTable.licenseSources].mapToLicenseSources(),
                severity = row[RuleViolationsTable.severity],
                message = row[RuleViolationsTable.message],
                howToFix = row[RuleViolationsTable.howToFix],
                resolutions = resolutionsByRuleViolationId[ruleViolationId].orEmpty().map { resolution ->
                    resolution.copy(
                        isDeleted = resolution.source == ResolutionSource.SERVER &&
                            RuleViolationResolution(
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

    private fun String?.mapToLicenseSources(): Set<LicenseSource> =
        this?.split(',')?.mapTo(mutableSetOf()) { enumValueOf<LicenseSource>(it) }.orEmpty()

    /** Count rule violations found in provided ORT runs. */
    suspend fun countForOrtRunIds(vararg ortRunIds: Long): Long = db.dbQuery {
        RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(RuleViolationsTable.id)
            .where { EvaluatorJobsTable.ortRunId inList ortRunIds.asList() }
            .withDistinct()
            .count()
    }

    /** Count rule violations by severity in provided ORT runs. */
    suspend fun countBySeverityForOrtRunIds(vararg ortRunIds: Long): CountByCategory<Severity> = db.dbQuery {
        val countAlias = Count(RuleViolationsTable.id, true)

        val severityToCountMap = Severity.entries.associateWithTo(mutableMapOf()) { 0L }

        RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(RuleViolationsTable.severity, countAlias)
            .where { EvaluatorJobsTable.ortRunId inList ortRunIds.asList() }
            .groupBy(RuleViolationsTable.severity)
            .map { row ->
                severityToCountMap.put(row[RuleViolationsTable.severity], row[countAlias])
            }

        CountByCategory(severityToCountMap)
    }

    /** Count unresolved rule violations found in provided ORT runs. */
    suspend fun countUnresolvedForOrtRunIds(vararg ortRunIds: Long): Long = db.dbQuery {
        val resolvedViolationIdsSubquery = ResolvedRuleViolationsTable
            .select(ResolvedRuleViolationsTable.ruleViolationId)
            .where { ResolvedRuleViolationsTable.ortRunId inList ortRunIds.asList() }

        RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(RuleViolationsTable.id)
            .where {
                (EvaluatorJobsTable.ortRunId inList ortRunIds.asList()) and
                    not(RuleViolationsTable.id inSubQuery resolvedViolationIdsSubquery)
            }
            .withDistinct()
            .count()
    }

    /** Count unresolved rule violations by severity for provided ORT runs. */
    suspend fun countUnresolvedBySeverityForOrtRunIds(vararg ortRunIds: Long): CountByCategory<Severity> = db.dbQuery {
        val countAlias = Count(RuleViolationsTable.id, true)

        val severityToCountMap = Severity.entries.associateWithTo(mutableMapOf()) { 0L }

        val resolvedViolationIdsSubquery = ResolvedRuleViolationsTable
            .select(ResolvedRuleViolationsTable.ruleViolationId)
            .where { ResolvedRuleViolationsTable.ortRunId inList ortRunIds.asList() }

        RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(RuleViolationsTable.severity, countAlias)
            .where {
                (EvaluatorJobsTable.ortRunId inList ortRunIds.asList()) and
                    not(RuleViolationsTable.id inSubQuery resolvedViolationIdsSubquery)
            }
            .groupBy(RuleViolationsTable.severity)
            .map { row ->
                severityToCountMap.put(row[RuleViolationsTable.severity], row[countAlias])
            }

        CountByCategory(severityToCountMap)
    }
}
