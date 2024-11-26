/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

@file:Suppress("TooManyFunctions")

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicenseDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunIssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraph
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphsWrapper
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

/**
 * An implementation of [AnalyzerRunRepository] that stores analyzer runs in [AnalyzerRunsTable].
 */
class DaoAnalyzerRunRepository(private val db: Database) : AnalyzerRunRepository {
    override fun create(
        analyzerJobId: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: AnalyzerConfiguration,
        projects: Set<Project>,
        packages: Set<Package>,
        issues: List<Issue>,
        dependencyGraphs: Map<String, DependencyGraph>
    ): AnalyzerRun {
        return db.blockingQuery {
            val jobDao = AnalyzerJobDao[analyzerJobId]
            val environmentDao = EnvironmentDao.getOrPut(environment)

            val analyzerRun = AnalyzerRunDao.new {
                this.analyzerJob = jobDao
                this.startTime = startTime
                this.endTime = endTime
                this.environment = environmentDao
                this.dependencyGraphsWrapper = DependencyGraphsWrapper(dependencyGraphs)
            }

            createAnalyzerConfiguration(analyzerRun, config)

            projects.forEach { createProject(analyzerRun, it) }
            packages.forEach { createPackage(analyzerRun, it) }

            issues.forEach {
                val analyzerIssue = it.copy(worker = AnalyzerRunDao.ISSUE_WORKER_TYPE)
                OrtRunIssueDao.createByIssue(jobDao.ortRun.id.value, analyzerIssue)
            }

            analyzerRun.mapToModel()
        }
    }

    override fun get(id: Long): AnalyzerRun? = db.entityQuery { AnalyzerRunDao[id].mapToModel() }

    override fun getByJobId(analyzerJobId: Long): AnalyzerRun? = db.blockingQuery {
        AnalyzerRunDao.find { AnalyzerRunsTable.analyzerJobId eq analyzerJobId }.firstOrNull()?.mapToModel()
    }
}

private fun createAnalyzerConfiguration(
    analyzerRun: AnalyzerRunDao,
    analyzerConfiguration: AnalyzerConfiguration
): AnalyzerConfigurationDao {
    val packageManagerConfigurations =
        mapAndDeduplicate(
            analyzerConfiguration.packageManagers?.entries
        ) { (packageManager, packageManagerConfiguration) ->
            val packageManagerConfigurationDao = PackageManagerConfigurationDao.new {
                name = packageManager
                mustRunAfter = packageManagerConfiguration.mustRunAfter
                hasOptions = (packageManagerConfiguration.options != null)
            }

            packageManagerConfiguration.options?.forEach { (name, value) ->
                PackageManagerConfigurationOptionDao.new {
                    this.packageManagerConfiguration = packageManagerConfigurationDao
                    this.name = name
                    this.value = value
                }
            }

            packageManagerConfigurationDao
        }

    val analyzerConfigurationDao = AnalyzerConfigurationDao.new {
        this.analyzerRun = analyzerRun
        this.packageManagerConfigurations = packageManagerConfigurations
        allowDynamicVersions = analyzerConfiguration.allowDynamicVersions
        enabledPackageManagers = analyzerConfiguration.enabledPackageManagers
        disabledPackageManagers = analyzerConfiguration.disabledPackageManagers
        skipExcluded = analyzerConfiguration.skipExcluded
    }

    return analyzerConfigurationDao
}

private fun createProject(analyzerRun: AnalyzerRunDao, project: Project): ProjectDao {
    val identifier = IdentifierDao.getOrPut(project.identifier)

    val vcs = VcsInfoDao.getOrPut(project.vcs)
    val vcsProcessed = VcsInfoDao.getOrPut(project.vcsProcessed)

    val projectDao = ProjectDao.findByProject(project) ?: insertProject(
        identifier,
        vcs,
        vcsProcessed,
        project
    )

    ProjectsAnalyzerRunsTable.insert {
        it[analyzerRunId] = analyzerRun.id
        it[projectId] = projectDao.id
    }

    return projectDao
}

/**
 * Create a new [ProjectDao] entity in the database with the given values.
 */
private fun insertProject(
    identifier: IdentifierDao,
    vcs: VcsInfoDao,
    vcsProcessed: VcsInfoDao,
    project: Project
): ProjectDao {
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

    createProcessedDeclaredLicense(project.processedDeclaredLicense, projectDao = projectDao)

    return projectDao
}

private fun createPackage(analyzerRun: AnalyzerRunDao, pkg: Package): PackageDao {
    val identifier = IdentifierDao.getOrPut(pkg.identifier)

    val vcs = VcsInfoDao.getOrPut(pkg.vcs)
    val vcsProcessed = VcsInfoDao.getOrPut(pkg.vcsProcessed)

    val binaryArtifact = RemoteArtifactDao.getOrPut(pkg.binaryArtifact)
    val sourceArtifact = RemoteArtifactDao.getOrPut(pkg.sourceArtifact)

    val pkgDao = PackageDao.findByPackage(pkg) ?: insertPackage(
        identifier,
        vcs,
        vcsProcessed,
        binaryArtifact,
        sourceArtifact,
        pkg
    )

    PackagesAnalyzerRunsTable.insert {
        it[analyzerRunId] = analyzerRun.id
        it[packageId] = pkgDao.id
    }

    return pkgDao
}

/**
 * Create a new [PackageDao] entity in the database with the given values.
 */
private fun insertPackage(
    identifier: IdentifierDao,
    vcs: VcsInfoDao,
    vcsProcessed: VcsInfoDao,
    binaryArtifact: RemoteArtifactDao,
    sourceArtifact: RemoteArtifactDao,
    pkg: Package
): PackageDao {
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

    return pkgDao
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
