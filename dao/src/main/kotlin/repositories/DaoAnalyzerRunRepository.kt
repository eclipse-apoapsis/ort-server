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

import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.server.dao.blockingQuery
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
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierOrtIssueDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.OrtIssueDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.utils.getOrPutEnvironment
import org.ossreviewtoolkit.server.dao.utils.getOrPutIdentifier
import org.ossreviewtoolkit.server.dao.utils.getOrPutIssue
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
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo

/**
 * An implementation of [AnalyzerRunRepository] that stores analyzer runs in [AnalyzerRunsTable].
 */
class DaoAnalyzerRunRepository : AnalyzerRunRepository {
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
    ): AnalyzerRun = blockingQuery {
        val environmentDao = getOrPutEnvironment(environment)

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
            val identifier = getOrPutIdentifier(id)
            issues.forEach { createIssue(analyzerRun, identifier, it) }
        }

        analyzerRun.mapToModel()
    }.getOrThrow()

    override fun get(id: Long): AnalyzerRun? = blockingQuery { AnalyzerRunDao[id].mapToModel() }.getOrNull()
}

private fun createAnalyzerConfiguration(
    analyzerRun: AnalyzerRunDao,
    analyzerConfiguration: AnalyzerConfiguration
): AnalyzerConfigurationDao {
    val analyzerConfigurationDao = AnalyzerConfigurationDao.new {
        this.analyzerRun = analyzerRun
        allowDynamicVersions = analyzerConfiguration.allowDynamicVersions
        enabledPackageManagers = analyzerConfiguration.enabledPackageManagers
        disabledPackageManagers = analyzerConfiguration.disabledPackageManagers
    }

    analyzerConfiguration.packageManagers?.forEach { (packageManager, packageManagerConfiguration) ->
        val packageManagerConfigurationDao = PackageManagerConfigurationDao.new {
            this.analyzerConfiguration = analyzerConfigurationDao
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
    }

    return analyzerConfigurationDao
}

private fun createProject(analyzerRun: AnalyzerRunDao, project: Project): ProjectDao {
    val identifier = getOrPutIdentifier(project.identifier)

    val vcs = getOrPutVcsInfo(project.vcs)
    val vcsProcessed = getOrPutVcsInfo(project.vcsProcessed)

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
        val authorDao = getOrPutAuthor(author)
        ProjectsAuthorsTable.insert {
            it[authorId] = authorDao.id
            it[projectId] = projectDao.id
        }
    }

    project.declaredLicenses.forEach { declaredLicense ->
        val declaredLicenseDao = getOrPutDeclaredLicense(declaredLicense)
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
    val identifier = getOrPutIdentifier(pkg.identifier)

    val vcs = getOrPutVcsInfo(pkg.vcs)
    val vcsProcessed = getOrPutVcsInfo(pkg.vcsProcessed)

    val binaryArtifact = getOrPutArtifact(pkg.binaryArtifact)
    val sourceArtifact = getOrPutArtifact(pkg.sourceArtifact)

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
        val authorDao = getOrPutAuthor(author)
        PackagesAuthorsTable.insert {
            it[authorId] = authorDao.id
            it[packageId] = pkgDao.id
        }
    }

    pkg.declaredLicenses.forEach { declaredLicense ->
        val declaredLicenseDao = getOrPutDeclaredLicense(declaredLicense)
        PackagesDeclaredLicensesTable.insert {
            it[declaredLicenseId] = declaredLicenseDao.id
            it[packageId] = pkgDao.id
        }
    }

    return pkgDao
}

private fun createIssue(analyzerRun: AnalyzerRunDao, identifier: IdentifierDao, issue: OrtIssue): OrtIssueDao {
    val issueDao = getOrPutIssue(issue)

    val identifiersOrtIssueDao = getOrPutIdentifierOrtIssue(identifier, issueDao)

    AnalyzerRunsIdentifiersOrtIssuesTable.insert {
        it[analyzerRunId] = analyzerRun.id
        it[identifierOrtIssueId] = identifiersOrtIssueDao.id
    }

    return issueDao
}

private fun getOrPutArtifact(artifact: RemoteArtifact): RemoteArtifactDao =
    RemoteArtifactDao.findByRemoteArtifact(artifact) ?: RemoteArtifactDao.new {
        url = artifact.url
        hashValue = artifact.hashValue
        hashAlgorithm = artifact.hashAlgorithm
    }

private fun getOrPutAuthor(author: String): AuthorDao =
    AuthorDao.findByName(author) ?: AuthorDao.new {
        name = author
    }

private fun getOrPutDeclaredLicense(declaredLicense: String): DeclaredLicenseDao =
    DeclaredLicenseDao.findByName(declaredLicense) ?: DeclaredLicenseDao.new {
        name = declaredLicense
    }

private fun getOrPutIdentifierOrtIssue(identifier: IdentifierDao, issue: OrtIssueDao): IdentifierOrtIssueDao =
    IdentifierOrtIssueDao.findByIdentifierAndIssue(identifier, issue) ?: IdentifierOrtIssueDao.new {
        this.identifier = identifier
        this.ortIssueDao = issue
    }

private fun getOrPutVcsInfo(vcsInfo: VcsInfo): VcsInfoDao =
    VcsInfoDao.findByVcsInfo(vcsInfo) ?: VcsInfoDao.new {
        type = vcsInfo.type
        url = vcsInfo.url
        revision = vcsInfo.revision
        path = vcsInfo.path
    }
