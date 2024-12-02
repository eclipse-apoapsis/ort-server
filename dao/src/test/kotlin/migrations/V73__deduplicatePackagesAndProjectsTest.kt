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

package org.eclipse.apoapsis.ortserver.dao.migrations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerConfigurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AuthorDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.MappedDeclaredLicenseDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackageDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicenseDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesMappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectScopeDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.UnmappedDeclaredLicenseDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicenseDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseMigrationTestExtension
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphsWrapper
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo

import org.jetbrains.exposed.sql.insert

@Suppress("ClassNaming")
class V73__deduplicatePackagesAndProjectsTest : StringSpec() {
    val extension = extension(DatabaseMigrationTestExtension("72", "73"))

    // TODO: The tests in this class are disabled because they are incompatible with a later migration. To enable them
    //       again, they must not depend on the Exposed tables and DAOs but instead use copies of them that are
    //       compatible with the schema version under test.

    init {
        "package migration should deduplicate packages".config(enabled = false) {
            val run1 = extension.fixtures.createOrtRun()
            val run2 = extension.fixtures.createOrtRun(revision = "revision2")

            val analyzerRun1 = createAnalyzerRun(run1)
            val analyzerRun2 = createAnalyzerRun(run2)

            val specialPackages = extension.db.dbQuery {
                (1..32).forEach { _ ->
                    createPackage(basePackage, null)
                }

                createPackage(basePackage, analyzerRun1)
                createPackage(basePackage, analyzerRun2)

                val pkg1 = basePackage.copy(description = "This is another description")
                createPackage(pkg1, analyzerRun1)

                val pkg2 = basePackage.copy(authors = basePackage.authors + "anotherAuthor")
                createPackage(pkg2, analyzerRun1)

                val pkg3 = basePackage.copy(declaredLicenses = basePackage.declaredLicenses + "anotherLicense")
                createPackage(pkg3, analyzerRun1)

                val processedLicense = baseProcessedDeclaredLicense.copy(
                    unmappedLicenses = baseProcessedDeclaredLicense.unmappedLicenses + "anotherUnmappedLicense"
                )
                val pkg4 = basePackage.copy(processedDeclaredLicense = processedLicense)
                createPackage(pkg4, analyzerRun1)

                setOf(pkg1, pkg2, pkg3, pkg4)
            }

            extension.testAppliedMigration {
                extension.db.dbQuery {
                    PackageDao.all().count() shouldBe 5
                }

                val newRun1 = extension.fixtures.analyzerRunRepository.get(analyzerRun1.id.value).shouldNotBeNull()
                val newRun2 = extension.fixtures.analyzerRunRepository.get(analyzerRun2.id.value).shouldNotBeNull()

                newRun1.packages shouldContainExactlyInAnyOrder listOf(basePackage, *specialPackages.toTypedArray())
                newRun2.packages shouldContainExactlyInAnyOrder listOf(basePackage)
            }
        }

        "project migration should deduplicate projects".config(enabled = false) {
            val run1 = extension.fixtures.createOrtRun()
            val run2 = extension.fixtures.createOrtRun(revision = "revision2")

            val analyzerRun1 = createAnalyzerRun(run1)
            val analyzerRun2 = createAnalyzerRun(run2)

            val specialProjects = extension.db.dbQuery {
                (1..32).forEach { _ ->
                    createProject(baseProject, null)
                }

                createProject(baseProject, analyzerRun1)
                createProject(baseProject, analyzerRun2)

                val project1 = baseProject.copy(homepageUrl = "https://example.com/anotherProject")
                createProject(project1, analyzerRun1)

                val project2 = baseProject.copy(authors = baseProject.authors + "anotherProjectAuthor")
                createProject(project2, analyzerRun1)

                val project3 = baseProject.copy(declaredLicenses = basePackage.declaredLicenses + "anotherLicense")
                createProject(project3, analyzerRun1)

                val processedLicense = baseProcessedDeclaredLicense.copy(
                    unmappedLicenses = baseProcessedDeclaredLicense.unmappedLicenses + "anotherUnmappedLicense"
                )
                val project4 = baseProject.copy(processedDeclaredLicense = processedLicense)
                createProject(project4, analyzerRun1)

                val project5 = baseProject.copy(scopeNames = baseProject.scopeNames + "debug")
                createProject(project5, analyzerRun1)

                setOf(project1, project2, project3, project4, project5)
            }

            extension.testAppliedMigration {
                extension.db.dbQuery {
                    ProjectDao.all().count() shouldBe 6
                }

                val newRun1 = extension.fixtures.analyzerRunRepository.get(analyzerRun1.id.value).shouldNotBeNull()
                val newRun2 = extension.fixtures.analyzerRunRepository.get(analyzerRun2.id.value).shouldNotBeNull()

                newRun1.projects shouldContainExactlyInAnyOrder listOf(baseProject, *specialProjects.toTypedArray())
                newRun2.projects shouldContainExactlyInAnyOrder listOf(baseProject)
            }
        }
    }

    private fun createPackage(pkg: Package, analyzerRun: AnalyzerRunDao?): PackageDao {
        val identifier = IdentifierDao.getOrPut(pkg.identifier)
        val vcs = VcsInfoDao.getOrPut(pkg.vcs)
        val vcsProcessed = VcsInfoDao.getOrPut(pkg.vcsProcessed)
        val binaryArtifact = RemoteArtifactDao.getOrPut(pkg.binaryArtifact)
        val sourceArtifact = RemoteArtifactDao.getOrPut(pkg.sourceArtifact)

        val pkgDao = PackageDao.new {
            this.identifier = identifier
            this.vcs = vcs
            this.vcsProcessed = vcsProcessed
            this.binaryArtifact = binaryArtifact
            this.sourceArtifact = sourceArtifact

            this.cpe = pkg.cpe
            this.purl = pkg.purl
            this.description = pkg.description
            this.homepageUrl = pkg.homepageUrl
            this.isMetadataOnly = pkg.isMetadataOnly
            this.isModified = pkg.isModified
        }

        pkg.authors.forEach { author ->
            val authorDao = AuthorDao.getOrPut(author)
            PackagesAuthorsTable.insert {
                it[authorId] = authorDao.id
                it[packageId] = pkgDao.id
            }
        }

        pkg.declaredLicenses.forEach { declaredLicense ->
            val declaredLicenseDao = DeclaredLicenseDao.getOrPut(declaredLicense)
            PackagesDeclaredLicensesTable.insert {
                it[declaredLicenseId] = declaredLicenseDao.id
                it[packageId] = pkgDao.id
            }
        }

        createProcessedDeclaredLicense(pkg.processedDeclaredLicense, pkgDao = pkgDao)

        analyzerRun?.let { runDao ->
            PackagesAnalyzerRunsTable.insert {
                it[packageId] = pkgDao.id
                it[analyzerRunId] = runDao.id
            }
        }

        return pkgDao
    }

    private fun createProject(project: Project, analyzerRun: AnalyzerRunDao?): ProjectDao {
        val identifier = IdentifierDao.getOrPut(project.identifier)
        val vcs = VcsInfoDao.getOrPut(project.vcs)
        val vcsProcessed = VcsInfoDao.getOrPut(project.vcsProcessed)

        val projectDao = ProjectDao.new {
            this.identifier = identifier
            this.vcs = vcs
            this.vcsProcessed = vcsProcessed

            this.cpe = project.cpe
            this.homepageUrl = project.homepageUrl
            this.definitionFilePath = project.definitionFilePath
        }

        project.authors.forEach { author ->
            val authorDao = AuthorDao.getOrPut(author)
            ProjectsAuthorsTable.insert {
                it[authorId] = authorDao.id
                it[projectId] = projectDao.id
            }
        }

        project.declaredLicenses.forEach { declaredLicense ->
            val declaredLicenseDao = DeclaredLicenseDao.getOrPut(declaredLicense)
            ProjectsDeclaredLicensesTable.insert {
                it[declaredLicenseId] = declaredLicenseDao.id
                it[projectId] = projectDao.id
            }
        }

        project.scopeNames.forEach { scopeName ->
            ProjectScopeDao.new {
                this.project = projectDao
                this.name = scopeName
            }
        }

        createProcessedDeclaredLicense(
            project.processedDeclaredLicense,
            projectDao = projectDao
        )

        analyzerRun?.let { runDao ->
            ProjectsAnalyzerRunsTable.insert {
                it[projectId] = projectDao.id
                it[analyzerRunId] = runDao.id
            }
        }

        return projectDao
    }

    private fun createProcessedDeclaredLicense(
        processedDeclaredLicense: ProcessedDeclaredLicense,
        pkgDao: PackageDao? = null,
        projectDao: ProjectDao? = null
    ) {
        require(listOfNotNull(pkgDao, projectDao).size == 1) {
            "Exactly one of 'pkgDao' and 'projectDao' must be not null."
        }

        val processedDeclaredLicenseDao = ProcessedDeclaredLicenseDao.new {
            pkgDao?.also { this.pkg = pkgDao }
            projectDao?.also { this.project = projectDao }
            spdxExpression = processedDeclaredLicense.spdxExpression
        }

        processedDeclaredLicense.mappedLicenses.forEach { (declaredLicense, mappedLicense) ->
            val mappedDeclaredLicenseDao = MappedDeclaredLicenseDao.getOrPut(declaredLicense, mappedLicense)

            ProcessedDeclaredLicensesMappedDeclaredLicensesTable.insert {
                it[processedDeclaredLicenseId] = processedDeclaredLicenseDao.id
                it[mappedDeclaredLicenseId] = mappedDeclaredLicenseDao.id
            }
        }

        processedDeclaredLicense.unmappedLicenses.forEach { unmappedLicense ->
            val unmappedDeclaredLicenseDao = UnmappedDeclaredLicenseDao.getOrPut(unmappedLicense)

            ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.insert {
                it[processedDeclaredLicenseId] = processedDeclaredLicenseDao.id
                it[unmappedDeclaredLicenseId] = unmappedDeclaredLicenseDao.id
            }
        }
    }

    private suspend fun createAnalyzerRun(ortRun: OrtRun): AnalyzerRunDao = extension.db.dbQuery {
        val analyzerJob = extension.fixtures.createAnalyzerJob(ortRunId = ortRun.id)
        val environmentDao = EnvironmentDao.getOrPut(environment)

        val runDao = AnalyzerRunDao.new {
            this.analyzerJob = AnalyzerJobDao[analyzerJob.id]
            this.startTime = Clock.System.now().minus(10.minutes)
            this.endTime = Clock.System.now()
            this.environment = environmentDao
            this.dependencyGraphsWrapper = DependencyGraphsWrapper(emptyMap())
        }

        AnalyzerConfigurationDao.new {
            this.analyzerRun = runDao
            this.allowDynamicVersions = false
            this.skipExcluded = false
        }

        runDao
    }
}

private val vcsInfo = VcsInfo(
    type = RepositoryType.GIT,
    url = "https://example.com/vcs",
    revision = "test",
    path = ""
)

private val environment = Environment(
    ortVersion = "26.7.5",
    javaVersion = "17",
    os = "testOS",
    processors = 2,
    maxMemory = 16384,
    variables = emptyMap(),
    toolVersions = emptyMap()
)

private val baseIdentifier = Identifier("test", "ns", "name", "1.0")

private val baseProcessedDeclaredLicense = ProcessedDeclaredLicense(
    spdxExpression = "MIT",
    mappedLicenses = mapOf("BSD" to "BSD-3-Clause"),
    unmappedLicenses = setOf("Strange License")
)

private val basePackage = Package(
    identifier = baseIdentifier,
    purl = "pkg:test/name@1.0",
    cpe = "cpe:2.3:a:test:ns:name:1.0",
    authors = setOf("author1", "author2"),
    declaredLicenses = setOf("ASL", "BSL", "CSL"),
    processedDeclaredLicense = baseProcessedDeclaredLicense,
    description = "Some test package",
    homepageUrl = "https://example.com",
    binaryArtifact = RemoteArtifact(
        url = "https://example.com/binary",
        hashValue = "binaryHash",
        hashAlgorithm = "SHA-1"
    ),
    sourceArtifact = RemoteArtifact(
        url = "https://example.com/source",
        hashValue = "sourceHash",
        hashAlgorithm = "SHA-256"
    ),
    vcs = vcsInfo,
    vcsProcessed = vcsInfo
)

private val baseProject = Project(
    identifier = baseIdentifier,
    cpe = "cpe:2.3:a:testProject:ns:name:1.0",
    definitionFilePath = "some-definition.file",
    authors = setOf("projectAuthor1", "projectAuthor2"),
    declaredLicenses = setOf("XSL", "YSL", "ZSL"),
    processedDeclaredLicense = baseProcessedDeclaredLicense,
    vcs = vcsInfo,
    vcsProcessed = vcsInfo,
    description = "",
    homepageUrl = "https://example.com/project",
    scopeNames = setOf("compile", "test")
)
