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
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.model.CountByCategory
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection.DESCENDING
import org.eclipse.apoapsis.ortserver.model.util.OrderField

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
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
        val totalCount = ortRunIssuesQuery.count()

        // There always has to be some sort order defined, else the rows would be returned in random order
        // and tests that rely on a deterministic order would fail.
        val sortFields = parameters.sortFields.ifEmpty {
            listOf(OrderField("timestamp", DESCENDING))
        }

        // For sorting we need to find the corresponding columns in the query.
        // TODO: Find a mechanism that does not rely on the internal names of table columns.
        val sortColumns = ortRunIssuesQuery.set.fields.mapNotNull { it as? Column }.associateBy { it.name }
        val orders = sortFields.map { sortField ->
            val column = sortColumns[sortField.name]
                ?: throw QueryParametersException("Field for sorting not found in query alias: '${sortField.name}'.")
            column to sortField.direction.toSortOrder()
        }.toTypedArray()

        val orderedQuery = ortRunIssuesQuery.orderBy(*orders)

        val paginatedQuery = parameters.limit?.let { orderedQuery.limit(it).offset(parameters.offset ?: 0) }
            ?: orderedQuery

        ListQueryResult(
            IssueDao.createFromQuery(paginatedQuery),
            parameters,
            totalCount
        )
    }

    /** Count issues found in provided ORT runs. */
    suspend fun countForOrtRunIds(vararg ortRunIds: Long): Long = db.dbQuery {
        OrtRunsIssuesTable
            .select(OrtRunsIssuesTable.id)
            .where { OrtRunsIssuesTable.ortRunId inList ortRunIds.asList() }
            .count()
    }

    /** Count issues by severity for provided ORT runs. */
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

/**
 * Convert this [OrderDirection] constant to the corresponding [SortOrder].
 */
fun OrderDirection.toSortOrder(): SortOrder =
    when (this) {
        OrderDirection.ASCENDING -> SortOrder.ASC
        OrderDirection.DESCENDING -> SortOrder.DESC
    }
