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

import java.security.MessageDigest

import kotlin.random.Random

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsIdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsTable
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
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.RuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageConfigurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataAuthors
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageLicenseChoicesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.VcsInfoCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.VcsMatchersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScannersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.AdditionalScanResultData
import org.eclipse.apoapsis.ortserver.dao.tables.CopyrightFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.LicenseFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsSnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphsWrapper

import org.jetbrains.exposed.sql.insert

internal object OrphanRemovalServiceTestFixtures {
    @Suppress("LongParameterList")
    internal fun createOrtRunTableEntry(
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

    internal fun createVcsInfoTableEntry(
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

    internal fun createRepositoryTableEntry(
        type: String = "GIT",
        url: String = "http://some.%d.url".format(Random.nextInt(0, 10000)),
        productId: Long = createProductTableEntry().value
    ) = RepositoriesTable.insert {
        it[this.type] = type
        it[this.url] = url
        it[this.productId] = productId
    } get RepositoriesTable.id

    internal fun createProductTableEntry(
        name: String = "Prodct_" + Random.nextInt(0, 10000),
        organizationId: Long = createOrganizationsTableEntry().value
    ) = ProductsTable.insert {
        it[this.name] = name
        it[this.organizationId] = organizationId
    } get ProductsTable.id

    internal fun createOrganizationsTableEntry(
        name: String = "Org_" + Random.nextInt(0, 10000)
    ) = OrganizationsTable.insert {
        it[this.name] = name
    } get OrganizationsTable.id

    internal fun createProjectsTableEntry(
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

    internal fun createAuthorsTableEntry(
        name: String = "author_" + Random.nextInt(0, 10000)
    ) = AuthorsTable.insert {
        it[this.name] = name
    } get AuthorsTable.id

    internal fun createProjectsAuthorsTableEntry(
        projectId: Long = createProjectsTableEntry().value,
        authorId: Long = createAuthorsTableEntry().value
    ) = ProjectsAuthorsTable.insert {
        it[this.projectId] = projectId
        it[this.authorId] = authorId
    }

    internal fun createPackagesAuthorsTableEntry(
        authorId: Long = createAuthorsTableEntry().value,
        packageId: Long = createPackagesTableEntry().value
    ) = PackagesAuthorsTable.insert {
        it[this.authorId] = authorId
        it[this.packageId] = packageId
    }

    internal fun createPackageCurationDataAuthorsTableEntry(
        authorId: Long = createAuthorsTableEntry().value,
        packageCurationDataId: Long = createPackageCurationDataTableEntry().value
    ) = PackageCurationDataAuthors.insert {
        it[this.authorId] = authorId
        it[this.packageCurationDataId] = packageCurationDataId
    }

    internal fun createPackageCurationDataTableEntry(
        binaryArtifactId: Long = createRemoteArtifactsTableEntry().value,
        sourceArtifactId: Long = createRemoteArtifactsTableEntry().value
    ) = PackageCurationDataTable.insert {
        it[this.binaryArtifactId] = binaryArtifactId
        it[this.sourceArtifactId] = sourceArtifactId
    } get PackageCurationDataTable.id

    internal fun createProjectsAnalyzerRunsTableEntry(
        projectId: Long = createProjectsTableEntry().value,
        analyzerRunId: Long = createAnalyzerRunTableEntry().value
    ) = ProjectsAnalyzerRunsTable.insert {
        it[this.projectId] = projectId
        it[this.analyzerRunId] = analyzerRunId
    }

    internal fun createIdentifierTableEntry(
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

    internal fun createIdentifiersIssuesTableEntry(
        identifierId: Long = createIdentifierTableEntry().value,
        issueId: Long = createIssuesTableEntry().value
    ) = IdentifiersIssuesTable.insert {
        it[this.identifierId] = identifierId
        it[this.issueId] = issueId
    } get IdentifiersIssuesTable.id

    internal fun createIssuesTableEntry(
        issueSource: String = "src_" + Random.nextInt(0, 10000),
        message: String = "msg_" + Random.nextInt(0, 10000),
        severity: Severity = Severity.ERROR,
        affectedPath: String = "path/" + Random.nextInt(0, 10000)
    ) = IssuesTable.insert {
        it[this.issueSource] = issueSource
        it[this.message] = message
        it[this.severity] = severity
        it[this.affectedPath] = affectedPath
    } get IssuesTable.id

    internal fun createAdvisorRunsIdentifiersTableEntry(
        advisorRunId: Long = createAdvisorRunsTableEntry().value,
        identifierId: Long = createIdentifierTableEntry().value,
    ) = AdvisorRunsIdentifiersTable.insert {
        it[this.advisorRunId] = advisorRunId
        it[this.identifierId] = identifierId
    }

    internal fun createAdvisorRunsTableEntry(
        advisorJobId: Long = createAdvisorJobsTableEntry().value,
        environmentId: Long = createEnvironmentTableEntry().value,
        startTime: Instant = Clock.System.now(),
        endTime: Instant = Clock.System.now()
    ) = AdvisorRunsTable.insert {
        it[this.advisorJobId] = advisorJobId
        it[this.environmentId] = environmentId
        it[this.startTime] = startTime
        it[this.endTime] = endTime
    } get AdvisorRunsTable.id

    internal fun createAdvisorJobsTableEntry(
        ortRunId: Long = createOrtRunTableEntry().value,
        createdAt: Instant = Clock.System.now(),
        configuration: AdvisorJobConfiguration = AdvisorJobConfiguration(),
        status: JobStatus = JobStatus.FINISHED
    ) = AdvisorJobsTable.insert {
        it[this.ortRunId] = ortRunId
        it[this.createdAt] = createdAt
        it[this.configuration] = configuration
        it[this.status] = status
    } get AdvisorJobsTable.id

    internal fun createPackageProvenancesTableEntry(
        identifierId: Long = createIdentifierTableEntry().value,
        artifactId: Long = createRemoteArtifactsTableEntry().value,
        vcsId: Long = createVcsInfoTableEntry().value
    ) = PackageProvenancesTable.insert {
        it[this.identifierId] = identifierId
        it[this.artifactId] = artifactId
        it[this.vcsId] = vcsId
    } get PackageProvenancesTable.id

    internal fun createRuleViolationsTableEntry(
        rule: String = "rule_" + Random.nextInt(0, 10000),
        packageIdentifierId: Long = createIdentifierTableEntry().value,
        severity: Severity = Severity.WARNING,
        message: String = "msg_" + Random.nextInt(0, 10000),
        howToFix: String = "howhow_" + Random.nextInt(0, 10000)
    ) = RuleViolationsTable.insert {
        it[this.rule] = rule
        it[this.packageIdentifierId] = packageIdentifierId
        it[this.severity] = severity
        it[this.message] = message
        it[this.howToFix] = howToFix
    } get RuleViolationsTable.id

    internal fun createPackageCurationsTableEntry(
        identifierId: Long = createIdentifierTableEntry().value,
        packageCurationDataId: Long = createPackageCurationDataTableEntry().value
    ) = PackageCurationsTable.insert {
        it[this.identifierId] = identifierId
        it[this.packageCurationDataId] = packageCurationDataId
    } get PackageCurationsTable.id

    internal fun createPackageConfigurationsTableEntry(
        identifierId: Long = createIdentifierTableEntry().value,
        vcsMatcherId: Long = createVcsMatchersTableEntry().value
    ) = PackageConfigurationsTable.insert {
        it[this.identifierId] = identifierId
        it[this.vcsMatcherId] = vcsMatcherId
    } get PackageConfigurationsTable.id

    internal fun createVcsMatchersTableEntry(
        type: String = "type_" + Random.nextInt(0, 10000),
        url: String = "http://homepage.%d.url".format(Random.nextInt(0, 10000)),
        revision: String = "rev_" + Random.nextInt(0, 10000)
    ) = VcsMatchersTable.insert {
        it[this.type] = type
        it[this.url] = url
        it[this.revision] = revision
    } get VcsMatchersTable.id

    internal fun createPackageLicenseChoicesTableEntry(
        identifierId: Long = createIdentifierTableEntry().value
    ) = PackageLicenseChoicesTable.insert {
        it[this.identifierId] = identifierId
    } get PackageLicenseChoicesTable.id

    internal fun createScannerRunsScannersTableEntry(
        scannerRunId: Long = createScannerRunsTableEntry().value,
        identifierId: Long = createIdentifierTableEntry().value,
        scannerName: String = "name_" + Random.nextInt(0, 10000)
    ) = ScannerRunsScannersTable.insert {
        it[this.scannerRunId] = scannerRunId
        it[this.identifierId] = identifierId
        it[this.scannerName] = scannerName
    } get ScannerRunsScannersTable.id

    internal fun createScannerRunsTableEntry(
        scannerJobId: Long = createScannerJobsTableEntry().value,
        environmentId: Long = createEnvironmentTableEntry().value
    ) = ScannerRunsTable.insert {
        it[this.scannerJobId] = scannerJobId
        it[this.environmentId] = environmentId
    } get ScannerRunsTable.id

    internal fun createScannerJobsTableEntry(
        ortRunId: Long = createOrtRunTableEntry().value,
        createdAt: Instant = Clock.System.now(),
        configuration: ScannerJobConfiguration = ScannerJobConfiguration(),
        status: JobStatus = JobStatus.CREATED
    ) = ScannerJobsTable.insert {
        it[this.ortRunId] = ortRunId
        it[this.createdAt] = createdAt
        it[this.configuration] = configuration
        it[this.status] = status
    } get ScannerJobsTable.id

    internal fun createOrtRunsIssuesTableEntry(
        ortRunId: Long = createOrtRunTableEntry().value,
        issueId: Long = createIssuesTableEntry().value,
        identifierId: Long = createIdentifierTableEntry().value
    ) = OrtRunsIssuesTable.insert {
        it[this.ortRunId] = ortRunId
        it[this.issueId] = issueId
        it[this.identifierId] = identifierId
        it[this.timestamp] = Clock.System.now()
    } get OrtRunsIssuesTable.id

    @Suppress("LongParameterList")
    internal fun createPackagesTableEntry(
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

    internal fun createProcessedDeclaredLicensesTableEntry(
        packageId: Long = createPackagesTableEntry().value,
        projectId: Long = createProjectsTableEntry().value,
        spdxExpression: String = "spdx_expression_" + Random.nextInt(0, 10000)
    ) = ProcessedDeclaredLicensesTable.insert {
        it[this.packageId] = packageId
        it[this.projectId] = projectId
        it[this.spdxExpression] = spdxExpression
    } get ProcessedDeclaredLicensesTable.id

    internal fun createPackageAnalyzerRunsTableEntry(
        packageId: Long,
        analyzerRunId: Long
    ) = PackagesAnalyzerRunsTable.insert {
        it[this.packageId] = packageId
        it[this.analyzerRunId] = analyzerRunId
    }

    internal fun createAnalyzerJobTableEntry(
        ortRunId: Long = createOrtRunTableEntry().value,
        createdAt: Instant = Clock.System.now(),
        configuration: AnalyzerJobConfiguration = AnalyzerJobConfiguration(),
        status: JobStatus = JobStatus.CREATED
    ) = AnalyzerJobsTable.insert {
        it[this.ortRunId] = ortRunId
        it[this.createdAt] = createdAt
        it[this.configuration] = configuration
        it[this.status] = status
    } get AnalyzerJobsTable.id

    internal fun createAnalyzerRunTableEntry(
        analyzerJobId: Long = createAnalyzerJobTableEntry().value,
        environmentId: Long = createEnvironmentTableEntry().value,
        startTime: Instant = Clock.System.now(),
        endTime: Instant = Clock.System.now(),
        dependencyGraphs: DependencyGraphsWrapper = DependencyGraphsWrapper(emptyMap())
    ) = AnalyzerRunsTable.insert {
        it[this.analyzerJobId] = analyzerJobId
        it[this.environmentId] = environmentId
        it[this.startTime] = startTime
        it[this.endTime] = endTime
        it[this.dependencyGraphs] = dependencyGraphs
    } get AnalyzerRunsTable.id

    internal fun createEnvironmentTableEntry(
        ortVersion: String = "ver_" + Random.nextInt(0, 10000),
        javaVersion: String = "22",
        os: String = "Linux",
        processors: Int = 1,
        maxMemory: Long = Random.nextLong(100, 10000)
    ) = EnvironmentsTable.insert {
        it[this.ortVersion] = ortVersion
        it[this.javaVersion] = javaVersion
        it[this.os] = os
        it[this.processors] = processors
        it[this.maxMemory] = maxMemory
    } get EnvironmentsTable.id

    internal fun createNestedRepositoriesTableEntry(
        ortRunId: Long = createOrtRunTableEntry().value,
        path: String = "path/" + Random.nextInt(0, 10000),
        vcsId: Long = createVcsInfoTableEntry().value
    ) = NestedRepositoriesTable.insert {
        it[this.ortRunId] = ortRunId
        it[this.path] = path
        it[this.vcsId] = vcsId
    } get NestedRepositoriesTable.id

    @Suppress("LongParameterList")
    internal fun createSnippetsTableEntry(
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

    internal fun createRemoteArtifactsTableEntry(
        url: String = "git.someurl.org",
        hashValue: String = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).toString(),
        hashAlgorithm: String = "SHA1"
    ) = RemoteArtifactsTable.insert {
        it[this.url] = url
        it[this.hashValue] = hashValue
        it[this.hashAlgorithm] = hashAlgorithm
    } get RemoteArtifactsTable.id

    internal fun createDeclaredLicensesTableEntry(
        name: String = "name_" + Random.nextInt(0, 10000)
    ) = DeclaredLicensesTable.insert {
        it[this.name] = name
    } get DeclaredLicensesTable.id

    internal fun createPackagesDeclaredLicensesTableEntry(
        packageId: Long = createPackagesTableEntry().value,
        declaredLicenseId: Long = createDeclaredLicensesTableEntry().value
    ) = PackagesDeclaredLicensesTable.insert {
        it[this.packageId] = packageId
        it[this.declaredLicenseId] = declaredLicenseId
    }

    internal fun createProjectsDeclaredLicensesTableEntry(
        projectId: Long = createProjectsTableEntry().value,
        declaredLicenseId: Long = createDeclaredLicensesTableEntry().value
    ) = ProjectsDeclaredLicensesTable.insert {
        it[this.projectId] = projectId
        it[this.declaredLicenseId] = declaredLicenseId
    }

    internal fun createProcessedDeclaredLicensesMappedDeclaredLicensesTableEntry(
        mappedDeclaredLicenseId: Long = createMappedDeclaredLicenseTableEntry().value,
        processedDeclaredLicenseId: Long = createProcessedDeclaredLicensesTableEntry().value
    ) = ProcessedDeclaredLicensesMappedDeclaredLicensesTable.insert {
        it[this.mappedDeclaredLicenseId] = mappedDeclaredLicenseId
        it[this.processedDeclaredLicenseId] = processedDeclaredLicenseId
    }

    internal fun createProcessedDeclaredLicensesUnmappedDeclaredLicensesTableEntry(
        unmappedDeclaredLicenseId: Long = createUnmappedDeclaredLicenseTableEntry().value,
        processedDeclaredLicenseId: Long = createProcessedDeclaredLicensesTableEntry().value
    ) = ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.insert {
        it[this.unmappedDeclaredLicenseId] = unmappedDeclaredLicenseId
        it[this.processedDeclaredLicenseId] = processedDeclaredLicenseId
    }

    internal fun createMappedDeclaredLicenseTableEntry(
        declaredLicense: String = "license_" + Random.nextInt(0, 10000),
        mappedLicense: String = "license_" + Random.nextInt(0, 10000)
    ) = MappedDeclaredLicensesTable.insert {
        it[this.declaredLicense] = declaredLicense
        it[this.mappedLicense] = mappedLicense
    } get MappedDeclaredLicensesTable.id

    internal fun createUnmappedDeclaredLicenseTableEntry(
        unmappedLicense: String = "license_" + Random.nextInt(0, 10000)
    ) = UnmappedDeclaredLicensesTable.insert {
        it[this.unmappedLicense] = unmappedLicense
    } get UnmappedDeclaredLicensesTable.id

    internal fun createProjectScopesTableEntry(
        projectId: Long = createProjectsTableEntry().value,
        name: String = "name_" + Random.nextInt(0, 10000)
    ) = ProjectScopesTable.insert {
        it[this.projectId] = projectId
        it[this.name] = name
    } get ProjectScopesTable.id

    internal fun createScanSummaryTableEntry(
        startTime: Instant = Clock.System.now(),
        endTime: Instant = Clock.System.now(),
        hash: String = MessageDigest.getInstance("SHA-1").digest(startTime.toString().toByteArray()).toString()
    ) = ScanSummariesTable.insert {
        it[this.startTime] = startTime
        it[this.endTime] = endTime
        it[this.hash] = hash
    } get ScanSummariesTable.id

    @Suppress("LongParameterList")
    internal fun createScanResultsTableEntry(
        artifactUrl: String = "http://some.%d.url".format(Random.nextInt(0, 10000)),
        artifactHash: String = MessageDigest.getInstance("SHA-1").digest(artifactUrl.toByteArray()).toString(),
        artifactHashAlgorithm: String = "SHA-1",
        vcsType: String = "GIT",
        vcsUrl: String = "http://some.%d.url".format(Random.nextInt(0, 10000)),
        vcsRevision: String = "rev_" + Random.nextInt(0, 10000),
        scanSummaryId: Long = createScanSummaryTableEntry().value,
        scannerName: String = "scanner_" + Random.nextInt(0, 1000),
        scannerVersion: String = "v." + Random.nextFloat(),
        scannerConfiguration: String = "config_" + Random.nextInt(0, 10000),
        additionalScanResultData: AdditionalScanResultData = AdditionalScanResultData(emptyMap())
    ) = ScanResultsTable.insert {
        it[this.artifactUrl] = artifactUrl
        it[this.artifactHash] = artifactHash
        it[this.artifactHashAlgorithm] = artifactHashAlgorithm
        it[this.vcsType] = vcsType
        it[this.vcsUrl] = vcsUrl
        it[this.vcsRevision] = vcsRevision
        it[this.scanSummaryId] = scanSummaryId
        it[this.scannerName] = scannerName
        it[this.scannerVersion] = scannerVersion
        it[this.scannerConfiguration] = scannerConfiguration
        it[this.additionalScanResultData] = additionalScanResultData
    } get ScanResultsTable.id

    internal fun createCopyrightFindingsTableEntry(
        statement: String = "statement_" + Random.nextInt(0, 10000),
        path: String = "path_" + Random.nextInt(0, 10000),
        startLine: Int = Random.nextInt(0, 10000),
        endLine: Int = Random.nextInt(10001, 20000),
        scanSummaryId: Long = createScanSummaryTableEntry().value
    ) = CopyrightFindingsTable.insert {
        it[this.statement] = statement
        it[this.path] = path
        it[this.startLine] = startLine
        it[this.endLine] = endLine
        it[this.scanSummaryId] = scanSummaryId
    }

    @Suppress("LongParameterList")
    internal fun createLicenseFindingsTableEntry(
        license: String = "license_" + Random.nextInt(0, 10000),
        path: String = "path_" + Random.nextInt(0, 10000),
        startLine: Int = Random.nextInt(0, 10000),
        endLine: Int = Random.nextInt(10001, 20000),
        score: Float = Random.nextFloat(),
        scanSummaryId: Long = createScanSummaryTableEntry().value
    ) = LicenseFindingsTable.insert {
        it[this.license] = license
        it[this.path] = path
        it[this.startLine] = startLine
        it[this.endLine] = endLine
        it[this.score] = score
        it[this.scanSummaryId] = scanSummaryId
    } get LicenseFindingsTable.id

    internal fun createSnippetFindingsTableEntry(
        path: String = "path_" + Random.nextInt(0, 10000),
        startLine: Int = Random.nextInt(0, 10000),
        endLine: Int = Random.nextInt(10001, 20000),
        scanSummaryId: Long = createScanSummaryTableEntry().value
    ) = SnippetFindingsTable.insert {
        it[this.path] = path
        it[this.startLine] = startLine
        it[this.endLine] = endLine
        it[this.scanSummaryId] = scanSummaryId
    } get SnippetFindingsTable.id

    internal fun createSnippetFindingsSnippetsTableEntry(
        snippetFindingId: Long = createSnippetFindingsTableEntry().value,
        snippetId: Long = createSnippetsTableEntry().value
    ) = SnippetFindingsSnippetsTable.insert {
        it[this.snippetFindingId] = snippetFindingId
        it[this.snippetId] = snippetId
    }

    internal fun createScannerRunsScanResultsTableEntry(
        scannerRunId: Long = createScannerRunsTableEntry().value,
        scanResultId: Long = createScanResultsTableEntry().value
    ) = ScannerRunsScanResultsTable.insert {
        it[this.scannerRunId] = scannerRunId
        it[this.scanResultId] = scanResultId
    }
}
