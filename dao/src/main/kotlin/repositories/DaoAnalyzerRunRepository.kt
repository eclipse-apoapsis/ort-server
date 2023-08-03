/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.repositories

import kotlinx.datetime.Instant

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.AnalyzerJobDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AnalyzerConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AnalyzerRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AnalyzerRunsIdentifiersOrtIssuesTable
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AnalyzerRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AuthorDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackageDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackageManagerConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackageManagerConfigurationOptionDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackagesAnalyzerRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackagesAuthorsTable
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackagesDeclaredLicensesTable
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.ProjectDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.ProjectScopeDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.ProjectsAuthorsTable
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.ProjectsDeclaredLicensesTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.DeclaredLicenseDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierOrtIssueDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.OrtIssueDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.DependencyGraph
import org.ossreviewtoolkit.server.model.runs.DependencyGraphsWrapper
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.runs.Package
import org.ossreviewtoolkit.server.model.runs.Project

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
        issues: Map<Identifier, List<OrtIssue>>,
        dependencyGraphs: Map<String, DependencyGraph>
    ): AnalyzerRun = db.blockingQuery {
        val environmentDao = EnvironmentDao.getOrPut(environment)

        val analyzerRun = AnalyzerRunDao.new {
            this.analyzerJob = AnalyzerJobDao[analyzerJobId]
            this.startTime = startTime
            this.endTime = endTime
            this.environment = environmentDao
            this.dependencyGraphsWrapper = DependencyGraphsWrapper(dependencyGraphs)
        }

        createAnalyzerConfiguration(analyzerRun, config)

        projects.forEach { createProject(analyzerRun, it) }
        packages.forEach { createPackage(analyzerRun, it) }

        issues.forEach { (id, issues) ->
            val identifier = IdentifierDao.getOrPut(id)
            issues.forEach { createIssue(analyzerRun, identifier, it) }
        }

        analyzerRun.mapToModel()
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
        analyzerConfiguration.packageManagers?.map { (packageManager, packageManagerConfiguration) ->
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
        }.orEmpty()

    val analyzerConfigurationDao = AnalyzerConfigurationDao.new {
        this.analyzerRun = analyzerRun
        this.packageManagerConfigurations = SizedCollection(packageManagerConfigurations)
        allowDynamicVersions = analyzerConfiguration.allowDynamicVersions
        enabledPackageManagers = analyzerConfiguration.enabledPackageManagers
        disabledPackageManagers = analyzerConfiguration.disabledPackageManagers
    }

    return analyzerConfigurationDao
}

private fun createProject(analyzerRun: AnalyzerRunDao, project: Project): ProjectDao {
    val identifier = IdentifierDao.getOrPut(project.identifier)

    val vcs = VcsInfoDao.getOrPut(project.vcs)
    val vcsProcessed = VcsInfoDao.getOrPut(project.vcsProcessed)

    val projectDao = ProjectDao.findByProject(project) ?: ProjectDao.new {
        this.analyzerRun = analyzerRun
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

    return projectDao
}

private fun createPackage(analyzerRun: AnalyzerRunDao, pkg: Package): PackageDao {
    val identifier = IdentifierDao.getOrPut(pkg.identifier)

    val vcs = VcsInfoDao.getOrPut(pkg.vcs)
    val vcsProcessed = VcsInfoDao.getOrPut(pkg.vcsProcessed)

    val binaryArtifact = RemoteArtifactDao.getOrPut(pkg.binaryArtifact)
    val sourceArtifact = RemoteArtifactDao.getOrPut(pkg.sourceArtifact)

    val pkgDao = PackageDao.findByPackage(pkg) ?: PackageDao.new {
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

    PackagesAnalyzerRunsTable.insert {
        it[analyzerRunId] = analyzerRun.id
        it[packageId] = pkgDao.id
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

    return pkgDao
}

private fun createIssue(analyzerRun: AnalyzerRunDao, identifier: IdentifierDao, issue: OrtIssue): OrtIssueDao {
    val issueDao = OrtIssueDao.getOrPut(issue)

    val identifiersOrtIssueDao = IdentifierOrtIssueDao.getOrPut(identifier, issueDao)

    AnalyzerRunsIdentifiersOrtIssuesTable.insert {
        it[analyzerRunId] = analyzerRun.id
        it[identifierOrtIssueId] = identifiersOrtIssueDao.id
    }

    return issueDao
}
