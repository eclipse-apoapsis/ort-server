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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataAuthors
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceSubRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsSnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.AbstractQuery
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.union

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OrphanRemovalService::class.java)

/**
 * Maintenance service to remove orphaned entities.
 *
 * When ORT runs reach their retention period and get deleted, they can leave orphaned entities in the database in
 * tables containing data that is shared between multiple runs. This service implements functionality to remove such
 * orphaned entities.
 *
 * Ideally, the removal of all orphans from a specific table can be done via a single SQL DELETE statement. However,
 * for some tables, constructing an efficient statement is very difficult or even impossible. Therefore, this service
 * uses different strategies to remove orphaned entities from different tables:
 * - For tables where an efficient DELETE statement can be constructed, the service uses that statement.
 * - For other tables, there are [OrphanEntityHandler] instances that apply a two-step approach: First, they fetch a
 *   limited number of orphaned entities, then they delete them in smaller chunks. So, the deletion of obsolete
 *   entities is spread over multiple invocations.
 */
class OrphanRemovalService(
    private val db: Database
) {
    /**
     * Delete orphaned entities of ORT runs.
     * Method uses heavy SQL queries, so it is not recommended to run it very often or under high DB loads.
     */
    suspend fun deleteRunsOrphanedEntities(config: ConfigManager) {
        logger.info("Deleting orphaned children of ORT runs.")

        logger.info("Deleted {} records from {}", deleteOrphanedPackages(), PackagesTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedProjects(), ProjectsTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedAuthors(), AuthorsTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedDeclaredLicenses(), DeclaredLicensesTable.tableName)
        logger.info(
            "Deleted {} records from {}",
            deleteOrphanedSnippetAssociations(),
            SnippetFindingsSnippetsTable.tableName
        )

        OrphanEntityHandler.entries.forEach { handler ->
            handler.deleteOrphanedEntities(db, config)
        }

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
                id inSubQuery (
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

    private suspend fun deleteOrphanedSnippetAssociations() =
        db.dbQuery {
            SnippetFindingsSnippetsTable.deleteWhere {
                SnippetFindingsSnippetsTable.snippetFindingId inSubQuery findUnassignedSnippetFindings()
            }
        }

    /**
     * Return a subquery that selects all snippet findings that are no longer assigned to a scanner run. Since snippet
     * findings are not shared between multiple runs, these are orphans and can be deleted.
     */
    private fun findUnassignedSnippetFindings(): Query {
        val snippetFindingsAlias = SnippetFindingsTable.alias("snippet_findings2")
        val runsSnippetsJoin = ScannerRunsScanResultsTable
            .innerJoin(ScanResultsTable)
            .innerJoin(ScanSummariesTable)
            .join(
                snippetFindingsAlias,
                JoinType.INNER,
                ScanSummariesTable.id,
                snippetFindingsAlias[SnippetFindingsTable.scanSummaryId]
            )

        val assignedSnippetFindings = runsSnippetsJoin.select(SnippetFindingsTable.id)
            .where { SnippetFindingsTable.id eq snippetFindingsAlias[SnippetFindingsTable.id] }

        return SnippetFindingsTable.select(SnippetFindingsTable.id)
            .where { notExists(assignedSnippetFindings) }
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

/**
 * An enumeration that contains handlers to delete orphaned entities from different tables for which no efficient
 * SQL DELETE statement can be constructed. Therefore, the handlers load a limited number of orphan entities and
 * delete them in smaller chunks. Each constant in this class is a handler responsible for a specific database table.
 */
private enum class OrphanEntityHandler(
    /** The table this handler is responsible for. */
    val table: LongIdTable,

    /** A prefix for the configuration options used by this handler. */
    val configPrefix: String
) {
    VCS_INFO(VcsInfoTable, "vcsInfo") {
        override fun filterOrphanedEntities(): SqlExpressionBuilder.() -> AbstractQuery<*> = {
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

            unionCondition(subQuery, table.id)
        }
    },

    REMOTE_ARTIFACTS(RemoteArtifactsTable, "remoteArtifacts") {
        override fun filterOrphanedEntities(): SqlExpressionBuilder.() -> AbstractQuery<*> = {
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

            unionCondition(subQuery, table.id)
        }
    },

    SNIPPETS(SnippetsTable, "snippets") {
        override fun filterOrphanedEntities(): SqlExpressionBuilder.() -> AbstractQuery<*> = {
            SnippetFindingsSnippetsTable
                .select(intLiteral(1))
                .where(SnippetFindingsSnippetsTable.snippetId eq table.id)
        }
    },

    SNIPPET_FINDINGS(SnippetFindingsTable, "snippetFindings") {
        override fun filterOrphanedEntities(): SqlExpressionBuilder.() -> AbstractQuery<*> = {
            SnippetFindingsSnippetsTable
                .select(intLiteral(1))
                .where(SnippetFindingsSnippetsTable.snippetFindingId eq table.id)
        }
    };

    /**
     * Delete orphaned entities from the represented table in the given [db] using configuration from the given
     * [config].
     */
    suspend fun deleteOrphanedEntities(db: Database, config: ConfigManager) {
        logger.info("Deleting orphaned children of ${table.tableName}.")

        val limit = config.getInt("$configPrefix.limit")
        val chunkSize = config.getInt("$configPrefix.chunkSize")

        val orphanIds = db.dbQuery {
            val orphansQuery = table.select(table.id).where {
                notExists(filterOrphanedEntities().invoke(this))
            }.limit(limit)

            orphansQuery.mapTo(mutableSetOf()) { it[table.id] }
        }

        logger.info("Found ${orphanIds.size} orphaned entities in ${table.tableName}.")

        orphanIds.chunked(chunkSize).forEach { ids ->
            logger.info("Deleting ${ids.size} orphaned entities from ${table.tableName}.")

            runCatching {
                db.dbQuery {
                    table.deleteWhere { table.id inList ids }
                }
            }.onFailure {
                logger.error("Failed to delete chunk of orphaned entities from ${table.tableName}.", it)
            }
        }
    }

    /**
     * Return a query condition that filters for orphaned entities for the represented table.
     */
    abstract fun filterOrphanedEntities(): SqlExpressionBuilder.() -> AbstractQuery<*>
}
