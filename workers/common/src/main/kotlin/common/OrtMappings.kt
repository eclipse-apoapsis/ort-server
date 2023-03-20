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
import org.ossreviewtoolkit.model.Defect as OrtDefect
import org.ossreviewtoolkit.model.DependencyGraph as OrtDependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphEdge as OrtDependencyGraphEdge
import org.ossreviewtoolkit.model.DependencyGraphNode as OrtDependencyGraphNode
import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Issue as OrtOrtIssue
import org.ossreviewtoolkit.model.Package as OrtPackage
import org.ossreviewtoolkit.model.Project as OrtProject
import org.ossreviewtoolkit.model.RemoteArtifact as OrtRemoteArtifact
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.model.VcsInfo as OrtVcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.Vulnerability as OrtVulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference as OrtVulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration as OrtAdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration as OrtAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.GitHubDefectsConfiguration as OrtGitHubDefectsConfiguration
import org.ossreviewtoolkit.model.config.NexusIqConfiguration as OrtNexusIqConfiguration
import org.ossreviewtoolkit.model.config.OsvConfiguration as OrtOsvConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration as OrtPackageManagerConfiguration
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration as OrtVulnerableCodeConfiguration
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.DependencyGraph
import org.ossreviewtoolkit.server.model.runs.DependencyGraphEdge
import org.ossreviewtoolkit.server.model.runs.DependencyGraphNode
import org.ossreviewtoolkit.server.model.runs.DependencyGraphRoot
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.runs.Package
import org.ossreviewtoolkit.server.model.runs.PackageManagerConfiguration
import org.ossreviewtoolkit.server.model.runs.Project
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorResult
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.advisor.Defect
import org.ossreviewtoolkit.server.model.runs.advisor.GithubDefectsConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.NexusIqConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.OsvConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.Vulnerability
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerabilityReference
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerableCodeConfiguration
import org.ossreviewtoolkit.utils.ort.Environment as OrtEnvironment

fun OrtAdvisorConfiguration.mapToModel() =
    AdvisorConfiguration(
        githubDefectsConfiguration = gitHubDefects?.mapToModel(),
        nexusIqConfiguration = nexusIq?.mapToModel(),
        osvConfiguration = osv?.mapToModel(),
        vulnerableCodeConfiguration = vulnerableCode?.mapToModel(),
        // TODO: Currently, the type of options is Map<String, String>, which should be
        //       Map<String, Map<String, String>>. This has to be fixed in order to create the database
        //       correctly.
        options = emptyMap()
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

fun OrtGitHubDefectsConfiguration.mapToModel() =
    GithubDefectsConfiguration(
        endpointUrl = endpointUrl,
        labelFilter = labelFilter,
        maxNumberOfIssuesPerRepository = maxNumberOfIssuesPerRepository,
        parallelRequests = parallelRequests
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

fun OrtNexusIqConfiguration.mapToModel() = NexusIqConfiguration(serverUrl = serverUrl, browseUrl = browseUrl)

fun OrtOrtIssue.mapToModel() =
    OrtIssue(
        timestamp = timestamp.toKotlinInstant(),
        source = source,
        message = message,
        severity = severity.name
    )

fun OrtOsvConfiguration.mapToModel() = OsvConfiguration(serverUrl = serverUrl)

fun OrtPackage.mapToModel() =
    Package(
        identifier = id.mapToModel(),
        purl = purl,
        cpe = cpe,
        authors = authors,
        declaredLicenses = declaredLicenses,
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = binaryArtifact.mapToModel(),
        sourceArtifact = sourceArtifact.mapToModel(),
        vcs = vcs.mapToModel(),
        vcsProcessed = vcsProcessed.mapToModel(),
        isMetadataOnly = isMetadataOnly,
        isModified = isModified
    )

fun OrtPackageManagerConfiguration.mapToModel() =
    PackageManagerConfiguration(
        mustRunAfter = mustRunAfter,
        options = options
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

fun OrtRemoteArtifact.mapToModel() =
    RemoteArtifact(
        url = url,
        hashValue = hash.value,
        hashAlgorithm = hash.algorithm.toString()
    )

fun OrtVcsInfo.mapToModel() = VcsInfo(type.mapToModel(), url, revision, path)

fun VcsType.mapToModel() = when (this) {
    VcsType.GIT -> RepositoryType.GIT
    VcsType.GIT_REPO -> RepositoryType.GIT_REPO
    VcsType.MERCURIAL -> RepositoryType.MERCURIAL
    VcsType.SUBVERSION -> RepositoryType.SUBVERSION
    VcsType.CVS -> RepositoryType.CVS
    VcsType.UNKNOWN -> RepositoryType.UNKNOWN
    else -> throw IllegalArgumentException("Unknown VcsType: $this")
}

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

fun OrtVulnerableCodeConfiguration.mapToModel() = VulnerableCodeConfiguration(serverUrl = serverUrl)
