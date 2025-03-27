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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsIdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.RuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageConfigurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataAuthors
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageLicenseChoicesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScannersTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceSubRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.AbstractQuery
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.union

import org.slf4j.LoggerFactory

/**
 * Maintenance service to remove orphaned entities.
 */
class OrphanRemovalService(
    private val db: Database
) {
    private val logger = LoggerFactory.getLogger(OrphanRemovalService::class.java)

    /**
     * Delete orphaned entities of ORT runs.
     * Method uses heavy SQL queries, so it is not recommended to run it very often or under high DB loads.
     */
    suspend fun deleteRunsOrphanedEntities() {
        logger.info("Deleting orphaned children of ORT runs.")

        logger.info("Deleted {} records from {}", deleteOrphanedPackages(), PackagesTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedProjects(), ProjectsTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedAuthors(), AuthorsTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedDeclaredLicenses(), DeclaredLicensesTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedIdentifiers(), IdentifiersTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedVcsInfo(), VcsInfoTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedRemoteArtifacts(), RemoteArtifactsTable.tableName)

        logger.info("Deleting orphaned children of ORT runs finished.")
    }

    /**
     * Delete all entries from this table for which the given [condition][cond] of a `NON EXISTS` subquery is
     * fulfilled.
     */
    private suspend fun <T : LongIdTable> T.deleteWhereNotExists(
        cond: SqlExpressionBuilder.(Column<EntityID<Long>>) -> AbstractQuery<*>
    ): Int =
        db.dbQuery {
            deleteWhere {
                id inSubQuery(
                    select(id).where { notExists(cond(id)) }
                )
            }
        }

    private suspend fun deleteOrphanedPackages() =
        PackagesTable.deleteWhereNotExists { id ->
            PackagesAnalyzerRunsTable.select(PackagesAnalyzerRunsTable.packageId)
                .where(PackagesAnalyzerRunsTable.packageId eq id)
        }

    private suspend fun deleteOrphanedProjects() =
        ProjectsTable.deleteWhereNotExists { id ->
            ProjectsAnalyzerRunsTable.select(ProjectsAnalyzerRunsTable.projectId)
                .where(ProjectsAnalyzerRunsTable.projectId eq id)
        }

    private suspend fun deleteOrphanedAuthors() =
        AuthorsTable.deleteWhereNotExists { id ->
            PackagesAuthorsTable
                .select(PackagesAuthorsTable.authorId.alias("id"))
                .where { PackagesAuthorsTable.authorId eq id }
                .union(
                    ProjectsAuthorsTable
                        .select(ProjectsAuthorsTable.authorId.alias("id"))
                        .where { ProjectsAuthorsTable.authorId eq id }
                )
                .union(
                    PackageCurationDataAuthors
                        .select(PackageCurationDataAuthors.authorId.alias("id"))
                        .where { PackageCurationDataAuthors.authorId eq id }
                )
        }

    private suspend fun deleteOrphanedDeclaredLicenses() =
        DeclaredLicensesTable.deleteWhereNotExists { id ->
            PackagesDeclaredLicensesTable
                .select(PackagesDeclaredLicensesTable.declaredLicenseId.alias("id"))
                .where { PackagesDeclaredLicensesTable.declaredLicenseId eq id }
                .union(
                    ProjectsDeclaredLicensesTable
                        .select(ProjectsDeclaredLicensesTable.declaredLicenseId.alias("id"))
                        .where { ProjectsDeclaredLicensesTable.declaredLicenseId eq id }
                )
        }

    private suspend fun deleteOrphanedIdentifiers() =
        IdentifiersTable.deleteWhereNotExists { id ->
            ProjectsTable
                .select(ProjectsTable.identifierId.alias("id"))
                .where { ProjectsTable.identifierId eq id }
                .union(
                    PackagesTable
                        .select(PackagesTable.identifierId.alias("id"))
                        .where { PackagesTable.identifierId eq id }
                )
                .union(
                    IdentifiersIssuesTable
                        .select(IdentifiersIssuesTable.identifierId.alias("id"))
                        .where { IdentifiersIssuesTable.identifierId eq id }
                )
                .union(
                    AdvisorRunsIdentifiersTable
                        .select(AdvisorRunsIdentifiersTable.identifierId.alias("id"))
                        .where { AdvisorRunsIdentifiersTable.identifierId eq id }
                )
                .union(
                    PackageProvenancesTable
                        .select(PackageProvenancesTable.identifierId.alias("id"))
                        .where { PackageProvenancesTable.identifierId eq id }
                )
                .union(
                    RuleViolationsTable
                        .select(RuleViolationsTable.packageIdentifierId.alias("id"))
                        .where { RuleViolationsTable.packageIdentifierId eq id }
                )
                .union(
                    PackageCurationsTable
                        .select(PackageCurationsTable.identifierId.alias("id"))
                        .where { PackageCurationsTable.identifierId eq id }
                )
                .union(
                    PackageConfigurationsTable
                        .select(PackageConfigurationsTable.identifierId.alias("id"))
                        .where { PackageConfigurationsTable.identifierId eq id }
                )
                .union(
                    PackageLicenseChoicesTable
                        .select(PackageLicenseChoicesTable.identifierId.alias("id"))
                        .where { PackageLicenseChoicesTable.identifierId eq id }
                )
                .union(
                    ScannerRunsScannersTable
                        .select(ScannerRunsScannersTable.identifierId.alias("id"))
                        .where { ScannerRunsScannersTable.identifierId eq id }
                )
                .union(
                    OrtRunsIssuesTable
                        .select(OrtRunsIssuesTable.identifierId.alias("id"))
                        .where { OrtRunsIssuesTable.identifierId eq id }
                )
        }

    private suspend fun deleteOrphanedVcsInfo() =
        VcsInfoTable.deleteWhereNotExists { id ->
            val subQuery = OrtRunsTable
                .select(OrtRunsTable.vcsId.alias("id"))
                .union(
                    OrtRunsTable
                        .select(OrtRunsTable.vcsProcessedId)
                )
                .union(
                    ProjectsTable
                        .select(ProjectsTable.vcsId)
                )
                .union(
                    ProjectsTable
                        .select(ProjectsTable.vcsProcessedId)
                )
                .union(
                    PackagesTable
                        .select(PackagesTable.vcsId)
                )
                .union(
                    PackagesTable
                        .select(PackagesTable.vcsProcessedId)
                )
                .union(
                    NestedProvenancesTable
                        .select(NestedProvenancesTable.rootVcsId)
                )
                .union(
                    NestedProvenanceSubRepositoriesTable
                        .select(NestedProvenanceSubRepositoriesTable.vcsId)
                )
                .union(
                    PackageProvenancesTable
                        .select(PackageProvenancesTable.vcsId)
                )
                .union(
                    NestedRepositoriesTable
                        .select(NestedRepositoriesTable.vcsId)
                )
                .union(
                    SnippetsTable
                        .select(SnippetsTable.vcsId)
                )

            unionCondition(subQuery, id)
        }

    private suspend fun deleteOrphanedRemoteArtifacts() =
        RemoteArtifactsTable.deleteWhereNotExists { id ->
            val subQuery = PackagesTable
                .select(PackagesTable.binaryArtifactId.alias("id"))
                .union(
                    PackagesTable
                        .select(PackagesTable.sourceArtifactId)
                )
                .union(
                    PackageProvenancesTable
                        .select(PackageProvenancesTable.artifactId)
                )
                .union(
                    PackageCurationDataTable
                        .select(PackageCurationDataTable.binaryArtifactId)
                )
                .union(
                    PackageCurationDataTable
                        .select(PackageCurationDataTable.sourceArtifactId)
                )
                .union(
                    SnippetsTable
                        .select(SnippetsTable.artifactId)
                )

            unionCondition(subQuery, id)
        }
}

/**
 * Add a condition to match the given [matchColumn] to the given [unionQuery]. This results in a statement of the
 * form: SELECT * FROM (SELECT * FROM sub_query) WHERE sub_query.id = matchColumn.
 * TODO: Check whether there is a better way to do this with Exposed.
 */
private fun <T : AbstractQuery<T>> unionCondition(
    unionQuery: AbstractQuery<T>,
    matchColumn: Column<EntityID<Long>>
): Query {
    val subQuery = unionQuery.alias("sub_query")
    val queryTable = Table("sub_query")
    val column = Column(queryTable, "id", LongColumnType())

    return subQuery.selectAll()
        .where { column eq matchColumn }
}
