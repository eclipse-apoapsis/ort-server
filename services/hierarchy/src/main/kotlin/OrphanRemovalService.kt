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
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
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

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.deleteWhere
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
        logger.info("Deleted {} records from {}", deleteOrphanedProjects(), ProductsTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedAuthors(), AuthorsTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedDeclaredLicenses(), DeclaredLicensesTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedIdentifiers(), IdentifiersTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedVcsInfo(), VcsInfoTable.tableName)
        logger.info("Deleted {} records from {}", deleteOrphanedRemoteArtifacts(), RemoteArtifactsTable.tableName)

        logger.info("Deleting orphaned children of ORT runs finished.")
    }

    private suspend fun deleteOrphanedPackages() =
        db.dbQuery {
            PackagesTable.deleteWhere {
                id notInSubQuery (
                    PackagesAnalyzerRunsTable
                        .select(PackagesAnalyzerRunsTable.packageId.alias("id"))
                        .where(PackagesAnalyzerRunsTable.packageId.isNotNull())
                    )
            }
        }

    private suspend fun deleteOrphanedProjects() =
        db.dbQuery {
            ProjectsTable.deleteWhere {
                id notInSubQuery (
                    ProjectsAnalyzerRunsTable
                        .select(ProjectsAnalyzerRunsTable.projectId.alias("id"))
                        .where(ProjectsAnalyzerRunsTable.projectId.isNotNull())
                    )
            }
        }

    private suspend fun deleteOrphanedAuthors() =
        db.dbQuery {
            AuthorsTable.deleteWhere {
                id notInSubQuery (
                    PackagesAuthorsTable
                        .select(PackagesAuthorsTable.authorId.alias("id"))
                        .where(PackagesAuthorsTable.authorId.isNotNull())
                        .union(
                            ProjectsAuthorsTable
                                .select(ProjectsAuthorsTable.authorId.alias("id"))
                                .where(ProjectsAuthorsTable.authorId.isNotNull())
                        )
                        .union(
                            PackageCurationDataAuthors
                                .select(PackageCurationDataAuthors.authorId.alias("id"))
                                .where(PackageCurationDataAuthors.authorId.isNotNull())
                        )
                    )
            }
        }

    private suspend fun deleteOrphanedDeclaredLicenses() =
        db.dbQuery {
            DeclaredLicensesTable.deleteWhere {
                id notInSubQuery (
                    PackagesDeclaredLicensesTable
                        .select(PackagesDeclaredLicensesTable.declaredLicenseId.alias("id"))
                        .where(PackagesDeclaredLicensesTable.declaredLicenseId.isNotNull())
                        .union(
                            ProjectsDeclaredLicensesTable
                                .select(ProjectsDeclaredLicensesTable.declaredLicenseId.alias("id"))
                                .where(ProjectsDeclaredLicensesTable.declaredLicenseId.isNotNull())
                        )
                    )
            }
        }

    private suspend fun deleteOrphanedIdentifiers() =
        db.dbQuery {
            IdentifiersTable.deleteWhere {
                id notInSubQuery (
                    ProjectsTable
                        .select(ProjectsTable.identifierId.alias("id"))
                        .where(ProjectsTable.identifierId.isNotNull())
                        .union(
                            PackagesTable
                                .select(PackagesTable.identifierId.alias("id"))
                                .where(PackagesTable.identifierId.isNotNull())
                        )
                        .union(
                            IdentifiersIssuesTable
                                .select(IdentifiersIssuesTable.identifierId.alias("id"))
                                .where(IdentifiersIssuesTable.identifierId.isNotNull())
                        )
                        .union(
                            AdvisorRunsIdentifiersTable
                                .select(AdvisorRunsIdentifiersTable.identifierId.alias("id"))
                                .where(AdvisorRunsIdentifiersTable.identifierId.isNotNull())
                        )
                        .union(
                            PackageProvenancesTable
                                .select(PackageProvenancesTable.identifierId.alias("id"))
                                .where(PackageProvenancesTable.identifierId.isNotNull())
                        )
                        .union(
                            RuleViolationsTable
                                .select(RuleViolationsTable.packageIdentifierId.alias("id"))
                                .where(RuleViolationsTable.packageIdentifierId.isNotNull())
                        )
                        .union(
                            PackageCurationsTable
                                .select(PackageCurationsTable.identifierId.alias("id"))
                                .where(PackageCurationsTable.identifierId.isNotNull())
                        )
                        .union(
                            PackageConfigurationsTable
                                .select(PackageConfigurationsTable.identifierId.alias("id"))
                                .where(PackageConfigurationsTable.identifierId.isNotNull())
                        )
                        .union(
                            PackageLicenseChoicesTable
                                .select(PackageLicenseChoicesTable.identifierId.alias("id"))
                                .where(PackageLicenseChoicesTable.identifierId.isNotNull())
                        )
                        .union(
                            ScannerRunsScannersTable
                                .select(ScannerRunsScannersTable.identifierId.alias("id"))
                                .where(ScannerRunsScannersTable.identifierId.isNotNull())
                        )
                        .union(
                            OrtRunsIssuesTable
                                .select(OrtRunsIssuesTable.identifierId.alias("id"))
                                .where(OrtRunsIssuesTable.identifierId.isNotNull())
                        )
                    )
            }
        }

    private suspend fun deleteOrphanedVcsInfo() =
        db.dbQuery {
            VcsInfoTable.deleteWhere {
                VcsInfoTable.id notInSubQuery (
                    OrtRunsTable
                        .select(OrtRunsTable.vcsId.alias("vcs_id"))
                        .where(OrtRunsTable.vcsId.isNotNull())
                        .union(
                            OrtRunsTable
                                .select(OrtRunsTable.vcsProcessedId.alias("vcs_id"))
                                .where(OrtRunsTable.vcsProcessedId.isNotNull())
                        )
                        .union(
                            ProjectsTable
                                .select(ProjectsTable.vcsId.alias("vcs_id"))
                                .where(ProjectsTable.vcsId.isNotNull())
                        )
                        .union(
                            ProjectsTable
                                .select(ProjectsTable.vcsProcessedId.alias("vcs_id"))
                                .where(ProjectsTable.vcsProcessedId.isNotNull())
                        )
                        .union(
                            PackagesTable
                                .select(PackagesTable.vcsId.alias("vcs_id"))
                                .where(PackagesTable.vcsId.isNotNull())
                        )
                        .union(
                            PackagesTable
                                .select(PackagesTable.vcsProcessedId.alias("id"))
                                .where(PackagesTable.vcsProcessedId.isNotNull())
                        )
                        .union(
                            NestedProvenancesTable
                                .select(NestedProvenancesTable.rootVcsId.alias("id"))
                                .where(NestedProvenancesTable.rootVcsId.isNotNull())
                        )
                        .union(
                            NestedProvenanceSubRepositoriesTable
                                .select(NestedProvenanceSubRepositoriesTable.vcsId.alias("id"))
                                .where(NestedProvenanceSubRepositoriesTable.vcsId.isNotNull())
                        )
                        .union(
                            PackageProvenancesTable
                                .select(PackageProvenancesTable.vcsId.alias("id"))
                                .where(PackageProvenancesTable.vcsId.isNotNull())
                        )
                        .union(
                            NestedRepositoriesTable
                                .select(NestedRepositoriesTable.vcsId.alias("id"))
                                .where(NestedRepositoriesTable.vcsId.isNotNull())
                        )
                        .union(
                            SnippetsTable
                                .select(SnippetsTable.vcsId.alias("id"))
                                .where(SnippetsTable.vcsId.isNotNull())
                        )
                    )
            }
        }

    private suspend fun deleteOrphanedRemoteArtifacts() =
        db.dbQuery {
            RemoteArtifactsTable.deleteWhere {
                id notInSubQuery (
                    PackagesTable
                        .select(PackagesTable.binaryArtifactId.alias("id"))
                        .where(PackagesTable.binaryArtifactId.isNotNull())
                        .union(
                            PackagesTable
                                .select(PackagesTable.sourceArtifactId.alias("id"))
                                .where(PackagesTable.sourceArtifactId.isNotNull())
                        )
                        .union(
                            PackageProvenancesTable
                                .select(PackageProvenancesTable.artifactId.alias("id"))
                                .where(PackageProvenancesTable.artifactId.isNotNull())
                        )
                        .union(
                            PackageCurationDataTable
                                .select(PackageCurationDataTable.binaryArtifactId.alias("id"))
                                .where(PackageCurationDataTable.binaryArtifactId.isNotNull())
                        )
                        .union(
                            PackageCurationDataTable
                                .select(PackageCurationDataTable.sourceArtifactId.alias("id"))
                                .where(PackageCurationDataTable.sourceArtifactId.isNotNull())
                        )
                        .union(
                            SnippetsTable
                                .select(SnippetsTable.artifactId.alias("id"))
                                .where(SnippetsTable.artifactId.isNotNull())
                        )
                    )
            }
        }
}
