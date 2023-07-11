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

package org.ossreviewtoolkit.server.workers.common

import java.io.File
import java.net.URI

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphNode
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileStorageConfiguration
import org.ossreviewtoolkit.model.config.GitHubDefectsConfiguration
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.NexusIqConfiguration
import org.ossreviewtoolkit.model.config.OsvConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.StorageType
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.Environment

object OrtTestData {
    const val TIME_STAMP_SECONDS = 1678119934L

    val ortRepository = Repository(
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/org/repo.git",
            revision = "abc123",
            path = ""
        ),
        nestedRepositories = emptyMap(),
        config = RepositoryConfiguration()
    )

    val ortEnvironment = Environment(
        os = "Linux",
        ortVersion = "def456",
        javaVersion = "17",
        processors = 8,
        maxMemory = 12884901888L,
        variables = mapOf("JAVA_HOME" to "/opt/java/openjdk"),
        toolVersions = emptyMap()
    )

    val ortAnalyzerConfiguration = AnalyzerConfiguration(
        allowDynamicVersions = false,
        enabledPackageManagers = listOf("Maven", "Gradle"),
        disabledPackageManagers = listOf("NPM"),
        packageManagers = mapOf(
            "DotNet" to PackageManagerConfiguration(options = mapOf("directDependenciesOnly" to "true")),
            "NPM" to PackageManagerConfiguration(options = mapOf("legacyPeerDeps" to "true")),
            "NuGet" to PackageManagerConfiguration(options = mapOf("directDependenciesOnly" to "true"))
        )
    )

    val ortProject = Project(
        id = Identifier(
            type = "Maven",
            namespace = "com.example",
            name = "project",
            version = "1.0"
        ),
        cpe = "cpe:example",
        definitionFilePath = "pom.xml",
        authors = setOf("Author One", "Author Two"),
        declaredLicenses = setOf("The MIT License", "Eclipse Public License 1.0"),
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = "git@github.com/org/project.git",
            revision = "",
            path = ""
        ),
        vcsProcessed = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/org.project.git",
            revision = "abc123",
            path = ""
        ),
        homepageUrl = "https://example-homepage.com",
        scopeNames = sortedSetOf("compile")
    )

    val ortPkgIdentifier = Identifier(
        type = "Maven",
        namespace = "com.example",
        name = "package",
        version = "1.0"
    )

    val ortPkg = Package(
        id = ortPkgIdentifier,
        purl = "Maven:com.example:package:1.0",
        cpe = "cpe:example",
        authors = setOf("Author One", "Author Two"),
        declaredLicenses = setOf("Eclipse Public License 1.0"),
        description = "Example description",
        homepageUrl = "https://package-homepage.com",
        binaryArtifact = RemoteArtifact(
            url = "https://repo.com/package-1.0.jar",
            hash = Hash(
                value = "123456",
                algorithm = HashAlgorithm.SHA1
            )
        ),
        sourceArtifact = RemoteArtifact(
            url = "https://repo.com/package-1.0-sources.jar",
            hash = Hash(
                value = "654321",
                algorithm = HashAlgorithm.SHA1
            )
        ),
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = "git://github.com/org/package.git",
            revision = "1.0",
            path = ""
        ),
        vcsProcessed = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/org/package.git",
            revision = "1.0",
            path = ""
        ),
        isMetadataOnly = true,
        isModified = true
    )

    val ortIssue = Issue(
        timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        source = "tool-x",
        message = "An issue occured.",
        severity = Severity.ERROR
    )

    val ortDependencyGraph = DependencyGraph(
        packages = listOf(ortPkgIdentifier),
        nodes = listOf(DependencyGraphNode(0)),
        edges = emptyList(),
        scopes = mapOf(
            "com.example:project:1.0:compile" to listOf(RootDependencyIndex(0))
        )
    )

    val ortAnalyzerRun = AnalyzerRun(
        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        environment = ortEnvironment,
        config = ortAnalyzerConfiguration,
        result = AnalyzerResult(
            projects = setOf(ortProject),
            packages = setOf(ortPkg),
            issues = mapOf(ortPkgIdentifier to listOf(ortIssue)),
            dependencyGraphs = mapOf("Maven" to ortDependencyGraph)
        )
    )

    val ortOsvConfiguration = OsvConfiguration(
        serverUrl = "https://osv.com"
    )

    val ortGithubDefectsConfiguration = GitHubDefectsConfiguration(
        token = null,
        endpointUrl = "https://github.com",
        labelFilter = listOf("filter-1", "filter-2"),
        maxNumberOfIssuesPerRepository = 5,
        parallelRequests = 2
    )

    val ortVulnerableCodeConfiguration = VulnerableCodeConfiguration(
        serverUrl = "https://vulnerablecode.com",
        apiKey = null
    )

    val ortNexusIqConfiguration = NexusIqConfiguration(
        serverUrl = "https://nexusiq.com",
        browseUrl = "https://nexusiq.com/browse",
        username = null,
        password = null
    )

    val ortAdvisorConfiguration = AdvisorConfiguration(
        osv = ortOsvConfiguration,
        gitHubDefects = ortGithubDefectsConfiguration,
        vulnerableCode = ortVulnerableCodeConfiguration,
        nexusIq = ortNexusIqConfiguration,
        options = emptyMap()
    )

    val ortAdvisorRecord = AdvisorRecord(
        advisorResults = sortedMapOf(
            ortPkgIdentifier to listOf(
                AdvisorResult(
                    advisor = AdvisorDetails(
                        name = "VulnerableCode",
                        capabilities = enumSetOf(AdvisorCapability.VULNERABILITIES)
                    ),
                    summary = AdvisorSummary(
                        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                        issues = listOf(ortIssue)
                    ),
                    defects = emptyList(),
                    vulnerabilities = listOf(
                        Vulnerability(
                            id = "CVE-2023-0001",
                            summary = "Example summary.",
                            description = "Example description.",
                            references = listOf(
                                VulnerabilityReference(
                                    url = URI("http://cve.mitre.org"),
                                    scoringSystem = "CVSS3",
                                    severity = "5.5"
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    val ortAdvisorRun = AdvisorRun(
        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        environment = ortEnvironment,
        config = ortAdvisorConfiguration,
        results = ortAdvisorRecord
    )

    val ortFileStorageConfiguration = FileStorageConfiguration(
        localFileStorage = LocalFileStorageConfiguration(
            directory = File("/path/to/storage"),
            compression = true
        )
    )

    val ortScannerConfiguration = ScannerConfiguration(
        skipConcluded = true,
        archive = FileArchiverConfiguration(
            enabled = true,
            fileStorage = ortFileStorageConfiguration
        ),
        createMissingArchives = true,
        detectedLicenseMapping = mapOf("license-1" to "spdx-license-1", "license-2" to "spdx-license-2"),
        options = mapOf("scanner-1" to mapOf("option-key-1" to "option-value-1")),
        storages = mapOf(
            "local" to FileBasedStorageConfiguration(
                backend = ortFileStorageConfiguration,
                type = StorageType.PROVENANCE_BASED
            )
        ),
        storageReaders = listOf("reader-1", "reader-2"),
        storageWriters = listOf("writer-1", "writer-2"),
        ignorePatterns = listOf("pattern-1", "pattern-2"),
        provenanceStorage = ProvenanceStorageConfiguration(
            fileStorage = ortFileStorageConfiguration
        )
    )

    val ortRepositoryProvenance = RepositoryProvenance(ortPkg.vcsProcessed, ortPkg.vcsProcessed.revision)

    val ortProvenanceResolutionResult = ProvenanceResolutionResult(
        id = ortPkgIdentifier,
        packageProvenance = ortRepositoryProvenance
    )

    val ortScanResult = ScanResult(
        provenance = ortRepositoryProvenance,
        scanner = ScannerDetails(
            name = "name",
            version = "version",
            configuration = "configuration"
        ),
        summary = ScanSummary(
            startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            packageVerificationCode = "",
            licenseFindings = sortedSetOf(),
            copyrightFindings = sortedSetOf(),
            issues = listOf(ortIssue)
        ),
        additionalData = mapOf("data-1" to "value-1")
    )

    val ortScannerRun = ScannerRun(
        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        environment = ortEnvironment,
        config = ortScannerConfiguration,
        provenances = setOf(ortProvenanceResolutionResult),
        scanResults = setOf(ortScanResult)
    )

    val ortResult = OrtResult(
        repository = ortRepository,
        analyzer = ortAnalyzerRun,
        advisor = ortAdvisorRun,
        scanner = ortScannerRun,
        evaluator = null,
        labels = mapOf("label key" to "label value")
    )
}
