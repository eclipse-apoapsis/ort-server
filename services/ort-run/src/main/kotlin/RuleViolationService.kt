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
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.EvaluatorRunsRuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.EvaluatorRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.ResolvedRuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.RuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RuleViolationResolutionsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.model.CountByCategory
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.LicenseSource
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolationFilters
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
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
 * A service to interact with rule violations.
 */
class RuleViolationService(private val db: Database, private val ortRunService: OrtRunService) {
    fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        ruleViolationFilter: RuleViolationFilters = RuleViolationFilters()
    ): ListQueryResult<RuleViolation> {
        ortRunService.getOrtRun(ortRunId) ?: throw ResourceNotFoundException(
            "ORT run with ID $ortRunId not found."
        )

        return db.blockingQuery {
            val query = buildListForOrtRunIdQuery(ortRunId, ruleViolationFilter)

            val totalCount = query.count()
            val ruleViolationIds = fetchPagedRuleViolationIds(query, parameters)

            if (ruleViolationIds.isEmpty()) {
                return@blockingQuery ListQueryResult(emptyList(), parameters, totalCount)
            }

            val ruleViolationRows = fetchRuleViolationRows(ruleViolationIds)
            val resolutionsByRuleViolationId = fetchResolutionsByRuleViolationId(ortRunId, ruleViolationIds)

            val identifierIds = ruleViolationRows
                .mapNotNullTo(mutableSetOf()) { it.getOrNull(RuleViolationsTable.identifierId)?.value }

            val purlByIdentifierId = getPurlByIdentifierIdForOrtRun(ortRunId, identifierIds)
            val ruleViolations = assembleRuleViolations(
                ruleViolationRows,
                resolutionsByRuleViolationId,
                purlByIdentifierId
            )

            ListQueryResult(data = ruleViolations, params = parameters, totalCount = totalCount)
        }
    }

    private fun buildListForOrtRunIdQuery(ortRunId: Long, ruleViolationFilter: RuleViolationFilters): Query {
        val query = RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(RuleViolationsTable.id)
            .where { EvaluatorJobsTable.ortRunId eq ortRunId }

        val resolvedRuleViolationIdsSubquery = ResolvedRuleViolationsTable
            .select(ResolvedRuleViolationsTable.ruleViolationId)
            .where { ResolvedRuleViolationsTable.ortRunId eq ortRunId }

        when (ruleViolationFilter.resolved) {
            true -> query.andWhere { RuleViolationsTable.id inSubQuery resolvedRuleViolationIdsSubquery }
            false -> query.andWhere { not(RuleViolationsTable.id inSubQuery resolvedRuleViolationIdsSubquery) }
            null -> {}
        }

        return query
    }

    private fun fetchPagedRuleViolationIds(query: Query, parameters: ListQueryParameters): List<Long> {
        val sortFields = parameters.sortFields.ifEmpty {
            listOf(OrderField("rule", OrderDirection.ASCENDING))
        }

        sortFields.forEach { orderField ->
            val sortOrder = orderField.direction.toSortOrder()

            when (orderField.name) {
                "rule" -> query.orderBy(RuleViolationsTable.rule to sortOrder)
                "severity" -> query.orderBy(RuleViolationsTable.severity to sortOrder)
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
    ): Map<Long, List<RuleViolationResolution>> =
        ResolvedRuleViolationsTable
            .innerJoin(RuleViolationResolutionsTable, { ruleViolationResolutionId }, { id })
            .select(
                ResolvedRuleViolationsTable.ruleViolationId,
                RuleViolationResolutionsTable.message,
                RuleViolationResolutionsTable.reason,
                RuleViolationResolutionsTable.comment
            )
            .where {
                (ResolvedRuleViolationsTable.ortRunId eq ortRunId) and
                    (ResolvedRuleViolationsTable.ruleViolationId inList ruleViolationIds)
            }
            .groupBy(
                { it[ResolvedRuleViolationsTable.ruleViolationId].value },
                {
                    RuleViolationResolution(
                        message = it[RuleViolationResolutionsTable.message],
                        reason = it[RuleViolationResolutionsTable.reason],
                        comment = it[RuleViolationResolutionsTable.comment]
                    )
                }
            )

    private fun assembleRuleViolations(
        ruleViolationRows: List<ResultRow>,
        resolutionsByRuleViolationId: Map<Long, List<RuleViolationResolution>>,
        purlByIdentifierId: Map<Long, String>
    ): List<RuleViolation> =
        ruleViolationRows.map { row ->
            val ruleViolationId = row[RuleViolationsTable.id].value
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

            val identifierId = row.getOrNull(RuleViolationsTable.identifierId)?.value

            RuleViolation(
                rule = row[RuleViolationsTable.rule],
                id = identifier,
                license = row.getOrNull(RuleViolationsTable.license),
                licenseSources = row[RuleViolationsTable.licenseSources].mapToLicenseSources(),
                severity = row[RuleViolationsTable.severity],
                message = row[RuleViolationsTable.message],
                howToFix = row[RuleViolationsTable.howToFix],
                resolutions = resolutionsByRuleViolationId[ruleViolationId].orEmpty(),
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
