/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.common

import kotlinx.datetime.toKotlinInstant

import org.ossreviewtoolkit.model.AdvisorResult as OrtAdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun as OrtAdvisorRun
import org.ossreviewtoolkit.model.AnalyzerRun as OrtAnalyzerRun
import org.ossreviewtoolkit.model.ArtifactProvenance as OrtArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding as OrtCopyrightFinding
import org.ossreviewtoolkit.model.Defect as OrtDefect
import org.ossreviewtoolkit.model.DependencyGraph as OrtDependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphEdge as OrtDependencyGraphEdge
import org.ossreviewtoolkit.model.DependencyGraphNode as OrtDependencyGraphNode
import org.ossreviewtoolkit.model.EvaluatorRun as OrtEvaluatorRun
import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Issue as OrtOrtIssue
import org.ossreviewtoolkit.model.LicenseFinding as OrtLicenseFinding
import org.ossreviewtoolkit.model.Package as OrtPackage
import org.ossreviewtoolkit.model.PackageCuration as OrtPackageCuration
import org.ossreviewtoolkit.model.PackageCurationData as OrtPackageCurationData
import org.ossreviewtoolkit.model.Project as OrtProject
import org.ossreviewtoolkit.model.Provenance as OrtProvenance
import org.ossreviewtoolkit.model.ProvenanceResolutionResult as OrtProvenanceResolutionResult
import org.ossreviewtoolkit.model.RemoteArtifact as OrtRemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance as OrtRepositoryProvenance
import org.ossreviewtoolkit.model.ResolvedPackageCurations as OrtResolvedPackageCurations
import org.ossreviewtoolkit.model.ResolvedPackageCurations.Provider as OrtPackageCurationProvider
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.model.RuleViolation as OrtRuleViolation
import org.ossreviewtoolkit.model.ScanResult as OrtScanResult
import org.ossreviewtoolkit.model.ScanSummary as OrtScanSummary
import org.ossreviewtoolkit.model.ScannerDetails as OrtScannerDetails
import org.ossreviewtoolkit.model.ScannerRun as OrtScannerRun
import org.ossreviewtoolkit.model.Snippet as OrtSnippet
import org.ossreviewtoolkit.model.SnippetFinding as OrtSnippetFinding
import org.ossreviewtoolkit.model.TextLocation as OrtTextLocation
import org.ossreviewtoolkit.model.UnknownProvenance as OrtUnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo as OrtVcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData as OrtVcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AdvisorConfiguration as OrtAdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration as OrtAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration as OrtClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.config.Curations as OrtCurations
import org.ossreviewtoolkit.model.config.Excludes as OrtExcludes
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration as OrtFileArchiverConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration as OrtFileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileStorageConfiguration as OrtFileStorageConfiguration
import org.ossreviewtoolkit.model.config.HttpFileStorageConfiguration as OrtHttpFileStorageConfiguration
import org.ossreviewtoolkit.model.config.IssueResolution as OrtIssueResolution
import org.ossreviewtoolkit.model.config.LicenseChoices as OrtLicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration as OrtLicenseFindingCuration
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration as OrtLocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.PackageConfiguration as OrtPackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice as OrtPackageLicenseChoice
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration as OrtPackageManagerConfiguration
import org.ossreviewtoolkit.model.config.PathExclude as OrtPathExclude
import org.ossreviewtoolkit.model.config.PluginConfiguration as OrtPluginConfiguration
import org.ossreviewtoolkit.model.config.PostgresConnection as OrtPostgresConnection
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration as OrtPostgresStorageConfiguration
import org.ossreviewtoolkit.model.config.ProvenanceStorageConfiguration as OrtProvenanceStorageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration as OrtRepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration as OrtRepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions as OrtResolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution as OrtRuleViolationResolution
import org.ossreviewtoolkit.model.config.ScannerConfiguration as OrtScannerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude as OrtScopeExclude
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration as OrtSw360StorageConfiguration
import org.ossreviewtoolkit.model.config.VcsMatcher as OrtVcsMatcher
import org.ossreviewtoolkit.model.config.VulnerabilityResolution as OrtVulnerabilityResolution
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability as OrtVulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference as OrtVulnerabilityReference
import org.ossreviewtoolkit.server.model.PluginConfiguration
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.resolvedconfiguration.PackageCurationProviderConfig
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedPackageCurations
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.DependencyGraph
import org.ossreviewtoolkit.server.model.runs.DependencyGraphEdge
import org.ossreviewtoolkit.server.model.runs.DependencyGraphNode
import org.ossreviewtoolkit.server.model.runs.DependencyGraphRoot
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.EvaluatorRun
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.runs.OrtRuleViolation as RuleViolation
import org.ossreviewtoolkit.server.model.runs.Package
import org.ossreviewtoolkit.server.model.runs.PackageManagerConfiguration
import org.ossreviewtoolkit.server.model.runs.ProcessedDeclaredLicense
import org.ossreviewtoolkit.server.model.runs.Project
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorResult
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.advisor.Defect
import org.ossreviewtoolkit.server.model.runs.advisor.Vulnerability
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerabilityReference
import org.ossreviewtoolkit.server.model.runs.repository.Curations
import org.ossreviewtoolkit.server.model.runs.repository.Excludes
import org.ossreviewtoolkit.server.model.runs.repository.IssueResolution
import org.ossreviewtoolkit.server.model.runs.repository.LicenseChoices
import org.ossreviewtoolkit.server.model.runs.repository.LicenseFindingCuration
import org.ossreviewtoolkit.server.model.runs.repository.PackageConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCuration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCurationData
import org.ossreviewtoolkit.server.model.runs.repository.PackageLicenseChoice
import org.ossreviewtoolkit.server.model.runs.repository.PathExclude
import org.ossreviewtoolkit.server.model.runs.repository.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.RepositoryConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.Resolutions
import org.ossreviewtoolkit.server.model.runs.repository.RuleViolationResolution
import org.ossreviewtoolkit.server.model.runs.repository.ScopeExclude
import org.ossreviewtoolkit.server.model.runs.repository.SpdxLicenseChoice
import org.ossreviewtoolkit.server.model.runs.repository.VcsInfoCurationData
import org.ossreviewtoolkit.server.model.runs.repository.VcsMatcher
import org.ossreviewtoolkit.server.model.runs.repository.VulnerabilityResolution
import org.ossreviewtoolkit.server.model.runs.scanner.ArtifactProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.CopyrightFinding
import org.ossreviewtoolkit.server.model.runs.scanner.FileArchiveConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileBasedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.HttpFileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.KnownProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.LicenseFinding
import org.ossreviewtoolkit.server.model.runs.scanner.LocalFileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.PostgresConnection
import org.ossreviewtoolkit.server.model.runs.scanner.PostgresStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceResolutionResult
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.RepositoryProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.ScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.ScanSummary
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerDetail
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.server.model.runs.scanner.Snippet
import org.ossreviewtoolkit.server.model.runs.scanner.SnippetFinding
import org.ossreviewtoolkit.server.model.runs.scanner.Sw360StorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.TextLocation
import org.ossreviewtoolkit.server.model.runs.scanner.UnknownProvenance
import org.ossreviewtoolkit.utils.ort.Environment as OrtEnvironment
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense as OrtProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice as OrtSpdxLicenseChoice

fun OrtAdvisorConfiguration.mapToModel() =
    AdvisorConfiguration(
        config = config?.mapValues { it.value.mapToModel() }.orEmpty()
    )

fun OrtAdvisorResult.mapToModel() =
    AdvisorResult(
        advisorName = advisor.name,
        capabilities = advisor.capabilities.map { it.name },
        startTime = summary.startTime.toKotlinInstant(),
        endTime = summary.endTime.toKotlinInstant(),
        issues = summary.issues.map { it.mapToModel() },
        defects = defects.map { it.mapToModel() },
        vulnerabilities = vulnerabilities.map { it.mapToModel() }
    )

fun OrtAdvisorRun.mapToModel(advisorJobId: Long) =
    AdvisorRun(
        id = -1,
        advisorJobId = advisorJobId,
        startTime = startTime.toKotlinInstant(),
        endTime = endTime.toKotlinInstant(),
        environment = environment.mapToModel(),
        config = config.mapToModel(),
        advisorRecords = results.advisorResults.mapKeys { it.key.mapToModel() }
            .mapValues { it.value.map { it.mapToModel() } }
    )

fun OrtAnalyzerConfiguration.mapToModel() =
    AnalyzerConfiguration(
        allowDynamicVersions = allowDynamicVersions,
        enabledPackageManagers = enabledPackageManagers,
        disabledPackageManagers = disabledPackageManagers,
        packageManagers = packageManagers?.mapValues { it.value.mapToModel() }
    )

fun OrtAnalyzerRun.mapToModel(analyzerJobId: Long) =
    AnalyzerRun(
        id = -1,
        analyzerJobId = analyzerJobId,
        startTime = startTime.toKotlinInstant(),
        endTime = endTime.toKotlinInstant(),
        environment = environment.mapToModel(),
        config = config.mapToModel(),
        projects = result.projects.mapTo(mutableSetOf()) { it.mapToModel() },
        packages = result.packages.mapTo(mutableSetOf()) { it.mapToModel() },
        issues = result.issues.mapKeys { it.key.mapToModel() }.mapValues { it.value.map { it.mapToModel() } },
        dependencyGraphs = result.dependencyGraphs.mapValues { it.value.mapToModel() }
    )

fun OrtCopyrightFinding.mapToModel() = CopyrightFinding(statement = statement, location = location.mapToModel())

fun OrtSnippetFinding.mapToModel() = SnippetFinding(
    location = sourceLocation.mapToModel(),
    snippets = snippets.mapTo(mutableSetOf(), OrtSnippet::mapToModel)
)

fun OrtSnippet.mapToModel() = Snippet(
    purl = purl,
    provenance = provenance.mapToModel(),
    location = location.mapToModel(),
    score = score,
    spdxLicense = licenses.toString(),
    additionalData = additionalData
)

fun OrtDefect.mapToModel() =
    Defect(
        externalId = id,
        url = url.toString(),
        title = title,
        state = state,
        severity = severity,
        description = description,
        creationTime = creationTime?.toKotlinInstant(),
        modificationTime = modificationTime?.toKotlinInstant(),
        closingTime = closingTime?.toKotlinInstant(),
        fixReleaseVersion = fixReleaseVersion,
        fixReleaseUrl = fixReleaseUrl,
        labels = labels
    )

fun OrtDependencyGraph.mapToModel() =
    DependencyGraph(
        packages = packages.map { it.mapToModel() },
        nodes = nodes?.map { it.mapToModel() }.orEmpty(),
        edges = edges?.map { it.mapToModel() }.orEmpty(),
        scopes = scopes.mapValues { it.value.map { it.mapToModel() } }
    )

fun OrtDependencyGraphEdge.mapToModel() =
    DependencyGraphEdge(
        from = from,
        to = to
    )

fun OrtDependencyGraphNode.mapToModel() =
    DependencyGraphNode(
        pkg = pkg,
        fragment = fragment,
        linkage = linkage.name,
        issues = issues.map { it.mapToModel() }
    )

fun RootDependencyIndex.mapToModel() =
    DependencyGraphRoot(
        root = root,
        fragment = fragment
    )

fun OrtEnvironment.mapToModel() =
    Environment(
        ortVersion = ortVersion,
        javaVersion = javaVersion,
        os = os,
        maxMemory = maxMemory,
        processors = processors,
        variables = variables,
        toolVersions = toolVersions
    )

fun OrtIdentifier.mapToModel() = Identifier(type, namespace, name, version)

fun OrtLicenseFinding.mapToModel() =
    LicenseFinding(
        spdxLicense = license.toString(),
        location = location.mapToModel(),
        score = score
    )

fun OrtOrtIssue.mapToModel() =
    OrtIssue(
        timestamp = timestamp.toKotlinInstant(),
        source = source,
        message = message,
        severity = severity.name
    )

fun OrtPackage.mapToModel() =
    Package(
        identifier = id.mapToModel(),
        purl = purl,
        cpe = cpe,
        authors = authors,
        declaredLicenses = declaredLicenses,
        processedDeclaredLicense = declaredLicensesProcessed.mapToModel(),
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = binaryArtifact.mapToModel(),
        sourceArtifact = sourceArtifact.mapToModel(),
        vcs = vcs.mapToModel(),
        vcsProcessed = vcsProcessed.mapToModel(),
        isMetadataOnly = isMetadataOnly,
        isModified = isModified
    )

fun OrtPackageCurationProvider.mapToModel() = PackageCurationProviderConfig(name = id)

fun OrtPackageManagerConfiguration.mapToModel() =
    PackageManagerConfiguration(
        mustRunAfter = mustRunAfter,
        options = options
    )

fun OrtProcessedDeclaredLicense.mapToModel() =
    ProcessedDeclaredLicense(
        spdxExpression = spdxExpression.toString(),
        mappedLicenses = mapped.mapValues { it.toString() },
        unmappedLicenses = unmapped
    )

fun OrtProject.mapToModel() =
    Project(
        identifier = id.mapToModel(),
        cpe = cpe,
        definitionFilePath = definitionFilePath,
        authors = authors,
        declaredLicenses = declaredLicenses,
        vcs = vcs.mapToModel(),
        vcsProcessed = vcsProcessed.mapToModel(),
        homepageUrl = homepageUrl,
        scopeNames = scopeNames.orEmpty()
    )

fun OrtProvenance.mapToModel() =
    when (this) {
        is OrtArtifactProvenance -> ArtifactProvenance(sourceArtifact = sourceArtifact.mapToModel())
        is OrtRepositoryProvenance -> RepositoryProvenance(
            vcsInfo = vcsInfo.mapToModel(),
            resolvedRevision = resolvedRevision
        )

        is OrtUnknownProvenance -> UnknownProvenance
    }

fun OrtProvenanceResolutionResult.mapToModel() =
    ProvenanceResolutionResult(
        id = id.mapToModel(),
        packageProvenance = packageProvenance?.mapToModel() as? KnownProvenance,
        subRepositories = subRepositories.mapValues { it.value.mapToModel() },
        packageProvenanceResolutionIssue = packageProvenanceResolutionIssue?.mapToModel(),
        nestedProvenanceResolutionIssue = nestedProvenanceResolutionIssue?.mapToModel()
    )

fun OrtRemoteArtifact.mapToModel() =
    RemoteArtifact(
        url = url,
        hashValue = hash.value,
        hashAlgorithm = hash.algorithm.toString()
    )

fun OrtResolvedPackageCurations.mapToModel() =
    ResolvedPackageCurations(
        provider = provider.mapToModel(),
        curations = curations.map { it.mapToModel() }
    )

fun OrtScannerDetails.mapToModel() =
    ScannerDetail(
        name = name,
        version = version,
        configuration = configuration
    )

fun OrtTextLocation.mapToModel() = TextLocation(path = path, startLine = startLine, endLine = endLine)

fun OrtVcsInfo.mapToModel() = VcsInfo(type.mapToModel(), url, revision, path)

fun VcsType.mapToModel() = RepositoryType.forName(aliases.first())

fun OrtVulnerability.mapToModel() =
    Vulnerability(
        externalId = id,
        summary = summary,
        description = description,
        references = references.map { it.mapToModel() }
    )

fun OrtVulnerabilityReference.mapToModel() =
    VulnerabilityReference(
        url = url.toString(),
        scoringSystem = scoringSystem,
        severity = severity
    )

fun OrtEvaluatorRun.mapToModel(evaluatorJobId: Long) =
    EvaluatorRun(
        id = -1,
        evaluatorJobId = evaluatorJobId,
        startTime = startTime.toKotlinInstant(),
        endTime = endTime.toKotlinInstant(),
        violations = violations.map(OrtRuleViolation::mapToModel)
    )

fun OrtScannerRun.mapToModel(scannerJobId: Long) =
    ScannerRun(
        id = -1L,
        scannerJobId = scannerJobId,
        startTime = startTime.toKotlinInstant(),
        endTime = endTime.toKotlinInstant(),
        environment = environment.mapToModel(),
        config = config.mapToModel(),
        provenances = provenances.mapTo(mutableSetOf(), OrtProvenanceResolutionResult::mapToModel),
        scanResults = scanResults.mapTo(mutableSetOf(), OrtScanResult::mapToModel)
    )

fun OrtScannerConfiguration.mapToModel() =
    ScannerConfiguration(
        skipConcluded = skipConcluded,
        archive = archive?.mapToModel(),
        createMissingArchives = createMissingArchives,
        detectedLicenseMappings = detectedLicenseMapping,
        config = config?.mapValues { it.value.mapToModel() }.orEmpty(),
        storages = storages?.map { (name, storage) ->
            name to when (storage) {
                is OrtClearlyDefinedStorageConfiguration -> storage.mapToModel()
                is OrtFileBasedStorageConfiguration -> storage.mapToModel()
                is OrtPostgresStorageConfiguration -> storage.mapToModel()
                is OrtSw360StorageConfiguration -> storage.mapToModel()
            }
        }?.toMap().orEmpty(),
        storageReaders = storageReaders,
        storageWriters = storageWriters,
        ignorePatterns = ignorePatterns,
        provenanceStorage = provenanceStorage?.mapToModel()
    )

fun OrtScanResult.mapToModel() =
    ScanResult(
        provenance = provenance.mapToModel(),
        scanner = scanner.mapToModel(),
        summary = summary.mapToModel(),
        additionalData = additionalData
    )

fun OrtScanSummary.mapToModel() =
    ScanSummary(
        startTime = startTime.toKotlinInstant(),
        endTime = endTime.toKotlinInstant(),
        licenseFindings = licenseFindings.mapTo(mutableSetOf(), OrtLicenseFinding::mapToModel),
        copyrightFindings = copyrightFindings.mapTo(mutableSetOf(), OrtCopyrightFinding::mapToModel),
        snippetFindings = snippetFindings.mapTo(mutableSetOf(), OrtSnippetFinding::mapToModel),
        issues = issues.map(OrtOrtIssue::mapToModel)
    )

fun OrtProvenanceStorageConfiguration.mapToModel() =
    ProvenanceStorageConfiguration(
        fileStorage = fileStorage?.mapToModel(),
        postgresStorageConfiguration = postgresStorage?.mapToModel()
    )

fun OrtClearlyDefinedStorageConfiguration.mapToModel() = ClearlyDefinedStorageConfiguration(serverUrl)

fun OrtFileBasedStorageConfiguration.mapToModel() =
    FileBasedStorageConfiguration(
        backend = backend.mapToModel(),
        type = type.name
    )

fun OrtSw360StorageConfiguration.mapToModel() = Sw360StorageConfiguration(restUrl, authUrl, username, clientId)

fun OrtFileArchiverConfiguration.mapToModel() =
    FileArchiveConfiguration(
        enabled = enabled,
        fileStorage = fileStorage?.mapToModel(),
        postgresStorage = postgresStorage?.mapToModel()
    )

fun OrtFileStorageConfiguration.mapToModel() =
    FileStorageConfiguration(
        httpFileStorage = httpFileStorage?.mapToModel(),
        localFileStorage = localFileStorage?.mapToModel()
    )

fun OrtHttpFileStorageConfiguration.mapToModel() = HttpFileStorageConfiguration(url, query, headers)

fun OrtLocalFileStorageConfiguration.mapToModel() = LocalFileStorageConfiguration(directory.absolutePath, compression)

fun OrtPostgresStorageConfiguration.mapToModel() =
    PostgresStorageConfiguration(
        connection = connection.mapToModel(),
        type = type.name
    )

fun OrtPostgresConnection.mapToModel() =
    PostgresConnection(
        url = url,
        schema = schema,
        username = username,
        sslMode = sslmode,
        sslCert = sslcert,
        sslKey = sslkey,
        sslRootCert = sslrootcert,
        parallelTransactions = parallelTransactions
    )

fun OrtRuleViolation.mapToModel() = RuleViolation(
    rule = rule,
    packageId = pkg?.mapToModel(),
    severity = severity.name,
    message = message,
    howToFix = howToFix,
    license = license?.toString(),
    licenseSource = licenseSource?.name
)

fun OrtRepositoryConfiguration.mapToModel(ortRunId: Long) = RepositoryConfiguration(
    id = -1L,
    ortRunId = ortRunId,
    analyzerConfig = analyzer?.mapToModel(),
    excludes = excludes.mapToModel(),
    resolutions = resolutions.mapToModel(),
    curations = curations.mapToModel(),
    packageConfigurations = packageConfigurations.map(OrtPackageConfiguration::mapToModel),
    licenseChoices = licenseChoices.mapToModel()
)

fun OrtRepositoryAnalyzerConfiguration.mapToModel() = RepositoryAnalyzerConfiguration(
    allowDynamicVersions = allowDynamicVersions,
    enabledPackageManagers = enabledPackageManagers,
    disabledPackageManagers = disabledPackageManagers,
    packageManagers = packageManagers?.mapValues { it.value.mapToModel() },
    skipExcluded = skipExcluded
)

fun OrtExcludes.mapToModel() = Excludes(
    paths = paths.map(OrtPathExclude::mapToModel),
    scopes = scopes.map(OrtScopeExclude::mapToModel)
)

fun OrtResolutions.mapToModel() = Resolutions(
    issues = issues.map(OrtIssueResolution::mapToModel),
    ruleViolations = ruleViolations.map(OrtRuleViolationResolution::mapToModel),
    vulnerabilities = vulnerabilities.map(OrtVulnerabilityResolution::mapToModel)
)

fun OrtCurations.mapToModel() = Curations(
    packages = packages.map(OrtPackageCuration::mapToModel),
    licenseFindings = licenseFindings.map(OrtLicenseFindingCuration::mapToModel)
)

fun OrtPackageCuration.mapToModel() = PackageCuration(
    id = id.mapToModel(),
    data = data.mapToModel()
)

fun OrtLicenseFindingCuration.mapToModel() = LicenseFindingCuration(
    path = path,
    startLines = startLines,
    lineCount = lineCount,
    detectedLicense = detectedLicense?.toString(),
    concludedLicense = concludedLicense.toString(),
    reason = reason.name,
    comment = comment
)

fun OrtPackageCurationData.mapToModel() = PackageCurationData(
    comment = comment,
    purl = purl,
    cpe = cpe,
    authors = authors,
    concludedLicense = concludedLicense?.toString(),
    description = description,
    homepageUrl = homepageUrl,
    binaryArtifact = binaryArtifact?.mapToModel(),
    sourceArtifact = sourceArtifact?.mapToModel(),
    vcs = vcs?.mapToModel(),
    isMetadataOnly = isMetadataOnly,
    isModified = isModified,
    declaredLicenseMapping = declaredLicenseMapping.mapValues { it.value.toString() }
)

fun OrtVcsInfoCurationData.mapToModel() = VcsInfoCurationData(
    type = type?.mapToModel(),
    url = url,
    revision = revision,
    path = path
)

fun OrtPackageConfiguration.mapToModel() = PackageConfiguration(
    id = id.mapToModel(),
    sourceArtifactUrl = sourceArtifactUrl,
    vcs = vcs?.mapToModel(),
    pathExcludes = pathExcludes.map(OrtPathExclude::mapToModel),
    licenseFindingCurations = licenseFindingCurations.map(OrtLicenseFindingCuration::mapToModel)
)

fun OrtLicenseChoices.mapToModel() = LicenseChoices(
    repositoryLicenseChoices = repositoryLicenseChoices.map(OrtSpdxLicenseChoice::mapToModel),
    packageLicenseChoices = packageLicenseChoices.map(OrtPackageLicenseChoice::mapToModel)
)

fun OrtSpdxLicenseChoice.mapToModel() = SpdxLicenseChoice(given?.toString(), choice.toString())

fun OrtPackageLicenseChoice.mapToModel() = PackageLicenseChoice(
    identifier = packageId.mapToModel(),
    licenseChoices = licenseChoices.map(OrtSpdxLicenseChoice::mapToModel)
)

fun OrtVcsMatcher.mapToModel() = VcsMatcher(type.mapToModel(), url, revision)

fun OrtIssueResolution.mapToModel() = IssueResolution(message, reason.name, comment)

fun OrtRuleViolationResolution.mapToModel() = RuleViolationResolution(message, reason.name, comment)

fun OrtVulnerabilityResolution.mapToModel() = VulnerabilityResolution(id, reason.name, comment)

fun OrtPathExclude.mapToModel() = PathExclude(pattern, reason.name, comment)

fun OrtScopeExclude.mapToModel() = ScopeExclude(pattern, reason.name, comment)

fun OrtPluginConfiguration.mapToModel() = PluginConfiguration(options = options, secrets = secrets)
