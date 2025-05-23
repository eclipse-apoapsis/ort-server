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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import java.security.MessageDigest

import kotlin.random.Random

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.MappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesMappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectScopesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.UnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataAuthors
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.VcsInfoCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsSnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphsWrapper

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
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

                service.deleteRunsOrphanedEntities(createConfigManager())

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

                service.deleteRunsOrphanedEntities(createConfigManager())

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

                service.deleteRunsOrphanedEntities(createConfigManager())

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

                service.deleteRunsOrphanedEntities(createConfigManager())

                db.dbQuery {
                    DeclaredLicensesTable.selectAll().count() shouldBe 2
                    DeclaredLicensesTable.selectAll().toList().forEach {
                        it[DeclaredLicensesTable.name] shouldStartWith "not.to.delete"
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

                service.deleteRunsOrphanedEntities(createConfigManager())

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

                service.deleteRunsOrphanedEntities(createConfigManager())

                db.dbQuery {
                    RemoteArtifactsTable.selectAll().count() shouldBe 6
                    RemoteArtifactsTable.selectAll().toList().forEach {
                        it[RemoteArtifactsTable.url] shouldStartWith "not.to.delete"
                    }
                }
            }

            "take the limit into account when deleting entities" {
                db.dbQuery {
                    (1..16).forEach {
                        createRemoteArtifactsTableEntry(url = "https://repo.example.com/artifact-$it")
                    }
                }

                service.deleteRunsOrphanedEntities(createConfigManager())

                db.dbQuery {
                    RemoteArtifactsTable.selectAll().count() shouldBe 6
                }
            }

            "delete snippet associations which are no longer assigned to an ORT run" {
                val remainingSnippet = db.dbQuery {
                    val run = createOrtRunTableEntry().value
                    val scanSummary1 = createScanSummariesTableEntry().value
                    val scanSummary2 = createScanSummariesTableEntry().value
                    val snippet1 = createSnippetsTableEntry().value
                    val snippet2 = createSnippetsTableEntry().value
                    val snippetFinding1 = createSnippetFindingTableEntry(scanSummary1).value
                    val snippetFinding2 = createSnippetFindingTableEntry(scanSummary2).value
                    createSnippetFindingsSnippetsTableEntry(snippetFinding1, snippet1)
                    createSnippetFindingsSnippetsTableEntry(snippetFinding2, snippet2)
                    assignScanSummaryWithRun(run, scanSummary1)
                    snippet1
                }

                service.deleteRunsOrphanedEntities(createConfigManager())

                db.dbQuery {
                    val snippetAssociation = SnippetFindingsSnippetsTable.selectAll().single()
                    snippetAssociation[SnippetFindingsSnippetsTable.snippetId].value shouldBe remainingSnippet
                }
            }
        }

        "deleteOrphanedSnippets" should {
            "only delete a limited number of orphaned snippets at a time" {
                val numberOfSnippets = 100
                db.dbQuery {
                    repeat(numberOfSnippets) {
                        createSnippetsTableEntry()
                    }
                }

                service.deleteRunsOrphanedEntities(createConfigManager())

                db.dbQuery(readOnly = true) {
                    SnippetsTable.selectAll().count() shouldBe (numberOfSnippets - 10) // 10 is the limit in this test
                }
            }

            "does not delete snippets that have snipped findings" {
                val numberOfSnippets = 100
                db.dbQuery {
                    val runId = createOrtRunTableEntry().value
                    val scanSummaryId = createScanSummariesTableEntry().value
                    assignScanSummaryWithRun(runId, scanSummaryId)
                    val snippedFindingId = createSnippetFindingTableEntry(scanSummaryId = scanSummaryId)

                    repeat(numberOfSnippets) {
                        val snippedId = createSnippetsTableEntry()
                        createSnippetFindingsSnippetsTableEntry(snippedFindingId.value, snippedId.value)
                    }
                }

                // Add 5 orphan snippets
                db.dbQuery {
                    repeat(5) {
                        createSnippetsTableEntry()
                    }
                }

                service.deleteRunsOrphanedEntities(createConfigManager())

                db.dbQuery(readOnly = true) {
                    SnippetsTable.selectAll().count() shouldBe (numberOfSnippets) // Nothing deleted but the orphans
                }
            }

            "delete a limited number of orphaned snippet findings at a time" {
                val numberOfSnippetFindings = 100
                db.dbQuery {
                    val summary = createScanSummariesTableEntry().value
                    repeat(numberOfSnippetFindings) {
                        createSnippetFindingTableEntry(summary)
                    }
                }

                service.deleteRunsOrphanedEntities(createConfigManager())

                db.dbQuery(readOnly = true) {
                    SnippetFindingsTable
                        .selectAll().count() shouldBe (numberOfSnippetFindings - 11) // 11 is the limit in this test
                }
            }

            "not delete snippet findings that have snippets" {
                val numberOfSnippetFindings = 100
                db.dbQuery {
                    val runId = createOrtRunTableEntry().value
                    val scanSummaryId = createScanSummariesTableEntry().value
                    assignScanSummaryWithRun(runId, scanSummaryId)

                    repeat(numberOfSnippetFindings) {
                        val snippedFindingId = createSnippetFindingTableEntry(scanSummaryId = scanSummaryId)
                        val snippedId = createSnippetsTableEntry()
                        createSnippetFindingsSnippetsTableEntry(snippedFindingId.value, snippedId.value)
                    }

                    // Add 5 orphan snippet findings
                    repeat(5) {
                        createSnippetFindingTableEntry(scanSummaryId)
                    }
                }

                service.deleteRunsOrphanedEntities(createConfigManager())

                db.dbQuery(readOnly = true) {
                    SnippetsTable
                        .selectAll().count() shouldBe (numberOfSnippetFindings) // Nothing deleted but the orphans
                }
            }
        }
    }

    @Suppress("LongParameterList")
    private fun createOrtRunTableEntry(
        index: Long = Random.nextLong(1, 10000),
        repositoryId: Long = createRepositoryTableEntry().value,
        revision: String = "rev1",
        createdAt: Instant = Clock.System.now(),
        jobConfigs: JobConfigurations = JobConfigurations(),
        status: OrtRunStatus = OrtRunStatus.CREATED,
        vcsId: Long? = null,
        vcsProcessedId: Long? = null
    ) = OrtRunsTable.insert {
        it[this.index] = index
        it[this.repositoryId] = repositoryId
        it[this.revision] = revision
        it[this.createdAt] = createdAt
        it[this.jobConfigs] = jobConfigs
        it[this.status] = status
        it[this.vcsId] = vcsId
        it[this.vcsProcessedId] = vcsProcessedId
    } get OrtRunsTable.id

    private fun createVcsInfoTableEntry(
        type: String = "type_" + Random.nextLong(1, 10000),
        url: String = "url_" + Random.nextLong(1, 10000),
        revision: String = "rev_" + Random.nextLong(1, 10000),
        path: String = "path/" + Random.nextLong(1, 10000)
    ) = VcsInfoTable.insert {
        it[this.type] = type
        it[this.url] = url
        it[this.revision] = revision
        it[this.path] = path
    } get VcsInfoTable.id

    private fun createRepositoryTableEntry(
        type: String = "GIT",
        url: String = "http://some.%d.url".format(Random.nextInt(0, 10000)),
        productId: Long = createProductTableEntry().value
    ) = RepositoriesTable.insert {
        it[this.type] = type
        it[this.url] = url
        it[this.productId] = productId
    } get RepositoriesTable.id

    private fun createProductTableEntry(
        name: String = "Prodct_" + Random.nextInt(0, 10000),
        organizationId: Long = createOrganizationsTableEntry().value
    ) = ProductsTable.insert {
        it[this.name] = name
        it[this.organizationId] = organizationId
    } get ProductsTable.id

    private fun createOrganizationsTableEntry(
        name: String = "Org_" + Random.nextInt(0, 10000)
    ) = OrganizationsTable.insert {
        it[this.name] = name
    } get OrganizationsTable.id

    private fun createProjectsTableEntry(
        identifierId: Long = createIdentifierTableEntry().value,
        vcsId: Long = createVcsInfoTableEntry().value,
        vcsProcessedId: Long = createVcsInfoTableEntry().value,
        homepageUrl: String = "http://homepage.%d.url".format(Random.nextInt(0, 10000)),
        definitionFilePath: String = "path_" + Random.nextInt(0, 10000)
    ) = ProjectsTable.insert {
        it[this.identifierId] = identifierId
        it[this.vcsId] = vcsId
        it[this.vcsProcessedId] = vcsProcessedId
        it[this.homepageUrl] = homepageUrl
        it[this.definitionFilePath] = definitionFilePath
    } get ProjectsTable.id

    private fun createAuthorsTableEntry(
        name: String = "author_" + Random.nextInt(0, 10000)
    ) = AuthorsTable.insert {
        it[this.name] = name
    } get AuthorsTable.id

    private fun createProjectsAuthorsTableEntry(
        projectId: Long = createProjectsTableEntry().value,
        authorId: Long = createAuthorsTableEntry().value
    ) = ProjectsAuthorsTable.insert {
        it[this.projectId] = projectId
        it[this.authorId] = authorId
    }

    private fun createPackagesAuthorsTableEntry(
        authorId: Long = createAuthorsTableEntry().value,
        packageId: Long = createPackagesTableEntry().value
    ) = PackagesAuthorsTable.insert {
        it[this.authorId] = authorId
        it[this.packageId] = packageId
    }

    private fun createPackageCurationDataAuthorsTableEntry(
        authorId: Long = createAuthorsTableEntry().value,
        packageCurationDataId: Long = createPackageCurationDataTableEntry().value
    ) = PackageCurationDataAuthors.insert {
        it[this.authorId] = authorId
        it[this.packageCurationDataId] = packageCurationDataId
    }

    private fun createPackageCurationDataTableEntry() =
        PackageCurationDataTable.insert {
            it[this.binaryArtifactId] = createRemoteArtifactsTableEntry()
            it[this.sourceArtifactId] = createRemoteArtifactsTableEntry()
        } get PackageCurationDataTable.id

    private fun createProjectsAnalyzerRunsTableEntry(
        projectId: Long = createProjectsTableEntry().value,
        analyzerRunId: Long = createAnalyzerRunTableEntry().value
    ) = ProjectsAnalyzerRunsTable.insert {
        it[this.projectId] = projectId
        it[this.analyzerRunId] = analyzerRunId
    }

    private fun createIdentifierTableEntry(
        type: String = "type_" + Random.nextInt(0, 10000),
        namespace: String = "namespace_" + Random.nextInt(0, 10000),
        name: String = "name_" + Random.nextInt(0, 10000),
        version: String = "version_" + Random.nextInt(0, 10000)
    ) = IdentifiersTable.insert {
        it[this.type] = type
        it[this.namespace] = namespace
        it[this.name] = name
        it[this.version] = version
    } get IdentifiersTable.id

    private fun createPackageProvenancesTableEntry(
        identifierId: Long = createIdentifierTableEntry().value,
        artifactId: Long = createRemoteArtifactsTableEntry().value,
        vcsId: Long = createVcsInfoTableEntry().value
    ) = PackageProvenancesTable.insert {
        it[this.identifierId] = identifierId
        it[this.artifactId] = artifactId
        it[this.vcsId] = vcsId
    } get PackageProvenancesTable.id

    private fun createPackageCurationDataTableEntry(
        binaryArtifactId: Long = createRemoteArtifactsTableEntry().value,
        sourceArtifactId: Long = createRemoteArtifactsTableEntry().value,
        vcsInfoCurationDataId: Long = createVcsInfoCurationDataTableEntry().value,
        hasAuthors: Boolean = false,
    ) = PackageCurationDataTable.insert {
        it[this.binaryArtifactId] = binaryArtifactId
        it[this.sourceArtifactId] = sourceArtifactId
        it[this.vcsInfoCurationDataId] = vcsInfoCurationDataId
        it[this.hasAuthors] = hasAuthors
    } get PackageCurationDataTable.id

    private fun createVcsInfoCurationDataTableEntry() =
        VcsInfoCurationDataTable.insert {
            it[this.type] = "type_" + Random.nextInt(0, 10000)
            it[this.url] = "http://homepage.%d.url".format(Random.nextInt(0, 10000))
            it[this.revision] = "rev_" + Random.nextInt(0, 10000)
            it[this.path] = "path/" + Random.nextInt(0, 10000)
        } get VcsInfoCurationDataTable.id

    @Suppress("LongParameterList")
    private fun createPackagesTableEntry(
        identifierId: Long = createIdentifierTableEntry().value,
        vcsId: Long = createVcsInfoTableEntry().value,
        vcsProcessedId: Long = createVcsInfoTableEntry().value,
        binaryArtifactId: Long = createRemoteArtifactsTableEntry().value,
        sourceArtifactId: Long = createRemoteArtifactsTableEntry().value,
        purl: String = "purl_" + Random.nextInt(0, 10000),
        cpe: String = "cpe_" + Random.nextInt(0, 10000),
        description: String = "description_" + Random.nextInt(0, 10000),
        homepageUrl: String = "some.nome_%d.url".format(Random.nextInt(0, 10000)),
        isMetadataOnly: Boolean = false,
        isModified: Boolean = false
    ) = PackagesTable.insert {
        it[this.identifierId] = identifierId
        it[this.vcsId] = vcsId
        it[this.vcsProcessedId] = vcsProcessedId
        it[this.binaryArtifactId] = binaryArtifactId
        it[this.sourceArtifactId] = sourceArtifactId
        it[this.purl] = purl
        it[this.cpe] = cpe
        it[this.description] = description
        it[this.homepageUrl] = homepageUrl
        it[this.isMetadataOnly] = isMetadataOnly
        it[this.isModified] = isModified
    } get PackagesTable.id

    private fun createProcessedDeclaredLicensesTableEntry(
        packageId: Long = createPackagesTableEntry().value,
        projectId: Long = createProjectsTableEntry().value,
        spdxExpression: String = "spdx_expression_" + Random.nextInt(0, 10000)
    ) = ProcessedDeclaredLicensesTable.insert {
        it[this.packageId] = packageId
        it[this.projectId] = projectId
        it[this.spdxExpression] = spdxExpression
    } get ProcessedDeclaredLicensesTable.id

    private fun createPackageAnalyzerRunsTableEntry(
        packageId: Long,
        analyzerRunId: Long
    ) = PackagesAnalyzerRunsTable.insert {
        it[this.packageId] = packageId
        it[this.analyzerRunId] = analyzerRunId
    }

    private fun createAnalyzerJobTableEntry() =
        AnalyzerJobsTable.insert {
            it[this.ortRunId] = createOrtRunTableEntry()
            it[this.createdAt] = Clock.System.now()
            it[this.configuration] = AnalyzerJobConfiguration()
            it[this.status] = JobStatus.CREATED
        } get AnalyzerJobsTable.id

    private fun createAnalyzerRunTableEntry() =
        AnalyzerRunsTable.insert {
            it[this.analyzerJobId] = createAnalyzerJobTableEntry()
            it[this.environmentId] = createEnvironmentTableEntry()
            it[this.startTime] = Clock.System.now()
            it[this.endTime] = Clock.System.now()
            it[this.dependencyGraphs] = DependencyGraphsWrapper(emptyMap())
        } get AnalyzerRunsTable.id

    private fun createEnvironmentTableEntry() =
        EnvironmentsTable.insert {
            it[this.ortVersion] = "ver_" + Random.nextInt(0, 10000)
            it[this.javaVersion] = "22"
            it[this.os] = "Linux"
            it[this.processors] = 1
            it[this.maxMemory] = Random.nextLong(100, 10000)
        } get EnvironmentsTable.id

    private fun createNestedRepositoriesTableEntry(
        ortRunId: Long = createOrtRunTableEntry().value,
        path: String = "path/" + Random.nextInt(0, 10000),
        vcsId: Long = createVcsInfoTableEntry().value
    ) = NestedRepositoriesTable.insert {
        it[this.ortRunId] = ortRunId
        it[this.path] = path
        it[this.vcsId] = vcsId
    } get NestedRepositoriesTable.id

    private fun createScanSummariesTableEntry() = ScanSummariesTable.insert {
        it[this.startTime] = Clock.System.now()
        it[this.endTime] = Clock.System.now()
    } get ScanSummariesTable.id

    /**
     * Generate the required structures to assign the scan summary with the given [scanSummaryId] to the run with the
     * given [runId].
     */
    private fun assignScanSummaryWithRun(runId: Long, scanSummaryId: Long) {
        val jobId = ScannerJobsTable.insert {
            it[this.ortRunId] = runId
            it[this.createdAt] = Clock.System.now()
            it[this.startedAt] = Clock.System.now()
            it[this.finishedAt] = Clock.System.now()
            it[this.status] = JobStatus.FINISHED
            it[this.configuration] = ScannerJobConfiguration()
        } get ScannerJobsTable.id

        val scannerRunId = ScannerRunsTable.insert {
            it[this.startTime] = Clock.System.now()
            it[this.endTime] = Clock.System.now()
            it[this.scannerJobId] = jobId
        } get ScannerRunsTable.id

        val scanResultId = ScanResultsTable.insert {
            it[this.scanSummaryId] = scanSummaryId
            it[this.scannerName] = "SomeSnippetScanner"
            it[this.scannerVersion] = "1.0.0"
            it[this.scannerConfiguration] = "--really-fast"
        } get ScanResultsTable.id

        ScannerRunsScanResultsTable.insertIfNotExists(scannerRunId.value, scanResultId.value)
    }

    private fun createSnippetFindingTableEntry(
        scanSummaryId: Long,
        path: String = "path/" + Random.nextInt(0, 10000),
        startLine: Int = Random.nextInt(0, 10000),
        endLine: Int = Random.nextInt(0, 10000),
    ) = SnippetFindingsTable.insert {
        it[this.scanSummaryId] = scanSummaryId
        it[this.path] = path
        it[this.startLine] = startLine
        it[this.endLine] = endLine
    } get SnippetFindingsTable.id

    private fun createSnippetFindingsSnippetsTableEntry(
        snippedFindingId: Long,
        snippedId: Long
    ) = SnippetFindingsSnippetsTable.insert {
        it[this.snippetFindingId] = snippedFindingId
        it[this.snippetId] = snippedId
    }

    @Suppress("LongParameterList")
    private fun createSnippetsTableEntry(
        purl: String = "purl_" + Random.nextInt(0, 10000),
        artifactId: Long = createRemoteArtifactsTableEntry().value,
        vcsId: Long = createVcsInfoTableEntry().value,
        path: String = "path/" + Random.nextInt(0, 10000),
        startLine: Int = Random.nextInt(0, 10000),
        endLine: Int = Random.nextInt(0, 10000),
        license: String = "Lic_" + Random.nextInt(0, 10000),
        score: Float = Random.nextFloat()
    ) = SnippetsTable.insert {
        it[this.purl] = purl
        it[this.artifactId] = artifactId
        it[this.vcsId] = vcsId
        it[this.path] = path
        it[this.startLine] = startLine
        it[this.endLine] = endLine
        it[this.license] = license
        it[this.score] = score
    } get SnippetsTable.id

    private fun createRemoteArtifactsTableEntry(
        url: String = "git.someurl.org",
        hashValue: String = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).toString(),
        hashAlgorithm: String = "SHA1"
    ) = RemoteArtifactsTable.insert {
        it[this.url] = url
        it[this.hashValue] = hashValue
        it[this.hashAlgorithm] = hashAlgorithm
    } get RemoteArtifactsTable.id

    private fun createDeclaredLicensesTableEntry(
        name: String = "name_" + Random.nextInt(0, 10000)
    ) = DeclaredLicensesTable.insert {
        it[this.name] = name
    } get DeclaredLicensesTable.id

    private fun createPackagesDeclaredLicensesTableEntry(
        packageId: Long = createPackagesTableEntry().value,
        declaredLicenseId: Long = createDeclaredLicensesTableEntry().value
    ) = PackagesDeclaredLicensesTable.insert {
        it[this.packageId] = packageId
        it[this.declaredLicenseId] = declaredLicenseId
    }

    private fun createProjectsDeclaredLicensesTableEntry(
        projectId: Long = createProjectsTableEntry().value,
        declaredLicenseId: Long = createDeclaredLicensesTableEntry().value
    ) = ProjectsDeclaredLicensesTable.insert {
        it[this.projectId] = projectId
        it[this.declaredLicenseId] = declaredLicenseId
    }

    private fun createProcessedDeclaredLicensesMappedDeclaredLicensesTableEntry(
        mappedDeclaredLicenseId: Long = createMappedDeclaredLicenseTableEntry().value,
        processedDeclaredLicenseId: Long = createProcessedDeclaredLicensesTableEntry().value
    ) = ProcessedDeclaredLicensesMappedDeclaredLicensesTable.insert {
        it[this.mappedDeclaredLicenseId] = mappedDeclaredLicenseId
        it[this.processedDeclaredLicenseId] = processedDeclaredLicenseId
    }

    private fun createProcessedDeclaredLicensesUnmappedDeclaredLicensesTableEntry(
        unmappedDeclaredLicenseId: Long = createUnmappedDeclaredLicenseTableEntry().value,
        processedDeclaredLicenseId: Long = createProcessedDeclaredLicensesTableEntry().value
    ) = ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.insert {
        it[this.unmappedDeclaredLicenseId] = unmappedDeclaredLicenseId
        it[this.processedDeclaredLicenseId] = processedDeclaredLicenseId
    }

    private fun createMappedDeclaredLicenseTableEntry(
        declaredLicense: String = "license_" + Random.nextInt(0, 10000),
        mappedLicense: String = "license_" + Random.nextInt(0, 10000)
    ) = MappedDeclaredLicensesTable.insert {
        it[this.declaredLicense] = declaredLicense
        it[this.mappedLicense] = mappedLicense
    } get MappedDeclaredLicensesTable.id

    private fun createUnmappedDeclaredLicenseTableEntry(
        unmappedLicense: String = "license_" + Random.nextInt(0, 10000)
    ) = UnmappedDeclaredLicensesTable.insert {
        it[this.unmappedLicense] = unmappedLicense
    } get UnmappedDeclaredLicensesTable.id

    private fun createProjectScopesTableEntry(
        projectId: Long = createProjectsTableEntry().value,
        name: String = "name_" + Random.nextInt(0, 10000)
    ) =
        ProjectScopesTable.insert {
            it[this.projectId] = projectId
            it[this.name] = name
        } get ProjectScopesTable.id
}

/**
 * Return a [ConfigManager] instance with the default configuration for orphan deletion.
 */
private fun createConfigManager(): ConfigManager {
    val configMap = mapOf(
        "vcsInfo.limit" to "10",
        "vcsInfo.chunkSize" to "2",
        "remoteArtifacts.limit" to "10",
        "remoteArtifacts.chunkSize" to "2",
        "snippets.limit" to "10",
        "snippets.chunkSize" to "5",
        "snippetFindings.limit" to "11",
        "snippetFindings.chunkSize" to "7"
    )

    return ConfigManager.create(ConfigFactory.parseMap(configMap))
}
