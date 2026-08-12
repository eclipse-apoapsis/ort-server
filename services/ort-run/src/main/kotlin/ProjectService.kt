/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.UnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.applyILike
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.ProjectFilters
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

/** A service to interact with projects. */
class ProjectService(private val db: Database) {
    /** Return a filtered page of projects for the given ORT [ortRunId]. */
    suspend fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filters: ProjectFilters = ProjectFilters()
    ): ListQueryResult<Project> = db.dbQuery {
        val query = ProjectsTable.joinAnalyzerTables()
            .innerJoin(IdentifiersTable)
            .innerJoin(ProcessedDeclaredLicensesTable)
            .select(ProjectsTable.id)
            .where { AnalyzerJobsTable.ortRunId eq ortRunId }

        filters.identifier?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for identifier filter: ${filter.operator}"
            }

            val identifierExpression = concat(
                IdentifiersTable.type,
                stringLiteral(":"),
                IdentifiersTable.namespace,
                stringLiteral(":"),
                IdentifiersTable.name,
                stringLiteral(":"),
                IdentifiersTable.version
            )

            query.andWhere { identifierExpression.applyILike(filter.value) }
        }

        filters.declaredLicense?.let { filter ->
            require(filter.operator == ComparisonOperator.IN || filter.operator == ComparisonOperator.NOT_IN) {
                "Unsupported operator for declared license filter: ${filter.operator}"
            }

            val projectIdsWithSelectedLicenses = ProjectsTable.joinAnalyzerTables()
                .innerJoin(ProcessedDeclaredLicensesTable)
                .leftJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
                .leftJoin(UnmappedDeclaredLicensesTable)
                .select(ProjectsTable.id)
                .where {
                    (AnalyzerJobsTable.ortRunId eq ortRunId) and
                        (
                            (ProcessedDeclaredLicensesTable.spdxExpression inList filter.value) or
                                (UnmappedDeclaredLicensesTable.unmappedLicense inList filter.value)
                        )
                }

            query.andWhere {
                if (filter.operator == ComparisonOperator.IN) {
                    ProjectsTable.id inSubQuery projectIdsWithSelectedLicenses
                } else {
                    not(ProjectsTable.id inSubQuery projectIdsWithSelectedLicenses)
                }
            }
        }

        filters.definitionFilePath?.let { filter ->
            require(filter.operator == ComparisonOperator.ILIKE) {
                "Unsupported operator for definition file path filter: ${filter.operator}"
            }

            query.andWhere { ProjectsTable.definitionFilePath.applyILike(filter.value) }
        }

        val totalCount = query.count()

        parameters.sortFields.forEach { orderField ->
            val sortOrder = when (orderField.direction) {
                OrderDirection.ASCENDING -> SortOrder.ASC
                OrderDirection.DESCENDING -> SortOrder.DESC
            }

            when (orderField.name) {
                "id" -> query.orderBy(ProjectsTable.id to sortOrder)

                "identifier" -> {
                    query.orderBy(IdentifiersTable.type to sortOrder)
                    query.orderBy(IdentifiersTable.namespace to sortOrder)
                    query.orderBy(IdentifiersTable.name to sortOrder)
                    query.orderBy(IdentifiersTable.version to sortOrder)
                }

                "declaredLicense" -> query.orderBy(ProcessedDeclaredLicensesTable.spdxExpression to sortOrder)

                "definitionFilePath" -> query.orderBy(ProjectsTable.definitionFilePath to sortOrder)

                else -> throw QueryParametersException("Unsupported field for sorting: '${orderField.name}'.")
            }
        }

        query.orderBy(ProjectsTable.id to SortOrder.ASC)
        query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

        val projectIds = query.map { it[ProjectsTable.id] }
        if (projectIds.isEmpty()) return@dbQuery ListQueryResult(emptyList(), parameters, totalCount)

        val projectsById = ProjectDao.find { ProjectsTable.id inList projectIds }.associateBy { it.id }
        val projects = projectIds.map { projectsById.getValue(it).mapToModel() }

        ListQueryResult(projects, parameters, totalCount)
    }

    /** Return distinct processed declared SPDX license expressions for the ORT run. */
    suspend fun getProcessedDeclaredLicenses(ortRunId: Long): List<String> = db.dbQuery {
        ProjectsTable.joinAnalyzerTables()
            .innerJoin(ProcessedDeclaredLicensesTable)
            .select(ProcessedDeclaredLicensesTable.spdxExpression)
            .where { AnalyzerJobsTable.ortRunId eq ortRunId }
            .withDistinct()
            .mapNotNullTo(mutableSetOf()) { it[ProcessedDeclaredLicensesTable.spdxExpression] }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /** Return distinct unmapped declared license strings for the ORT run. */
    suspend fun getUnmappedDeclaredLicenses(ortRunId: Long): List<String> = db.dbQuery {
        ProjectsTable.joinAnalyzerTables()
            .innerJoin(ProcessedDeclaredLicensesTable)
            .innerJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
            .innerJoin(UnmappedDeclaredLicensesTable)
            .select(UnmappedDeclaredLicensesTable.unmappedLicense)
            .where { AnalyzerJobsTable.ortRunId eq ortRunId }
            .withDistinct()
            .mapTo(mutableSetOf()) { it[UnmappedDeclaredLicensesTable.unmappedLicense] }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}

private fun ProjectsTable.joinAnalyzerTables() =
    innerJoin(ProjectsAnalyzerRunsTable)
        .innerJoin(AnalyzerRunsTable)
        .innerJoin(AnalyzerJobsTable)
