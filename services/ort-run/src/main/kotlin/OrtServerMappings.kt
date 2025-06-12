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

package org.eclipse.apoapsis.ortserver.services.ortrun

import java.net.URI
import java.time.Instant

import kotlinx.datetime.toJavaInstant

import org.eclipse.apoapsis.ortserver.model.JiraRestClientConfiguration
import org.eclipse.apoapsis.ortserver.model.MailServerConfiguration
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
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
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.SnippetChoice
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.SnippetChoiceReason
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.CopyrightFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.KnownProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.LicenseFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.NestedProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.NestedProvenanceScanResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.Provenance
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

import org.ossreviewtoolkit.model.AdvisorCapability as OrtAdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails as OrtAdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult as OrtAdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun as OrtAdvisorRun
import org.ossreviewtoolkit.model.AdvisorSummary as OrtAdvisorSummary
import org.ossreviewtoolkit.model.AnalyzerResult as OrtAnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun as OrtAnalyzerRun
import org.ossreviewtoolkit.model.ArtifactProvenance as OrtArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding as OrtCopyrightFinding
import org.ossreviewtoolkit.model.Defect as OrtDefect
import org.ossreviewtoolkit.model.DependencyGraph as OrtDependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphEdge as OrtDependencyGraphEdge
import org.ossreviewtoolkit.model.DependencyGraphNode as OrtDependencyGraphNode
import org.ossreviewtoolkit.model.EvaluatorRun as OrtEvaluatorRun
import org.ossreviewtoolkit.model.Hash as OrtHash
import org.ossreviewtoolkit.model.HashAlgorithm.Companion as OrtHashAlgorithm
import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Issue as OrtIssue
import org.ossreviewtoolkit.model.KnownProvenance as OrtKnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding as OrtLicenseFinding
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package as OrtPackage
import org.ossreviewtoolkit.model.PackageCuration as OrtPackageCuration
import org.ossreviewtoolkit.model.PackageCurationData as OrtPackageCurationData
import org.ossreviewtoolkit.model.PackageLinkage as OrtPackageLinkage
import org.ossreviewtoolkit.model.Project as OrtProject
import org.ossreviewtoolkit.model.ProvenanceResolutionResult as OrtProvenanceResolutionResult
import org.ossreviewtoolkit.model.RemoteArtifact as OrtRemoteArtifact
import org.ossreviewtoolkit.model.Repository as OrtRepository
import org.ossreviewtoolkit.model.RepositoryProvenance as OrtRepositoryProvenance
import org.ossreviewtoolkit.model.ResolvedConfiguration as OrtResolvedConfiguration
import org.ossreviewtoolkit.model.ResolvedPackageCurations as OrtResolvedPackageCurations
import org.ossreviewtoolkit.model.RootDependencyIndex as OrtRootDependencyIndex
import org.ossreviewtoolkit.model.RuleViolation as OrtRuleViolation
import org.ossreviewtoolkit.model.ScanResult as OrtScanResult
import org.ossreviewtoolkit.model.ScanSummary as OrtScanSummary
import org.ossreviewtoolkit.model.ScannerDetails as OrtScannerDetails
import org.ossreviewtoolkit.model.ScannerRun as OrtScannerRun
import org.ossreviewtoolkit.model.Severity as OrtSeverity
import org.ossreviewtoolkit.model.Snippet as OrtSnippet
import org.ossreviewtoolkit.model.SnippetFinding as OrtSnippetFinding
import org.ossreviewtoolkit.model.SourceCodeOrigin as OrtSourceCodeOrigin
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
import org.ossreviewtoolkit.model.config.IssueResolutionReason as OrtIssueResolutionReason
import org.ossreviewtoolkit.model.config.JiraConfiguration as OrtJiraConfiguration
import org.ossreviewtoolkit.model.config.LicenseChoices as OrtLicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration as OrtLicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason as OrtLicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.PackageConfiguration as OrtPackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice as OrtPackageLicenseChoice
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration as OrtPackageManagerConfiguration
import org.ossreviewtoolkit.model.config.PathExclude as OrtPathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason as OrtPathExcludeReason
import org.ossreviewtoolkit.model.config.ProviderPluginConfiguration as OrtProviderPluginConfiguration
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration as OrtRepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration as OrtRepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions as OrtResolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution as OrtRuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason as OrtRuleViolationResolutionReason
import org.ossreviewtoolkit.model.config.ScannerConfiguration as OrtScannerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude as OrtScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason as OrtScopeExcludeReason
import org.ossreviewtoolkit.model.config.SendMailConfiguration as OrtSendMailConfiguration
import org.ossreviewtoolkit.model.config.SnippetChoices as OrtSnippetChoices
import org.ossreviewtoolkit.model.config.VcsMatcher as OrtVcsMatcher
import org.ossreviewtoolkit.model.config.VulnerabilityResolution as OrtVulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason as OrtVulnerabilityResolutionReason
import org.ossreviewtoolkit.model.config.snippet.Choice
import org.ossreviewtoolkit.model.config.snippet.Given
import org.ossreviewtoolkit.model.config.snippet.Provenance as OrtProvenance
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice as OrtSnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason as OrtSnippetChoiceReason
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability as OrtVulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference as OrtVulnerabilityReference
import org.ossreviewtoolkit.plugins.api.PluginConfig as OrtPluginConfig
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance as OrtNestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult as OrtNestedProvenanceScanResult
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.Environment as OrtEnvironment
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense as OrtProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice as OrtSpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

fun AdvisorConfiguration.mapToOrt() =
    OrtAdvisorConfiguration(
        advisors = config.mapValues { it.value.mapToOrt() }
    )

fun AdvisorResult.mapToOrt() =
    OrtAdvisorResult(
        advisor = OrtAdvisorDetails(advisorName, capabilities.mapTo(enumSetOf(), OrtAdvisorCapability::valueOf)),
        summary = OrtAdvisorSummary(
            startTime = startTime.toJavaInstant(),
            endTime = endTime.toJavaInstant(),
            issues = issues.map(Issue::mapToOrt)
        ),
        defects = defects.map(Defect::mapToOrt),
        vulnerabilities = vulnerabilities.map(Vulnerability::mapToOrt)
    )

fun AdvisorRun.mapToOrt() =
    OrtAdvisorRun(
        startTime = startTime.toJavaInstant(),
        endTime = endTime.toJavaInstant(),
        environment = environment.mapToOrt(),
        config = config.mapToOrt(),
        results = results.entries.associateTo(sortedMapOf()) {
            it.key.mapToOrt() to it.value.map(AdvisorResult::mapToOrt)
        }
    )

fun AnalyzerConfiguration.mapToOrt() =
    OrtAnalyzerConfiguration(
        allowDynamicVersions = allowDynamicVersions,
        enabledPackageManagers = enabledPackageManagers,
        disabledPackageManagers = disabledPackageManagers,
        packageManagers = packageManagers?.mapValues { it.value.mapToOrt() },
        skipExcluded = skipExcluded
    )

fun AnalyzerRun.mapToOrt() =
    OrtAnalyzerRun(
        startTime = startTime.toJavaInstant(),
        endTime = endTime.toJavaInstant(),
        environment = environment.mapToOrt(),
        config = config.mapToOrt(),
        result = OrtAnalyzerResult(
            projects = projects.mapTo(mutableSetOf(), Project::mapToOrt),
            // TODO: Currently, curations are not stored at all, therefore the mapping just creates the OrtPackage.
            packages = packages.mapTo(mutableSetOf()) { it.mapToOrt() },
            issues = issues.mapToOrt(),
            dependencyGraphs = dependencyGraphs.mapValues { it.value.mapToOrt() }
        )
    )

private fun Collection<Issue>.mapToOrt(): Map<OrtIdentifier, List<OrtIssue>> =
    mapNotNull { issue ->
        issue.identifier?.let { it.mapToOrt() to issue.mapToOrt() }
    }.groupBy({ it.first }, { it.second })

fun ArtifactProvenance.mapToOrt() = OrtArtifactProvenance(sourceArtifact.mapToOrt())

fun CopyrightFinding.mapToOrt() = OrtCopyrightFinding(statement, location.mapToOrt())

fun Curations.mapToOrt() = OrtCurations(
    packages = packages.map(PackageCuration::mapToOrt),
    licenseFindings = licenseFindings.map(LicenseFindingCuration::mapToOrt)
)

fun Defect.mapToOrt() =
    OrtDefect(
        id = externalId,
        url = URI.create(url),
        title = title,
        state = state,
        severity = severity,
        description = description,
        creationTime = creationTime?.toJavaInstant(),
        modificationTime = modificationTime?.toJavaInstant(),
        closingTime = closingTime?.toJavaInstant(),
        fixReleaseVersion = fixReleaseVersion,
        fixReleaseUrl = fixReleaseUrl,
        labels = labels
    )

fun DependencyGraph.mapToOrt() =
    OrtDependencyGraph(
        packages = packages.map(Identifier::mapToOrt),
        scopes = scopes.mapValues { it.value.map(DependencyGraphRoot::mapToOrt) },
        nodes = nodes.map(DependencyGraphNode::mapToOrt),
        edges = edges.mapTo(mutableSetOf(), DependencyGraphEdge::mapToOrt)
    )

fun DependencyGraphEdge.mapToOrt() = OrtDependencyGraphEdge(from, to)

fun DependencyGraphNode.mapToOrt() =
    OrtDependencyGraphNode(
        pkg = pkg,
        fragment = fragment,
        linkage = OrtPackageLinkage.valueOf(linkage),
        issues = issues.map(Issue::mapToOrt)
    )

fun DependencyGraphRoot.mapToOrt() = OrtRootDependencyIndex(root, fragment)

fun Environment.mapToOrt() =
    OrtEnvironment(
        os = os,
        ortVersion = ortVersion,
        javaVersion = javaVersion,
        processors = processors,
        maxMemory = maxMemory,
        variables = variables,
        toolVersions = toolVersions
    )

fun EvaluatorRun.mapToOrt() =
    OrtEvaluatorRun(
        startTime = startTime.toJavaInstant(),
        endTime = endTime.toJavaInstant(),
        violations = violations.map(RuleViolation::mapToOrt)
    )

fun Excludes.mapToOrt() = OrtExcludes(paths.map(PathExclude::mapToOrt), scopes.map(ScopeExclude::mapToOrt))

fun Identifier.mapToOrt() = OrtIdentifier(type, namespace, name, version)

fun Issue.mapToOrt() = OrtIssue(timestamp.toJavaInstant(), source, message, severity.mapToOrt(), affectedPath)

fun IssueResolution.mapToOrt() = OrtIssueResolution(message, OrtIssueResolutionReason.valueOf(reason), comment)

fun JiraRestClientConfiguration.mapToOrt() =
    OrtJiraConfiguration(
        host = serverUrl,
        username = username,
        password = password
    )

fun KnownProvenance.mapToOrt() =
    when (this) {
        is ArtifactProvenance -> this.mapToOrt()
        is RepositoryProvenance -> this.mapToOrt()
    }

fun LicenseChoices.mapToOrt() =
    OrtLicenseChoices(
        repositoryLicenseChoices = repositoryLicenseChoices.map(SpdxLicenseChoice::mapToOrt),
        packageLicenseChoices = packageLicenseChoices.map(PackageLicenseChoice::mapToOrt)
    )

fun LicenseFinding.mapToOrt() = OrtLicenseFinding(
    license = SpdxExpression.Companion.parse(spdxLicense),
    location = location.mapToOrt(),
    score = score
)

fun LicenseFindingCuration.mapToOrt() = OrtLicenseFindingCuration(
    path = path,
    startLines = startLines,
    lineCount = lineCount,
    detectedLicense = detectedLicense?.toSpdx(),
    concludedLicense = concludedLicense.toSpdx(),
    reason = OrtLicenseFindingCurationReason.valueOf(reason),
    comment = comment
)

fun MailServerConfiguration.mapToOrt() =
    OrtSendMailConfiguration(
        hostName = hostName,
        port = port,
        username = username,
        password = password,
        useSsl = useSsl,
        fromAddress = fromAddress
    )

fun NestedProvenance.mapToOrt() =
    OrtNestedProvenance(
        root = root.mapToOrt(),
        subRepositories = subRepositories.mapValues { it.value.mapToOrt() }
    )

fun NestedProvenanceScanResult.mapToOrt() =
    OrtNestedProvenanceScanResult(
        nestedProvenance = nestedProvenance.mapToOrt(),
        scanResults = scanResults.entries.associate { (provenance, results) ->
            provenance.mapToOrt() to results.map(ScanResult::mapToOrt)
        }
    )

fun OrtRun.mapToOrt(
    repository: OrtRepository,
    analyzerRun: OrtAnalyzerRun? = null,
    advisorRun: OrtAdvisorRun? = null,
    scannerRun: OrtScannerRun? = null,
    evaluatorRun: OrtEvaluatorRun? = null,
    resolvedConfiguration: OrtResolvedConfiguration
) = OrtResult(
    repository = repository,
    analyzer = analyzerRun,
    advisor = advisorRun,
    scanner = scannerRun,
    evaluator = evaluatorRun,
    labels = labels,
    resolvedConfiguration = resolvedConfiguration
)

fun Package.mapToOrt() =
    OrtPackage(
        id = identifier.mapToOrt(),
        purl = purl,
        cpe = cpe,
        authors = authors,
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = processedDeclaredLicense.mapToOrt(),
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = binaryArtifact.mapToOrt(),
        sourceArtifact = sourceArtifact.mapToOrt(),
        vcs = vcs.mapToOrt(),
        vcsProcessed = vcsProcessed.mapToOrt(),
        isMetadataOnly = isMetadataOnly,
        isModified = isModified
    )

fun PackageConfiguration.mapToOrt() =
    OrtPackageConfiguration(
        id = id.mapToOrt(),
        sourceArtifactUrl = sourceArtifactUrl,
        vcs = vcs?.mapToOrt(),
        pathExcludes = pathExcludes.map(PathExclude::mapToOrt),
        licenseFindingCurations = licenseFindingCurations.map(LicenseFindingCuration::mapToOrt)
    )

fun PackageCuration.mapToOrt() = OrtPackageCuration(id.mapToOrt(), data.mapToOrt())

fun PackageCurationData.mapToOrt() = OrtPackageCurationData(
    comment = comment,
    purl = purl,
    cpe = cpe,
    authors = authors,
    concludedLicense = concludedLicense?.toSpdx(),
    description = description,
    homepageUrl = homepageUrl,
    binaryArtifact = binaryArtifact?.mapToOrt(),
    sourceArtifact = sourceArtifact?.mapToOrt(),
    vcs = vcs?.mapToOrt(),
    isMetadataOnly = isMetadataOnly,
    isModified = isModified,
    declaredLicenseMapping = declaredLicenseMapping.mapValues { it.value.toSpdx() }
)

fun PackageCurationProviderConfig.mapToOrt() = OrtResolvedPackageCurations.Provider(id = name)

fun PackageLicenseChoice.mapToOrt() =
    OrtPackageLicenseChoice(
        packageId = identifier.mapToOrt(),
        licenseChoices = licenseChoices.map(SpdxLicenseChoice::mapToOrt)
    )

fun PackageManagerConfiguration.mapToOrt() = OrtPackageManagerConfiguration(mustRunAfter, options)

fun PathExclude.mapToOrt() = OrtPathExclude(pattern, OrtPathExcludeReason.valueOf(reason), comment)

fun PluginConfig.mapToOrt() =
    OrtPluginConfig(
        options = options,
        secrets = secrets
    )

fun ProcessedDeclaredLicense.mapToOrt() =
    OrtProcessedDeclaredLicense(
        spdxExpression = spdxExpression?.toSpdx(),
        mapped = mappedLicenses.mapValues { it.value.toSpdx() },
        unmapped = unmappedLicenses
    )

fun Project.mapToOrt() =
    OrtProject(
        id = identifier.mapToOrt(),
        cpe = cpe,
        definitionFilePath = definitionFilePath,
        authors = authors,
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = processedDeclaredLicense.mapToOrt(),
        vcs = vcs.mapToOrt(),
        vcsProcessed = vcsProcessed.mapToOrt(),
        description = description,
        homepageUrl = homepageUrl,
        scopeNames = scopeNames.toSortedSet()
    )

fun Provenance.mapToOrt() =
    when (this) {
        is ArtifactProvenance -> this.mapToOrt()
        is RepositoryProvenance -> this.mapToOrt()
        UnknownProvenance -> OrtUnknownProvenance
    }

fun ProvenanceResolutionResult.mapToOrt() =
    OrtProvenanceResolutionResult(
        id = id.mapToOrt(),
        packageProvenance = packageProvenance?.mapToOrt(),
        subRepositories = subRepositories.mapValues { it.value.mapToOrt() },
        packageProvenanceResolutionIssue = packageProvenanceResolutionIssue?.mapToOrt(),
        nestedProvenanceResolutionIssue = nestedProvenanceResolutionIssue?.mapToOrt()
    )

fun ProvenanceSnippetChoices.mapToOrt() = OrtSnippetChoices(
    OrtProvenance(provenance.url),
    choices.map(SnippetChoice::mapToOrt)
)

fun ProviderPluginConfiguration.mapToOrt() =
    OrtProviderPluginConfiguration(
        type = type,
        id = id,
        enabled = enabled,
        options = options,
        secrets = secrets
    )

fun RemoteArtifact.mapToOrt() =
    OrtRemoteArtifact(
        url = url,
        hash = OrtHash(
            // Convert hash values to lowercase as this is required by the SpdxDocument reporter.
            value = hashValue.lowercase(),
            algorithm = OrtHashAlgorithm.fromString(hashAlgorithm)
        )
    )

fun Repository.mapToOrt(revision: String, path: String = "", repositoryConfig: OrtRepositoryConfiguration) =
    OrtRepository(
        vcs = OrtVcsInfo(
            type = OrtVcsType.forName(type.name),
            url = url,
            revision = revision,
            // TODO: The path of the repository is not stored at all.
            path = path
        ),
        // TODO: Nested repositories are not stored in the current implementation of the ORT server repository.
        nestedRepositories = emptyMap(),
        // TODO: The repository configuration is not stored at all.
        config = repositoryConfig
    )

fun RepositoryAnalyzerConfiguration.mapToOrt() = OrtRepositoryAnalyzerConfiguration(
    allowDynamicVersions = allowDynamicVersions,
    enabledPackageManagers = enabledPackageManagers,
    disabledPackageManagers = disabledPackageManagers,
    packageManagers = packageManagers?.mapValues { it.value.mapToOrt() },
    skipExcluded = skipExcluded
)

fun RepositoryConfiguration.mapToOrt() = OrtRepositoryConfiguration(
    analyzer = analyzerConfig?.mapToOrt(),
    excludes = excludes.mapToOrt(),
    resolutions = resolutions.mapToOrt(),
    curations = curations.mapToOrt(),
    packageConfigurations = packageConfigurations.map(PackageConfiguration::mapToOrt),
    licenseChoices = licenseChoices.mapToOrt(),
    snippetChoices = provenanceSnippetChoices.map(ProvenanceSnippetChoices::mapToOrt)
)

fun RepositoryProvenance.mapToOrt() = OrtRepositoryProvenance(vcsInfo.mapToOrt(), resolvedRevision)

fun Resolutions.mapToOrt() =
    OrtResolutions(
        issues = issues.map(IssueResolution::mapToOrt),
        ruleViolations = ruleViolations.map(RuleViolationResolution::mapToOrt),
        vulnerabilities = vulnerabilities.map(VulnerabilityResolution::mapToOrt)
    )

fun ResolvedConfiguration.mapToOrt() =
    OrtResolvedConfiguration(
        packageConfigurations = packageConfigurations.map { it.mapToOrt() },
        packageCurations = packageCurations.map { it.mapToOrt() },
        resolutions = resolutions.mapToOrt()
    )

fun ResolvedPackageCurations.mapToOrt() =
    OrtResolvedPackageCurations(
        provider = provider.mapToOrt(),
        curations = curations.map { it.mapToOrt() }
    )

fun RuleViolation.mapToOrt() =
    OrtRuleViolation(
        rule = rule,
        pkg = packageId?.mapToOrt(),
        license = license?.let { SpdxSingleLicenseExpression.parse(it) },
        licenseSource = licenseSource?.let { LicenseSource.valueOf(it) },
        severity = severity.mapToOrt(),
        message = message,
        howToFix = howToFix
    )

fun RuleViolationResolution.mapToOrt() =
    OrtRuleViolationResolution(
        message = message,
        reason = OrtRuleViolationResolutionReason.valueOf(reason),
        comment = comment
    )

fun ScannerConfiguration.mapToOrt() =
    OrtScannerConfiguration(
        skipConcluded = skipConcluded,
        skipExcluded = skipExcluded,
        detectedLicenseMapping = detectedLicenseMappings,
        scanners = config.mapValues { it.value.mapToOrt() },
        ignorePatterns = ignorePatterns
    )

fun ScannerDetail.mapToOrt() = OrtScannerDetails(name, version, configuration)

fun ScannerRun.mapToOrt() =
    OrtScannerRun(
        startTime = startTime?.toJavaInstant() ?: Instant.EPOCH,
        endTime = endTime?.toJavaInstant() ?: Instant.EPOCH,
        environment = environment?.mapToOrt() ?: OrtEnvironment(),
        config = config?.mapToOrt() ?: OrtScannerConfiguration(),
        provenances = provenances.mapTo(mutableSetOf(), ProvenanceResolutionResult::mapToOrt),
        scanResults = scanResults.mapTo(mutableSetOf(), ScanResult::mapToOrt),
        files = emptySet(),
        scanners = scanners.mapKeys { it.key.mapToOrt() }
    )

fun ScanResult.mapToOrt() =
    OrtScanResult(
        provenance = provenance.mapToOrt(),
        scanner = scanner.mapToOrt(),
        summary = summary.mapToOrt(),
        additionalData = additionalData
    )

fun ScanSummary.mapToOrt() =
    OrtScanSummary(
        startTime = startTime.toJavaInstant(),
        endTime = endTime.toJavaInstant(),
        licenseFindings = licenseFindings.mapTo(mutableSetOf(), LicenseFinding::mapToOrt),
        copyrightFindings = copyrightFindings.mapTo(mutableSetOf(), CopyrightFinding::mapToOrt),
        snippetFindings = snippetFindings.mapTo(mutableSetOf(), SnippetFinding::mapToOrt),
        issues = issues.map(Issue::mapToOrt)
    )

fun ScopeExclude.mapToOrt() = OrtScopeExclude(pattern, OrtScopeExcludeReason.valueOf(reason), comment)

fun Severity.mapToOrt() = when (this) {
    Severity.ERROR -> OrtSeverity.ERROR
    Severity.WARNING -> OrtSeverity.WARNING
    Severity.HINT -> OrtSeverity.HINT
}

fun Snippet.mapToOrt() = OrtSnippet(
    score = score,
    location = location.mapToOrt(),
    provenance = provenance.mapToOrt() as OrtKnownProvenance,
    purl = purl,
    license = spdxLicense.toSpdx(),
    additionalData = additionalData
)

fun SnippetChoice.mapToOrt() = OrtSnippetChoice(
    Given(given.sourceLocation.mapToOrt()),
    Choice(
        choice.purl,
        choice.reason.mapToOrt(),
        choice.comment
    )
)

fun SnippetChoiceReason.mapToOrt() = OrtSnippetChoiceReason.valueOf(name)

fun SnippetFinding.mapToOrt() = OrtSnippetFinding(
    sourceLocation = location.mapToOrt(),
    snippets = snippets.mapTo(mutableSetOf()) { it.mapToOrt() }
)

fun SpdxLicenseChoice.mapToOrt() = OrtSpdxLicenseChoice(given?.toSpdx(), choice.toSpdx())

fun SourceCodeOrigin.mapToOrt() =
    when (this) {
        SourceCodeOrigin.ARTIFACT -> OrtSourceCodeOrigin.ARTIFACT
        SourceCodeOrigin.VCS -> OrtSourceCodeOrigin.VCS
    }

fun TextLocation.mapToOrt() = OrtTextLocation(path, startLine, endLine)

fun VcsInfo.mapToOrt() = OrtVcsInfo(OrtVcsType.forName(type.name), url, revision, path)

fun VcsInfoCurationData.mapToOrt() = OrtVcsInfoCurationData(
    type = type?.name?.let { OrtVcsType.forName(it) },
    url = url,
    revision = revision,
    path = path
)

fun VcsMatcher.mapToOrt() = OrtVcsMatcher(OrtVcsType.forName(type.name), url, revision)

fun Vulnerability.mapToOrt() =
    OrtVulnerability(
        id = externalId,
        summary = summary,
        description = description,
        references = references.map(VulnerabilityReference::mapToOrt)
    )

fun VulnerabilityReference.mapToOrt() = OrtVulnerabilityReference(
    URI.create(url),
    scoringSystem,
    severity,
    score,
    vector
)

fun VulnerabilityResolution.mapToOrt() = OrtVulnerabilityResolution(
    id = externalId,
    reason = OrtVulnerabilityResolutionReason.valueOf(reason),
    comment = comment
)
