/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common

import kotlinx.datetime.toKotlinInstant

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunDao
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraph
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphEdge
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphNode
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphRoot
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.EvaluatorRun
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.OrtRuleViolation as RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Defect
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.model.runs.repository.Curations
import org.eclipse.apoapsis.ortserver.model.runs.repository.Excludes
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.LicenseChoices
import org.eclipse.apoapsis.ortserver.model.runs.repository.LicenseFindingCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageLicenseChoice
import org.eclipse.apoapsis.ortserver.model.runs.repository.PathExclude
import org.eclipse.apoapsis.ortserver.model.runs.repository.ProvenanceSnippetChoices
import org.eclipse.apoapsis.ortserver.model.runs.repository.RepositoryAnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.RepositoryConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.ScopeExclude
import org.eclipse.apoapsis.ortserver.model.runs.repository.SpdxLicenseChoice
import org.eclipse.apoapsis.ortserver.model.runs.repository.VcsInfoCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.VcsMatcher
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.Choice
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.Given
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.Provenance
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.SnippetChoice
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.SnippetChoiceReason
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.CopyrightFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.KnownProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.LicenseFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ProvenanceResolutionResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.RepositoryProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanSummary
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerDetail
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.model.runs.scanner.Snippet
import org.eclipse.apoapsis.ortserver.model.runs.scanner.SnippetFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation
import org.eclipse.apoapsis.ortserver.model.runs.scanner.UnknownProvenance

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
import org.ossreviewtoolkit.model.Issue as OrtIssue
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
import org.ossreviewtoolkit.model.RootDependencyIndex as OrtRootDependencyIndex
import org.ossreviewtoolkit.model.RuleViolation as OrtRuleViolation
import org.ossreviewtoolkit.model.ScanResult as OrtScanResult
import org.ossreviewtoolkit.model.ScanSummary as OrtScanSummary
import org.ossreviewtoolkit.model.ScannerDetails as OrtScannerDetails
import org.ossreviewtoolkit.model.ScannerRun as OrtScannerRun
import org.ossreviewtoolkit.model.Severity as OrtSeverity
import org.ossreviewtoolkit.model.Snippet as OrtSnippet
import org.ossreviewtoolkit.model.SnippetFinding as OrtSnippetFinding
import org.ossreviewtoolkit.model.TextLocation as OrtTextLocation
import org.ossreviewtoolkit.model.UnknownProvenance as OrtUnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo as OrtVcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData as OrtVcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType as OrtVcsType
import org.ossreviewtoolkit.model.config.AdvisorConfiguration as OrtAdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration as OrtAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Curations as OrtCurations
import org.ossreviewtoolkit.model.config.Excludes as OrtExcludes
import org.ossreviewtoolkit.model.config.IssueResolution as OrtIssueResolution
import org.ossreviewtoolkit.model.config.LicenseChoices as OrtLicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration as OrtLicenseFindingCuration
import org.ossreviewtoolkit.model.config.PackageConfiguration as OrtPackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice as OrtPackageLicenseChoice
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration as OrtPackageManagerConfiguration
import org.ossreviewtoolkit.model.config.PathExclude as OrtPathExclude
import org.ossreviewtoolkit.model.config.PluginConfiguration as OrtPluginConfiguration
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration as OrtRepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration as OrtRepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions as OrtResolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution as OrtRuleViolationResolution
import org.ossreviewtoolkit.model.config.ScannerConfiguration as OrtScannerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude as OrtScopeExclude
import org.ossreviewtoolkit.model.config.SnippetChoices as OrtSnippetChoices
import org.ossreviewtoolkit.model.config.VcsMatcher as OrtVcsMatcher
import org.ossreviewtoolkit.model.config.VulnerabilityResolution as OrtVulnerabilityResolution
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice as OrtSnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason as OrtSnippetChoiceReason
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability as OrtVulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference as OrtVulnerabilityReference
import org.ossreviewtoolkit.utils.ort.Environment as OrtEnvironment
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense as OrtProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice as OrtSpdxLicenseChoice

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
        results = results.entries.associate { (k, v) ->
            k.mapToModel() to v.map { it.mapToModel() }
        }
    )

fun OrtAnalyzerConfiguration.mapToModel() =
    AnalyzerConfiguration(
        allowDynamicVersions = allowDynamicVersions,
        enabledPackageManagers = enabledPackageManagers,
        disabledPackageManagers = disabledPackageManagers,
        packageManagers = packageManagers?.mapValues { it.value.mapToModel() },
        skipExcluded = skipExcluded
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
        issues = result.issues.entries.flatMap { (k, v) ->
            val identifier = k.mapToModel()
            v.map { issue -> issue.mapToModel(identifier = identifier, worker = AnalyzerRunDao.ISSUE_WORKER_TYPE) }
        },
        dependencyGraphs = result.dependencyGraphs.mapValues { it.value.mapToModel() }
    )

fun OrtCopyrightFinding.mapToModel() = CopyrightFinding(statement = statement, location = location.mapToModel())

fun OrtCurations.mapToModel() = Curations(
    packages = packages.map(OrtPackageCuration::mapToModel),
    licenseFindings = licenseFindings.map(OrtLicenseFindingCuration::mapToModel)
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
        nodes = nodes.map { it.mapToModel() },
        edges = edges.mapTo(mutableSetOf()) { it.mapToModel() },
        scopes = scopes.mapValues { (_, indices) -> indices.map { it.mapToModel() } }
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

fun OrtEvaluatorRun.mapToModel(evaluatorJobId: Long) =
    EvaluatorRun(
        id = -1,
        evaluatorJobId = evaluatorJobId,
        startTime = startTime.toKotlinInstant(),
        endTime = endTime.toKotlinInstant(),
        violations = violations.map(OrtRuleViolation::mapToModel)
    )

fun OrtExcludes.mapToModel() = Excludes(
    paths = paths.map(OrtPathExclude::mapToModel),
    scopes = scopes.map(OrtScopeExclude::mapToModel)
)

fun OrtIdentifier.mapToModel() = Identifier(type, namespace, name, version)

fun OrtIssueResolution.mapToModel() = IssueResolution(message, reason.name, comment)

fun OrtLicenseChoices.mapToModel() = LicenseChoices(
    repositoryLicenseChoices = repositoryLicenseChoices.map(OrtSpdxLicenseChoice::mapToModel),
    packageLicenseChoices = packageLicenseChoices.map(OrtPackageLicenseChoice::mapToModel)
)

fun OrtLicenseFinding.mapToModel() =
    LicenseFinding(
        spdxLicense = license.toString(),
        location = location.mapToModel(),
        score = score
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

fun OrtIssue.mapToModel(identifier: Identifier? = null, worker: String? = null) =
    Issue(
        timestamp = timestamp.toKotlinInstant(),
        source = source,
        message = message,
        severity = severity.mapToModel(),
        affectedPath = affectedPath,
        identifier = identifier,
        worker = worker
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

fun OrtPackageConfiguration.mapToModel() = PackageConfiguration(
    id = id.mapToModel(),
    sourceArtifactUrl = sourceArtifactUrl,
    vcs = vcs?.mapToModel(),
    pathExcludes = pathExcludes.map(OrtPathExclude::mapToModel),
    licenseFindingCurations = licenseFindingCurations.map(OrtLicenseFindingCuration::mapToModel)
)

fun OrtPackageCuration.mapToModel() = PackageCuration(
    id = id.mapToModel(),
    data = data.mapToModel()
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

fun OrtPackageCurationProvider.mapToModel() = PackageCurationProviderConfig(name = id)

fun OrtPackageLicenseChoice.mapToModel() = PackageLicenseChoice(
    identifier = packageId.mapToModel(),
    licenseChoices = licenseChoices.map(OrtSpdxLicenseChoice::mapToModel)
)

fun OrtPackageManagerConfiguration.mapToModel() =
    PackageManagerConfiguration(
        mustRunAfter = mustRunAfter,
        options = options
    )

fun OrtPathExclude.mapToModel() = PathExclude(pattern, reason.name, comment)

fun OrtPluginConfiguration.mapToModel() = PluginConfiguration(options = options, secrets = secrets)

fun OrtProcessedDeclaredLicense.mapToModel() =
    ProcessedDeclaredLicense(
        spdxExpression = spdxExpression?.toString(),
        mappedLicenses = mapped.mapValues { it.value.toString() },
        unmappedLicenses = unmapped
    )

fun OrtProject.mapToModel() =
    Project(
        identifier = id.mapToModel(),
        cpe = cpe,
        definitionFilePath = definitionFilePath,
        authors = authors,
        declaredLicenses = declaredLicenses,
        processedDeclaredLicense = declaredLicensesProcessed.mapToModel(),
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

fun OrtRepositoryAnalyzerConfiguration.mapToModel() = RepositoryAnalyzerConfiguration(
    allowDynamicVersions = allowDynamicVersions,
    enabledPackageManagers = enabledPackageManagers,
    disabledPackageManagers = disabledPackageManagers,
    packageManagers = packageManagers?.mapValues { it.value.mapToModel() },
    skipExcluded = skipExcluded
)

fun OrtRepositoryConfiguration.mapToModel(ortRunId: Long) = RepositoryConfiguration(
    id = -1L,
    ortRunId = ortRunId,
    analyzerConfig = analyzer?.mapToModel(),
    excludes = excludes.mapToModel(),
    resolutions = resolutions.mapToModel(),
    curations = curations.mapToModel(),
    packageConfigurations = packageConfigurations.map(OrtPackageConfiguration::mapToModel),
    licenseChoices = licenseChoices.mapToModel(),
    provenanceSnippetChoices = snippetChoices.map(OrtSnippetChoices::mapToModel)
)

fun OrtResolutions.mapToModel() = Resolutions(
    issues = issues.map(OrtIssueResolution::mapToModel),
    ruleViolations = ruleViolations.map(OrtRuleViolationResolution::mapToModel),
    vulnerabilities = vulnerabilities.map(OrtVulnerabilityResolution::mapToModel)
)

fun OrtResolvedPackageCurations.mapToModel() =
    ResolvedPackageCurations(
        provider = provider.mapToModel(),
        curations = curations.map { it.mapToModel() }
    )

fun OrtRootDependencyIndex.mapToModel() =
    DependencyGraphRoot(
        root = root,
        fragment = fragment
    )

fun OrtRuleViolation.mapToModel() = RuleViolation(
    rule = rule,
    packageId = pkg?.mapToModel(),
    severity = severity.mapToModel(),
    message = message,
    howToFix = howToFix,
    license = license?.toString(),
    licenseSource = licenseSource?.name
)

fun OrtRuleViolationResolution.mapToModel() = RuleViolationResolution(message, reason.name, comment)

fun OrtScannerConfiguration.mapToModel() =
    ScannerConfiguration(
        skipConcluded = skipConcluded,
        skipExcluded = skipExcluded,
        detectedLicenseMappings = detectedLicenseMapping,
        config = config?.mapValues { it.value.mapToModel() }.orEmpty(),
        ignorePatterns = ignorePatterns
    )

fun OrtScannerDetails.mapToModel() =
    ScannerDetail(
        name = name,
        version = version,
        configuration = configuration
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
        scanResults = scanResults.mapTo(mutableSetOf(), OrtScanResult::mapToModel),
        scanners = scanners.mapKeys { it.key.mapToModel() }
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
        issues = issues.map(OrtIssue::mapToModel)
    )

fun OrtScopeExclude.mapToModel() = ScopeExclude(pattern, reason.name, comment)

fun OrtSeverity.mapToModel() = when (this) {
    OrtSeverity.ERROR -> Severity.ERROR
    OrtSeverity.WARNING -> Severity.WARNING
    OrtSeverity.HINT -> Severity.HINT
}

fun OrtSnippet.mapToModel() = Snippet(
    purl = purl,
    provenance = provenance.mapToModel(),
    location = location.mapToModel(),
    score = score,
    spdxLicense = license.toString(),
    additionalData = additionalData
)

fun OrtSnippetChoice.mapToModel() = SnippetChoice(
    Given(given.sourceLocation.mapToModel()),
    Choice(choice.purl, choice.reason.mapToOrt(), choice.comment)
)

fun OrtSnippetChoiceReason.mapToOrt() = SnippetChoiceReason.valueOf(name)

fun OrtSnippetChoices.mapToModel() = ProvenanceSnippetChoices(
    Provenance(provenance.url),
    choices.map(OrtSnippetChoice::mapToModel)
)

fun OrtSnippetFinding.mapToModel() = SnippetFinding(
    location = sourceLocation.mapToModel(),
    snippets = snippets.mapTo(mutableSetOf(), OrtSnippet::mapToModel)
)

fun OrtSpdxLicenseChoice.mapToModel() = SpdxLicenseChoice(given?.toString(), choice.toString())

fun OrtTextLocation.mapToModel() = TextLocation(path = path, startLine = startLine, endLine = endLine)

fun OrtVcsInfo.mapToModel() = VcsInfo(type.mapToModel(), url, revision, path)

fun OrtVcsInfoCurationData.mapToModel() = VcsInfoCurationData(
    type = type?.mapToModel(),
    url = url,
    revision = revision,
    path = path
)

fun OrtVcsMatcher.mapToModel() = VcsMatcher(type.mapToModel(), url, revision)

fun OrtVcsType.mapToModel() = RepositoryType.forName(toString())

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
        severity = severity,
        score = score,
        vector = vector
    )

fun OrtVulnerabilityResolution.mapToModel() = VulnerabilityResolution(id, reason.name, comment)
