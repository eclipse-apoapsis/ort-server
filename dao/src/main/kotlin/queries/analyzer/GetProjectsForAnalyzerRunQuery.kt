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

package org.eclipse.apoapsis.ortserver.dao.queries.analyzer

import kotlin.collections.orEmpty

import org.eclipse.apoapsis.ortserver.dao.Query
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.MappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesMappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectScopesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.UnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.selectAll

/**
 * A query to get the [Project]s for a given [analyzerRunId]. Returns an empty set if no projects are found.
 */
class GetProjectsForAnalyzerRunQuery(
    /** The ID of the analyzer run to retrieve projects for. */
    val analyzerRunId: Long
) : Query<Set<Project>> {
    override fun execute(): Set<Project> {
        val projectIds = ProjectsAnalyzerRunsTable
            .select(ProjectsAnalyzerRunsTable.projectId)
            .where { ProjectsAnalyzerRunsTable.analyzerRunId eq analyzerRunId }
            .mapTo(mutableSetOf()) { it[ProjectsAnalyzerRunsTable.projectId].value }

        if (projectIds.isEmpty()) return emptySet()

        val vcsProcessedTable = VcsInfoTable.alias("vcs_processed_info")

        val resultRows = ProjectsTable
            .innerJoin(IdentifiersTable)
            .join(VcsInfoTable, JoinType.LEFT, ProjectsTable.vcsId, VcsInfoTable.id)
            .join(vcsProcessedTable, JoinType.LEFT, ProjectsTable.vcsProcessedId, vcsProcessedTable[VcsInfoTable.id])
            .selectAll()
            .where { ProjectsTable.id inList projectIds }
            .toList()

        val authorsByProjectId = getAuthors(projectIds)
        val declaredLicensesByProjectId = getDeclaredLicenses(projectIds)
        val processedDeclaredLicenseByProjectId = getProcessedDeclaredLicenses(projectIds)
        val scopeNamesByProjectId = getScopes(projectIds)

        return resultRows.mapTo(mutableSetOf()) { resultRow ->
            val projectId = resultRow[ProjectsTable.id].value

            val identifier = Identifier(
                type = resultRow[IdentifiersTable.type],
                namespace = resultRow[IdentifiersTable.namespace],
                name = resultRow[IdentifiersTable.name],
                version = resultRow[IdentifiersTable.version]
            )

            val processedDeclaredLicense = processedDeclaredLicenseByProjectId[projectId] ?: ProcessedDeclaredLicense(
                spdxExpression = null,
                mappedLicenses = emptyMap(),
                unmappedLicenses = emptySet()
            )

            val vcs = VcsInfo(
                type = RepositoryType.forName(resultRow[VcsInfoTable.type]),
                url = resultRow[VcsInfoTable.url],
                revision = resultRow[VcsInfoTable.revision],
                path = resultRow[VcsInfoTable.path]
            )

            val vcsProcessed = VcsInfo(
                type = RepositoryType.forName(resultRow[vcsProcessedTable[VcsInfoTable.type]]),
                url = resultRow[vcsProcessedTable[VcsInfoTable.url]],
                revision = resultRow[vcsProcessedTable[VcsInfoTable.revision]],
                path = resultRow[vcsProcessedTable[VcsInfoTable.path]]
            )

            Project(
                identifier = identifier,
                cpe = resultRow[ProjectsTable.cpe],
                definitionFilePath = resultRow[ProjectsTable.definitionFilePath],
                authors = authorsByProjectId[projectId].orEmpty(),
                declaredLicenses = declaredLicensesByProjectId[projectId].orEmpty(),
                processedDeclaredLicense = processedDeclaredLicense,
                vcs = vcs,
                vcsProcessed = vcsProcessed,
                description = resultRow[ProjectsTable.description],
                homepageUrl = resultRow[ProjectsTable.homepageUrl],
                scopeNames = scopeNamesByProjectId[projectId].orEmpty()
            )
        }
    }

    /** Get the authors for the provided [projectIds]. */
    private fun getAuthors(projectIds: Set<Long>): Map<Long, Set<String>> =
        ProjectsAuthorsTable
            .innerJoin(AuthorsTable)
            .select(ProjectsAuthorsTable.projectId, AuthorsTable.name)
            .where { ProjectsAuthorsTable.projectId inList projectIds }
            .groupBy({ it[ProjectsAuthorsTable.projectId] }) { it[AuthorsTable.name] }
            .mapKeys { it.key.value }
            .mapValues { it.value.toSet() }

    /** Get the declared licenses for the provided [projectIds]. */
    private fun getDeclaredLicenses(projectIds: Set<Long>): Map<Long, Set<String>> =
        ProjectsDeclaredLicensesTable
            .innerJoin(DeclaredLicensesTable)
            .select(ProjectsDeclaredLicensesTable.projectId, DeclaredLicensesTable.name)
            .where { ProjectsDeclaredLicensesTable.projectId inList projectIds }
            .groupBy({ it[ProjectsDeclaredLicensesTable.projectId] }) { it[DeclaredLicensesTable.name] }
            .mapKeys { it.key.value }
            .mapValues { it.value.toSet() }

    /**
     * Get the [ProcessedDeclaredLicense]s for the provided [projectIds].
     */
    @Suppress("UNCHECKED_CAST")
    private fun getProcessedDeclaredLicenses(projectIds: Set<Long>): Map<Long, ProcessedDeclaredLicense?> =
        ProcessedDeclaredLicensesTable
            .leftJoin(ProcessedDeclaredLicensesMappedDeclaredLicensesTable)
            .leftJoin(MappedDeclaredLicensesTable)
            .leftJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
            .leftJoin(UnmappedDeclaredLicensesTable)
            .selectAll()
            .where { ProcessedDeclaredLicensesTable.projectId inList projectIds }
            .groupBy { it[ProcessedDeclaredLicensesTable.projectId]?.value }
            .mapValues { (_, resultRows) ->
                if (resultRows.isEmpty()) return@mapValues null

                // The compiler wrongly assumes that the declaredLicense, mappedLicense, and unmappedLicense columns
                // cannot be null, but they can be if there are no entries in the joined tables, so they must be cast
                // to nullable.
                val nullableMappedLicenses = resultRows.associate {
                    it[MappedDeclaredLicensesTable.declaredLicense] to
                            it[MappedDeclaredLicensesTable.mappedLicense]
                } as Map<String?, String>

                val mappedLicenses = nullableMappedLicenses.filterKeys { it != null } as Map<String, String>

                val unmappedLicenses = resultRows
                    .mapTo(mutableListOf<String?>()) { it[UnmappedDeclaredLicensesTable.unmappedLicense] }
                    .filterNotNullTo(mutableSetOf())

                ProcessedDeclaredLicense(
                    spdxExpression = resultRows[0][ProcessedDeclaredLicensesTable.spdxExpression],
                    mappedLicenses = mappedLicenses,
                    unmappedLicenses = unmappedLicenses
                )
            } as Map<Long, ProcessedDeclaredLicense?>

    /** Get the scopes for the provided [projectIds]. */
    private fun getScopes(projectIds: Set<Long>): Map<Long, Set<String>> =
        ProjectScopesTable
            .selectAll()
            .where { ProjectScopesTable.projectId inList projectIds }
            .groupBy({ it[ProjectScopesTable.projectId] }) { it[ProjectScopesTable.name] }
            .mapKeys { it.key.value }
            .mapValues { it.value.toSet() }
}
