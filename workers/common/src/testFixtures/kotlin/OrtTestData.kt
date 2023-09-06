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
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ResolvedConfiguration
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileStorageConfiguration
import org.ossreviewtoolkit.model.config.GitHubDefectsConfiguration
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.NexusIqConfiguration
import org.ossreviewtoolkit.model.config.OsvConfiguration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.config.StorageType
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.toSpdx

object OrtTestData {
    const val TIME_STAMP_SECONDS = 1678119934L

    val pathExclude = PathExclude(
        pattern = "**/path",
        reason = PathExcludeReason.EXAMPLE_OF,
        comment = "Test path exclude."
    )

    val licenseFindingCuration = LicenseFindingCuration(
        path = "**/path",
        startLines = listOf(8, 9),
        lineCount = 3,
        detectedLicense = "LicenseRef-a".toSpdx(),
        concludedLicense = "LicenseRef-b".toSpdx(),
        reason = LicenseFindingCurationReason.DOCUMENTATION_OF,
        comment = "Test license finding curation."
    )

    val spdxLicenseChoice = SpdxLicenseChoice(
        given = "LicenseRef-a OR LicenseRef-b".toSpdx(),
        choice = "LicenseRef-b".toSpdx()
    )

    const val projectRepositoryUrl = "git@example.org/project.git"
    const val projectProcessedRepositoryUrl = "https://example.org/project.git"
    const val projectRevision = "project123"

    val pkgIdentifier = Identifier("Maven:com.example:package:1.0")
    const val pkgBinaryArtifactUrl = "https://example.org/binary.zip"
    const val pkgCuratedBinaryArtifactUrl = "https://example.org/binary-curated.zip"
    const val pkgSourceArtifactUrl = "https://example.org/source.zip"
    const val pkgCuratedSourceArtifactUrl = "https://example.org/source-curated.zip"
    const val pkgRepositoryUrl = "git@example.org/package.git"
    const val pkgProcessedRepositoryUrl = "https://example.org/package.git"
    const val pkgCuratedRepositoryUrl = "https://example.org/package-curated.git"
    const val pkgRevision = "package123"
    const val pkgCuratedRevision = "package123-curated"
    const val pkgCuratedPath = "path"

    val repository = Repository(
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = projectProcessedRepositoryUrl,
            revision = projectRevision,
            path = ""
        ),
        nestedRepositories = emptyMap(),
        config = RepositoryConfiguration(
            analyzer = RepositoryAnalyzerConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = listOf("Gradle", "Maven"),
                disabledPackageManagers = listOf("NPM"),
                packageManagers = mapOf("Gradle" to PackageManagerConfiguration(listOf("Maven"))),
                skipExcluded = true
            ),
            excludes = Excludes(
                paths = listOf(pathExclude),
                scopes = listOf(ScopeExclude("test", ScopeExcludeReason.TEST_DEPENDENCY_OF, "Test scope exclude."))
            ),
            resolutions = Resolutions(
                issues = listOf(
                    IssueResolution(
                        message = "Error .*",
                        reason = IssueResolutionReason.SCANNER_ISSUE,
                        comment = "Test issue resolution."
                    )
                ),
                ruleViolations = listOf(
                    RuleViolationResolution(
                        message = "Rule 1",
                        reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                        comment = "Test rule violation resolution."
                    )
                ),
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = "CVE-ID-1234",
                        reason = VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
                        comment = "Test vulnerability resolution."
                    )
                )
            ),
            curations = Curations(
                packages = listOf(
                    PackageCuration(
                        id = pkgIdentifier,
                        data = PackageCurationData(
                            comment = "Test curation data.",
                            purl = "Maven:com.example:package:1.0",
                            concludedLicense = "LicenseRef-a".toSpdx(),
                        )
                    )
                ),
                licenseFindings = listOf(licenseFindingCuration)
            ),
            packageConfigurations = listOf(
                PackageConfiguration(
                    id = pkgIdentifier,
                    sourceArtifactUrl = pkgCuratedSourceArtifactUrl,
                    pathExcludes = listOf(pathExclude),
                    licenseFindingCurations = listOf(licenseFindingCuration)
                )
            ),
            licenseChoices = LicenseChoices(
                repositoryLicenseChoices = listOf(spdxLicenseChoice),
                packageLicenseChoices = listOf(
                    PackageLicenseChoice(
                        packageId = pkgIdentifier,
                        licenseChoices = listOf(spdxLicenseChoice)
                    )
                )
            )
        )
    )

    val environment = Environment(
        os = "Linux",
        ortVersion = "def456",
        javaVersion = "17",
        processors = 8,
        maxMemory = 12884901888L,
        variables = mapOf("JAVA_HOME" to "/opt/java/openjdk"),
        toolVersions = emptyMap()
    )

    val analyzerConfiguration = AnalyzerConfiguration(
        allowDynamicVersions = false,
        enabledPackageManagers = listOf("Maven", "Gradle"),
        disabledPackageManagers = listOf("NPM"),
        packageManagers = mapOf(
            "DotNet" to PackageManagerConfiguration(options = mapOf("directDependenciesOnly" to "true")),
            "NPM" to PackageManagerConfiguration(options = mapOf("legacyPeerDeps" to "true")),
            "NuGet" to PackageManagerConfiguration(options = mapOf("directDependenciesOnly" to "true"))
        )
    )

    val project = Project(
        id = Identifier("Maven:com.example:project:1.0"),
        cpe = "cpe:example",
        definitionFilePath = "pom.xml",
        authors = setOf("Author One", "Author Two"),
        declaredLicenses = setOf("The MIT License", "Eclipse Public License 1.0"),
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = projectRepositoryUrl,
            revision = "",
            path = ""
        ),
        vcsProcessed = VcsInfo(
            type = VcsType.GIT,
            url = projectProcessedRepositoryUrl,
            revision = projectRevision,
            path = ""
        ),
        homepageUrl = "https://example.org/project",
        scopeNames = sortedSetOf("compile")
    )

    val pkg = Package(
        id = pkgIdentifier,
        purl = "Maven:com.example:package:1.0",
        cpe = "cpe:example",
        authors = setOf("Author One", "Author Two"),
        declaredLicenses = setOf("Eclipse Public License 1.0"),
        description = "Example description",
        homepageUrl = "https://example.org/package",
        binaryArtifact = RemoteArtifact(
            url = pkgBinaryArtifactUrl,
            hash = Hash(
                value = "123456",
                algorithm = HashAlgorithm.SHA1
            )
        ),
        sourceArtifact = RemoteArtifact(
            url = pkgSourceArtifactUrl,
            hash = Hash(
                value = "654321",
                algorithm = HashAlgorithm.SHA1
            )
        ),
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = pkgRepositoryUrl,
            revision = pkgRevision,
            path = ""
        ),
        vcsProcessed = VcsInfo(
            type = VcsType.GIT,
            url = pkgProcessedRepositoryUrl,
            revision = pkgRevision,
            path = ""
        ),
        isMetadataOnly = true,
        isModified = true
    )

    val issue = Issue(
        timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        source = "tool-x",
        message = "An issue occurred.",
        severity = Severity.ERROR
    )

    val dependencyGraph = DependencyGraph(
        packages = listOf(pkgIdentifier),
        nodes = listOf(DependencyGraphNode(0)),
        edges = emptyList(),
        scopes = mapOf(
            "com.example:project:1.0:compile" to listOf(RootDependencyIndex(0))
        )
    )

    val analyzerRun = AnalyzerRun(
        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        environment = environment,
        config = analyzerConfiguration,
        result = AnalyzerResult(
            projects = setOf(project),
            packages = setOf(pkg),
            issues = mapOf(pkgIdentifier to listOf(issue)),
            dependencyGraphs = mapOf("Maven" to dependencyGraph)
        )
    )

    val osvConfiguration = OsvConfiguration(
        serverUrl = "https://osv.com"
    )

    val githubDefectsConfiguration = GitHubDefectsConfiguration(
        token = null,
        endpointUrl = "https://github.com",
        labelFilter = listOf("filter-1", "filter-2"),
        maxNumberOfIssuesPerRepository = 5,
        parallelRequests = 2
    )

    val vulnerableCodeConfiguration = VulnerableCodeConfiguration(
        serverUrl = "https://vulnerablecode.com",
        apiKey = null
    )

    val nexusIqConfiguration = NexusIqConfiguration(
        serverUrl = "https://nexusiq.com",
        browseUrl = "https://nexusiq.com/browse",
        username = null,
        password = null
    )

    val advisorConfiguration = AdvisorConfiguration(
        osv = osvConfiguration,
        gitHubDefects = githubDefectsConfiguration,
        vulnerableCode = vulnerableCodeConfiguration,
        nexusIq = nexusIqConfiguration,
        options = emptyMap()
    )

    val advisorRecord = AdvisorRecord(
        advisorResults = sortedMapOf(
            pkgIdentifier to listOf(
                AdvisorResult(
                    advisor = AdvisorDetails(
                        name = "VulnerableCode",
                        capabilities = enumSetOf(AdvisorCapability.VULNERABILITIES)
                    ),
                    summary = AdvisorSummary(
                        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                        issues = listOf(issue)
                    ),
                    defects = emptyList(),
                    vulnerabilities = listOf(
                        Vulnerability(
                            id = "CVE-2023-0001",
                            summary = "Example summary.",
                            description = "Example description.",
                            references = listOf(
                                VulnerabilityReference(
                                    url = URI("http://cve.example.org"),
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

    val advisorRun = AdvisorRun(
        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        environment = environment,
        config = advisorConfiguration,
        results = advisorRecord
    )

    val fileStorageConfiguration = FileStorageConfiguration(
        localFileStorage = LocalFileStorageConfiguration(
            directory = File("/path/to/storage"),
            compression = true
        )
    )

    val scannerConfiguration = ScannerConfiguration(
        skipConcluded = true,
        archive = FileArchiverConfiguration(
            enabled = true,
            fileStorage = fileStorageConfiguration
        ),
        createMissingArchives = true,
        detectedLicenseMapping = mapOf("license-1" to "spdx-license-1", "license-2" to "spdx-license-2"),
        options = mapOf("scanner-1" to mapOf("option-key-1" to "option-value-1")),
        storages = mapOf(
            "local" to FileBasedStorageConfiguration(
                backend = fileStorageConfiguration,
                type = StorageType.PROVENANCE_BASED
            )
        ),
        storageReaders = listOf("reader-1", "reader-2"),
        storageWriters = listOf("writer-1", "writer-2"),
        ignorePatterns = listOf("pattern-1", "pattern-2"),
        provenanceStorage = ProvenanceStorageConfiguration(
            fileStorage = fileStorageConfiguration
        )
    )

    val repositoryProvenance = RepositoryProvenance(pkg.vcsProcessed, pkg.vcsProcessed.revision)

    val provenanceResolutionResult = ProvenanceResolutionResult(
        id = pkgIdentifier,
        packageProvenance = repositoryProvenance
    )

    val scanResult = ScanResult(
        provenance = repositoryProvenance,
        scanner = ScannerDetails(
            // This has to be "ScanCode" because the value is currently hardcoded in `OrtServerMappings`.
            name = "ScanCode",
            version = "version",
            configuration = "configuration"
        ),
        summary = ScanSummary(
            startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            licenseFindings = sortedSetOf(),
            copyrightFindings = sortedSetOf(),
            issues = listOf(issue)
        ),
        additionalData = mapOf("data-1" to "value-1")
    )

    val scannerRun = ScannerRun(
        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        environment = environment,
        config = scannerConfiguration,
        provenances = setOf(provenanceResolutionResult),
        scanResults = setOf(scanResult),
        files = emptySet(),
        scanners = mapOf(provenanceResolutionResult.id to setOf(scanResult.scanner.name))
    )

    val resolvedConfiguration = ResolvedConfiguration(
        packageConfigurations = listOf(
            PackageConfiguration(
                id = pkgIdentifier,
                sourceArtifactUrl = pkgSourceArtifactUrl,
                pathExcludes = listOf(pathExclude),
                licenseFindingCurations = listOf(licenseFindingCuration)
            )
        ),
        packageCurations = listOf(
            ResolvedPackageCurations(
                provider = ResolvedPackageCurations.Provider(id = "name"),
                curations = setOf(
                    PackageCuration(
                        id = pkgIdentifier,
                        data = PackageCurationData(
                            comment = "comment",
                            purl = "purl",
                            cpe = "cpe",
                            authors = setOf("author 1", "author 2"),
                            concludedLicense = "Apache-2.0".toSpdx(),
                            description = "description",
                            homepageUrl = "https://example.org/package-curated",
                            binaryArtifact = RemoteArtifact(
                                url = pkgCuratedBinaryArtifactUrl,
                                hash = Hash.Companion.create("0123456789abcdef0123456789abcdef01234567")
                            ),
                            sourceArtifact = RemoteArtifact(
                                url = pkgCuratedSourceArtifactUrl,
                                hash = Hash.Companion.create("0123456789abcdef0123456789abcdef01234567")
                            ),
                            vcs = VcsInfoCurationData(
                                type = VcsType.GIT,
                                url = pkgCuratedRepositoryUrl,
                                revision = pkgCuratedRevision,
                                path = pkgCuratedPath
                            ),
                            isMetadataOnly = false,
                            isModified = false,
                            declaredLicenseMapping = mapOf("Apache" to "Apache-2.0".toSpdx())
                        )
                    )
                )
            )
        ),
        resolutions = Resolutions(
            issues = listOf(
                IssueResolution(
                    message = "message",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "comment"
                )
            ),
            ruleViolations = listOf(
                RuleViolationResolution(
                    message = "message",
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "comment"
                )
            ),
            vulnerabilities = listOf(
                VulnerabilityResolution(
                    id = "message",
                    reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                    comment = "comment"
                )
            )
        )
    )

    val result = OrtResult(
        repository = repository,
        analyzer = analyzerRun,
        advisor = advisorRun,
        scanner = scannerRun,
        evaluator = null,
        labels = mapOf("label key" to "label value"),
        resolvedConfiguration = resolvedConfiguration
    )
}
