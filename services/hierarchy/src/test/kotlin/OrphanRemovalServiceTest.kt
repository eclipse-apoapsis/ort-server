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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.MappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.UnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.CopyrightFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.LicenseFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsSnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createAdvisorRunsIdentifiersTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createAnalyzerRunTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createAuthorsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createCopyrightFindingsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createDeclaredLicensesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createIdentifierTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createIdentifiersIssuesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createLicenseFindingsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createMappedDeclaredLicenseTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createNestedRepositoriesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createOrtRunTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createOrtRunsIssuesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackageAnalyzerRunsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackageConfigurationsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackageCurationDataAuthorsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackageCurationDataTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackageCurationsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackageLicenseChoicesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackageProvenancesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackagesAuthorsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackagesDeclaredLicensesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createPackagesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createProcessedDeclaredLicensesMappedDeclaredLicensesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createProcessedDeclaredLicensesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createProcessedDeclaredLicensesUnmappedDeclaredLicensesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createProjectScopesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createProjectsAnalyzerRunsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createProjectsAuthorsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createProjectsDeclaredLicensesTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createProjectsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createRemoteArtifactsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createRuleViolationsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createScanResultsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createScanSummaryTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createScannerRunsScanResultsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createScannerRunsScannersTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createSnippetFindingsSnippetsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createSnippetFindingsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createSnippetsTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createUnmappedDeclaredLicenseTableEntry
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalServiceTestFixtures.createVcsInfoTableEntry

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll

@Suppress("LargeClass")
class OrphanRemovalServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var service: OrphanRemovalService

    init {
        beforeEach {
            db = dbExtension.db
            service = OrphanRemovalService(db)
        }

        "deleteOrphanedEntities" should {
            "delete packages that are not associated with any other entity" {
                db.dbQuery {
                    // Orphan entries - should be deleted by removal process.
                    createPackagesTableEntry(purl = "to.delete.1")

                    // Packages in relation, that should not be removed.
                    createPackageAnalyzerRunsTableEntry(
                        createPackagesTableEntry(purl = "not.to.delete.1").value,
                        createAnalyzerRunTableEntry().value
                    )

                    createProcessedDeclaredLicensesTableEntry(
                        packageId = createPackagesTableEntry(purl = "to.delete.2").value
                    )

                    createPackagesDeclaredLicensesTableEntry(
                        packageId = createPackagesTableEntry(purl = "to.delete.2").value
                    )

                    createPackagesAuthorsTableEntry(
                        packageId = createPackagesTableEntry(purl = "to.delete.3").value
                    )

                    createProcessedDeclaredLicensesMappedDeclaredLicensesTableEntry(
                        mappedDeclaredLicenseId = createMappedDeclaredLicenseTableEntry(
                            declaredLicense = "not.to.delete.1",
                            mappedLicense = "not.to.delete.2"
                        ).value,
                        processedDeclaredLicenseId = createProcessedDeclaredLicensesTableEntry(
                            packageId = createPackagesTableEntry(purl = "to.delete.3").value
                        ).value
                    )

                    createProcessedDeclaredLicensesUnmappedDeclaredLicensesTableEntry(
                        processedDeclaredLicenseId = createProcessedDeclaredLicensesTableEntry(
                            createPackagesTableEntry(purl = "to.delete.4").value,
                            createProjectsTableEntry(homepageUrl = "to.delete.1").value
                        ).value,
                        unmappedDeclaredLicenseId = createUnmappedDeclaredLicenseTableEntry(
                            unmappedLicense = "not.to.delete.1"
                        ).value
                    )

                    // Orphan entries - should be deleted by removal process.
                    createPackagesTableEntry(purl = "to.delete.4")
                    createPackagesTableEntry(purl = "to.delete.5")

                    // Check packages number before cleanup.
                    PackagesTable.selectAll().count() shouldBe 9
                    MappedDeclaredLicensesTable.selectAll().count() shouldBe 1
                    UnmappedDeclaredLicensesTable.selectAll().count() shouldBe 1
                    ProcessedDeclaredLicensesTable.selectAll().count() shouldBe 3
                }

                service.deleteRunsOrphanedEntities()

                db.dbQuery {
                    PackagesTable.selectAll().count() shouldBe 1
                    PackagesTable.selectAll().toList().forEach {
                        it[PackagesTable.purl] shouldStartWith "not.to.delete"
                    }

                    MappedDeclaredLicensesTable.selectAll().count() shouldBe 1
                    UnmappedDeclaredLicensesTable.selectAll().count() shouldBe 1
                    ProcessedDeclaredLicensesTable.selectAll().count() shouldBe 0
                }
            }

            "delete projects that are not associated with any other entity" {
                db.dbQuery {
                    // Orphan entry - should be deleted by removal process
                    createProjectsTableEntry(homepageUrl = "to.delete.1")

                    // Projects in relation, that should not be removed.
                    createProjectsAnalyzerRunsTableEntry(
                        createProjectsTableEntry(homepageUrl = "not.to.delete.1").value,
                        createAnalyzerRunTableEntry().value
                    )

                    createProjectsDeclaredLicensesTableEntry(
                        createProjectsTableEntry(homepageUrl = "to.delete.2").value,
                        createDeclaredLicensesTableEntry().value
                    )

                    createProjectsAuthorsTableEntry(
                        createProjectsTableEntry(homepageUrl = "to.delete.3").value,
                        createAuthorsTableEntry().value
                    )

                    createProcessedDeclaredLicensesTableEntry(
                        projectId = createProjectsTableEntry(homepageUrl = "to.delete.3").value
                    )

                    createProcessedDeclaredLicensesUnmappedDeclaredLicensesTableEntry(
                        processedDeclaredLicenseId = createProcessedDeclaredLicensesTableEntry(
                            createPackagesTableEntry(purl = "to.delete.4").value,
                            createProjectsTableEntry(homepageUrl = "to.delete.4").value
                        ).value,
                        unmappedDeclaredLicenseId = createUnmappedDeclaredLicenseTableEntry(
                            unmappedLicense = "not.to.delete.1"
                        ).value
                    )

                    createProjectScopesTableEntry(
                        createProjectsTableEntry(homepageUrl = "to.delete.5").value
                    )

                    // Orphan entries - should be deleted by removal process
                    createProjectsTableEntry(homepageUrl = "to.delete.6")
                    createProjectsTableEntry(homepageUrl = "to.delete.7")

                    ProjectsTable.selectAll().count() shouldBe 9
                    ProcessedDeclaredLicensesTable.selectAll().count() shouldBe 2
                    UnmappedDeclaredLicensesTable.selectAll().count() shouldBe 1
                }

                service.deleteRunsOrphanedEntities()

                db.dbQuery {
                    ProjectsTable.selectAll().count() shouldBe 1
                    ProjectsTable.selectAll().toList().forEach {
                        it[ProjectsTable.homepageUrl] shouldStartWith "not.to.delete"
                    }
                    ProcessedDeclaredLicensesTable.selectAll().count() shouldBe 0
                    UnmappedDeclaredLicensesTable.selectAll().count() shouldBe 1
                }
            }

            "delete authors that are not associated with any other entity" {
                db.dbQuery {
                    createAuthorsTableEntry(name = "to.delete.1")

                    createPackagesAuthorsTableEntry(
                        authorId = createAuthorsTableEntry("to.delete.2").value
                    )

                    createProjectsAuthorsTableEntry(
                        authorId = createAuthorsTableEntry("to.delete.3").value
                    )

                    createPackageCurationDataAuthorsTableEntry(
                        authorId = createAuthorsTableEntry("not.to.delete.1").value
                    )

                    val package1Id = createPackagesTableEntry().value
                    createPackageAnalyzerRunsTableEntry(
                        packageId = package1Id,
                        analyzerRunId = createAnalyzerRunTableEntry().value
                    )
                    createPackagesAuthorsTableEntry(
                        authorId = createAuthorsTableEntry("not.to.delete.2").value,
                        packageId = package1Id
                    )

                    val project1Id = createProjectsTableEntry().value
                    createProjectsAnalyzerRunsTableEntry(
                        projectId = project1Id
                    )
                    createProjectsAuthorsTableEntry(
                        authorId = createAuthorsTableEntry("not.to.delete.3").value,
                        projectId = project1Id
                    )

                    createAuthorsTableEntry(name = "to.delete.4")
                    createAuthorsTableEntry(name = "to.delete.5")

                    AuthorsTable.selectAll().count() shouldBe 8
                }

                service.deleteRunsOrphanedEntities()

                db.dbQuery {
                    AuthorsTable.selectAll().count() shouldBe 3
                    AuthorsTable.selectAll().toList().forEach {
                        it[AuthorsTable.name] shouldStartWith "not.to.delete"
                    }
                }
            }

            "delete declared licenses that are not associated with any other entity" {
                db.dbQuery {
                    createDeclaredLicensesTableEntry(name = "to.delete.1")

                    createPackagesDeclaredLicensesTableEntry(
                        declaredLicenseId = createDeclaredLicensesTableEntry("to.delete.2").value
                    )

                    createProjectsDeclaredLicensesTableEntry(
                        declaredLicenseId = createDeclaredLicensesTableEntry("to.delete.3").value
                    )

                    val project1Id = createProjectsTableEntry().value
                    createProjectsAnalyzerRunsTableEntry(
                        projectId = project1Id
                    )

                    createProjectsDeclaredLicensesTableEntry(
                        declaredLicenseId = createDeclaredLicensesTableEntry("not.to.delete.1").value,
                        projectId = project1Id
                    )

                    val package1Id = createPackagesTableEntry().value
                    createPackageAnalyzerRunsTableEntry(
                        packageId = package1Id,
                        analyzerRunId = createAnalyzerRunTableEntry().value
                    )

                    createPackagesDeclaredLicensesTableEntry(
                        declaredLicenseId = createDeclaredLicensesTableEntry("not.to.delete.2").value,
                        packageId = package1Id
                    )

                    createDeclaredLicensesTableEntry(name = "to.delete.4")
                    createDeclaredLicensesTableEntry(name = "to.delete.5")

                    DeclaredLicensesTable.selectAll().count() shouldBe 7
                }

                service.deleteRunsOrphanedEntities()

                db.dbQuery {
                    DeclaredLicensesTable.selectAll().count() shouldBe 2
                    DeclaredLicensesTable.selectAll().toList().forEach {
                        it[DeclaredLicensesTable.name] shouldStartWith "not.to.delete"
                    }
                }
            }

            "delete identifiers that are not associated with any other entity" {
                db.dbQuery {
                    createIdentifierTableEntry(name = "to.delete.1")

                    // Wrapped, to prevent deletion identifier with project
                    createProjectsAnalyzerRunsTableEntry(
                        projectId = createProjectsTableEntry(
                            identifierId = createIdentifierTableEntry(name = "not.to.delete.1").value
                        ).value
                    )

                    // Wrapped, to prevent deletion identifier with package
                    createPackageAnalyzerRunsTableEntry(
                        createPackagesTableEntry(
                            identifierId = createIdentifierTableEntry(name = "not.to.delete.2").value
                        ).value,
                        createAnalyzerRunTableEntry().value
                    )

                    createIdentifiersIssuesTableEntry(
                        identifierId = createIdentifierTableEntry(name = "not.to.delete.3").value
                    )

                    createAdvisorRunsIdentifiersTableEntry(
                        identifierId = createIdentifierTableEntry(name = "not.to.delete.4").value
                    )

                    createPackageProvenancesTableEntry(
                        identifierId = createIdentifierTableEntry(name = "not.to.delete.5").value
                    )

                    createRuleViolationsTableEntry(
                        packageIdentifierId = createIdentifierTableEntry(name = "not.to.delete.6").value
                    )

                    createPackageCurationsTableEntry(
                        identifierId = createIdentifierTableEntry(name = "not.to.delete.7").value
                    )

                    createPackageConfigurationsTableEntry(
                        identifierId = createIdentifierTableEntry(name = "not.to.delete.8").value
                    )

                    createPackageLicenseChoicesTableEntry(
                        identifierId = createIdentifierTableEntry(name = "not.to.delete.9").value
                    )

                    createScannerRunsScannersTableEntry(
                        identifierId = createIdentifierTableEntry(name = "not.to.delete.10").value
                    )

                    createOrtRunsIssuesTableEntry(
                        identifierId = createIdentifierTableEntry(name = "not.to.delete.11").value
                    )

                    createIdentifierTableEntry(name = "to.delete.2")
                    createIdentifierTableEntry(name = "to.delete.3")

                    IdentifiersTable.selectAll().count() shouldBe 14
                }

                service.deleteRunsOrphanedEntities()

                db.dbQuery {
                    IdentifiersTable.selectAll().count() shouldBe 11
                    IdentifiersTable.selectAll().toList().forEach {
                        it[IdentifiersTable.name] shouldStartWith "not.to.delete"
                    }
                }
            }

            "delete vcsInfos that are not associated with any other entity" {
                db.dbQuery {
                    // Orphan entries - should be deleted by removal process
                    createVcsInfoTableEntry(url = "to.delete1")
                    createVcsInfoTableEntry(url = "to.delete2")

                    createOrtRunTableEntry(
                        vcsId = createVcsInfoTableEntry(url = "not.to.delete_1").value,
                        vcsProcessedId = null
                    )

                    createOrtRunTableEntry(
                        vcsId = null,
                        vcsProcessedId = createVcsInfoTableEntry(url = "not.to.delete_2").value
                    )

                    // Project wrapped with author to prevent cascade deletion
                    createProjectsAnalyzerRunsTableEntry(
                        projectId = createProjectsTableEntry(
                            vcsId = createVcsInfoTableEntry(url = "not.to.delete_3").value,
                            vcsProcessedId = createVcsInfoTableEntry(url = "not.to.delete_4").value
                        ).value
                    )

                    // Package wrapped in run to prevent cascade deletion.
                    createPackageAnalyzerRunsTableEntry(
                        createPackagesTableEntry(
                            vcsId = createVcsInfoTableEntry(url = "not.to.delete_5").value,
                            vcsProcessedId = createVcsInfoTableEntry(url = "not.to.delete_6").value
                        ).value,
                        createAnalyzerRunTableEntry().value
                    )

                    createNestedRepositoriesTableEntry(
                        vcsId = createVcsInfoTableEntry(url = "not.to.delete_7").value
                    )

                    createSnippetsTableEntry(
                        vcsId = createVcsInfoTableEntry(url = "not.to.delete_8").value
                    )

                    // More orphan entries - should be deleted by removal process
                    createVcsInfoTableEntry(url = "to.delete3")

                    VcsInfoTable.selectAll().count() shouldBe 11
                }

                service.deleteRunsOrphanedEntities()

                db.dbQuery {
                    VcsInfoTable.selectAll().count() shouldBe 8
                    VcsInfoTable.selectAll().toList().forEach {
                        it[VcsInfoTable.url] shouldStartWith "not.to.delete"
                    }
                }
            }

            "delete remote artifacts that are not associated with any other entity" {
                db.dbQuery {
                    createRemoteArtifactsTableEntry(url = "to.delete.1")

                    // Package-related entries wrapped to prevent cascade deletion with package
                    createPackageAnalyzerRunsTableEntry(
                        packageId = createPackagesTableEntry(
                            binaryArtifactId = createRemoteArtifactsTableEntry(url = "not.to.delete.1").value,
                            sourceArtifactId = createRemoteArtifactsTableEntry(url = "not.to.delete.2").value
                        ).value,
                        createAnalyzerRunTableEntry().value
                    )

                    createPackageProvenancesTableEntry(
                        artifactId = createRemoteArtifactsTableEntry("not.to.delete.3").value
                    )

                    createPackageCurationDataTableEntry(
                        binaryArtifactId = createRemoteArtifactsTableEntry(url = "not.to.delete.4").value,
                        sourceArtifactId = createRemoteArtifactsTableEntry(url = "not.to.delete.5").value
                    )

                    createSnippetsTableEntry(
                        artifactId = createRemoteArtifactsTableEntry(url = "not.to.delete.6").value
                    )

                    createRemoteArtifactsTableEntry(url = "to.delete.2")
                    createRemoteArtifactsTableEntry(url = "to.delete.3")

                    RemoteArtifactsTable.selectAll().count() shouldBe 9
                }

                service.deleteRunsOrphanedEntities()

                db.dbQuery {
                    RemoteArtifactsTable.selectAll().count() shouldBe 6
                    RemoteArtifactsTable.selectAll().toList().forEach {
                        it[RemoteArtifactsTable.url] shouldStartWith "not.to.delete"
                    }
                }
            }

            "delete findings, snippets and scan summaries not related to any other entity" {
                db.dbQuery {

                    // Create scan result not related to scan run.
                    val scanSummaryId = createScanSummaryTableEntry(hash = "to.delete.1").value
                    createScanResultsTableEntry(artifactUrl = "to.delete.1", scanSummaryId = scanSummaryId)
                    createCopyrightFindingsTableEntry(scanSummaryId = scanSummaryId, path = "to.delete.2")
                    createCopyrightFindingsTableEntry(scanSummaryId = scanSummaryId, path = "to.delete.3")
                    createLicenseFindingsTableEntry(scanSummaryId = scanSummaryId, path = "to.delete.4")
                    createLicenseFindingsTableEntry(scanSummaryId = scanSummaryId, path = "to.delete.5")

                    createSnippetFindingsSnippetsTableEntry(
                        snippetFindingId = createSnippetFindingsTableEntry(
                            scanSummaryId = scanSummaryId,
                            path = "to.delete.6"
                        ).value,
                        snippetId = createSnippetsTableEntry(purl = "to.delete.7").value
                    )

                    createSnippetFindingsSnippetsTableEntry(
                        snippetFindingId = createSnippetFindingsTableEntry(
                            scanSummaryId = scanSummaryId,
                            path = "to.delete.8"
                        ).value,
                        snippetId = createSnippetsTableEntry(purl = "to.delete.9").value
                    )

                    // Create scan result related to scan run.
                    val scanSummary2Id = createScanSummaryTableEntry(hash = "not.to.delete.1").value
                    createCopyrightFindingsTableEntry(scanSummaryId = scanSummary2Id, path = "not.to.delete.2")
                    createCopyrightFindingsTableEntry(scanSummaryId = scanSummary2Id, path = "not.to.delete.3")
                    createLicenseFindingsTableEntry(scanSummaryId = scanSummary2Id, path = "not.to.delete.4")
                    createLicenseFindingsTableEntry(scanSummaryId = scanSummary2Id, path = "not.to.delete.5")

                    createSnippetFindingsSnippetsTableEntry(
                        snippetFindingId = createSnippetFindingsTableEntry(
                            scanSummaryId = scanSummary2Id,
                            path = "not.to.delete.6"
                        ).value,
                        snippetId = createSnippetsTableEntry(purl = "not.to.delete.7").value
                    )

                    createSnippetFindingsSnippetsTableEntry(
                        snippetFindingId = createSnippetFindingsTableEntry(
                            scanSummaryId = scanSummary2Id,
                            path = "not.to.delete.8"
                        ).value,
                        snippetId = createSnippetsTableEntry(purl = "not.to.delete.9").value
                    )

                    createScannerRunsScanResultsTableEntry(
                        scannerRunId = createScannerRunsScannersTableEntry().value,
                        scanResultId = createScanResultsTableEntry(
                            artifactUrl = "not.to.delete.8", scanSummaryId = scanSummary2Id
                        ).value
                    )
                }

                db.dbQuery {
                    ScanSummariesTable.selectAll().count() shouldBe 2
                    ScanResultsTable.selectAll().count() shouldBe 2
                    SnippetFindingsTable.selectAll().count() shouldBe 4
                    LicenseFindingsTable.selectAll().count() shouldBe 4
                    CopyrightFindingsTable.selectAll().count() shouldBe 4
                    SnippetsTable.selectAll().count() shouldBe 4
                }

                service.deleteRunsOrphanedEntities()

                db.dbQuery {
                    ScanSummariesTable.selectAll().count() shouldBe 1
                    ScanSummariesTable.selectAll().forEach {
                        it[ScanSummariesTable.hash] shouldStartWith "not.to.delete"
                    }
                    ScanResultsTable.selectAll().count() shouldBe 1
                    ScanResultsTable.selectAll().forEach {
                        it[ScanResultsTable.artifactUrl] shouldStartWith "not.to.delete"
                    }
                    SnippetFindingsSnippetsTable.selectAll().count() shouldBe 2

                    SnippetFindingsTable.selectAll().count() shouldBe 2
                    SnippetFindingsTable.selectAll().forEach {
                        it[SnippetFindingsTable.path] shouldStartWith "not.to.delete"
                    }
                    LicenseFindingsTable.selectAll().count() shouldBe 2
                    LicenseFindingsTable.selectAll().forEach {
                        it[LicenseFindingsTable.path] shouldStartWith "not.to.delete"
                    }
                    CopyrightFindingsTable.selectAll().count() shouldBe 2
                    CopyrightFindingsTable.selectAll().forEach {
                        it[CopyrightFindingsTable.path] shouldStartWith "not.to.delete"
                    }
                    SnippetsTable.selectAll().count() shouldBe 2
                    SnippetsTable.selectAll().forEach {
                        it[SnippetsTable.purl] shouldStartWith "not.to.delete"
                    }
                }
            }
        }
    }
}
