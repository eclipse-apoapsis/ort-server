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

package org.eclipse.apoapsis.ortserver.shared.orttestdata

import java.io.File
import java.net.URI

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphNode
import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ResolvedConfiguration
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.FileStorageConfiguration
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.config.SnippetChoices
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason
import org.ossreviewtoolkit.model.config.snippet.Choice
import org.ossreviewtoolkit.model.config.snippet.Given
import org.ossreviewtoolkit.model.config.snippet.Provenance
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

object OrtTestData {
    const val TIME_STAMP_SECONDS = 1678119934L

    val pathExclude = PathExclude(
        pattern = "excluded/**",
        reason = PathExcludeReason.EXAMPLE_OF,
        comment = "Test path exclude."
    )

    val licenseFindingCuration = LicenseFindingCuration(
        path = "file1",
        startLines = listOf(1),
        lineCount = 2,
        detectedLicense = "LicenseRef-detected1".toSpdx(),
        concludedLicense = "LicenseRef-detected1-concluded".toSpdx(),
        reason = LicenseFindingCurationReason.INCORRECT,
        comment = "Test license finding curation."
    )

    val spdxLicenseChoice = SpdxLicenseChoice(
        given = "LicenseRef-a OR LicenseRef-b".toSpdx(),
        choice = "LicenseRef-b".toSpdx()
    )

    val snippetChoices = SnippetChoices(
        provenance = Provenance("https://example.org/provenance-url"),
        choices = listOf(
            SnippetChoice(
                Given(TextLocation("source.txt", 1, 10)),
                Choice(
                    "pkg:github/package-url/purl-spec@244fd47e07d1004",
                    SnippetChoiceReason.ORIGINAL_FINDING,
                    "A comment"
                )
            )
        )
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

    val pkgCuration = PackageCuration(
        id = pkgIdentifier,
        data = PackageCurationData(
            comment = "comment",
            purl = "purl",
            cpe = "cpe",
            authors = setOf("author 1", "author 2"),
            concludedLicense = "LicenseRef-concluded".toSpdx(),
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
            declaredLicenseMapping = mapOf(
                "LicenseRef-toBeMapped1" to "LicenseRef-mapped1".toSpdx(),
                "LicenseRef-toBeMapped2" to "LicenseRef-mapped2".toSpdx()
            )
        )
    )

    val issue = Issue(
        timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        source = "tool-x",
        message = "An issue occurred.",
        severity = Severity.ERROR
    )

    val vulnerability = Vulnerability(
        id = "CVE-2023-0001",
        summary = "Example summary.",
        description = "Example description.",
        references = listOf(
            VulnerabilityReference(
                url = URI.create("http://cve.example.org"),
                scoringSystem = "CVSS3",
                severity = "MEDIUM",
                score = 5.5f,
                vector = "CVSS:3.0/AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:N/A:H"
            )
        )
    )

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
                        message = issue.message,
                        reason = IssueResolutionReason.SCANNER_ISSUE,
                        comment = "Test issue resolution."
                    )
                ),
                ruleViolations = listOf(
                    RuleViolationResolution(
                        message = ".*",
                        reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                        comment = "Test rule violation resolution."
                    )
                ),
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = vulnerability.id,
                        reason = VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
                        comment = "Test vulnerability resolution."
                    )
                )
            ),
            curations = Curations(
                packages = listOf(pkgCuration),
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
            ),
            snippetChoices = listOf(snippetChoices)
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
        ),
        skipExcluded = false
    )

    val project = Project(
        id = Identifier("Maven:com.example:project:1.0"),
        cpe = "cpe:example",
        definitionFilePath = "pom.xml",
        authors = setOf("Author One", "Author Two"),
        declaredLicenses = setOf(
            "LicenseRef-declared",
            "LicenseRef-toBeMapped1",
            "LicenseRef-toBeMapped2",
            "LicenseRef-unmapped1",
            "LicenseRef-unmapped2"
        ),
        declaredLicensesProcessed = ProcessedDeclaredLicense(
            spdxExpression = "LicenseRef-declared OR LicenseRef-mapped1 OR LicenseRef-mapped2".toSpdx(),
            mapped = mapOf(
                "LicenseRef-toBeMapped1" to "LicenseRef-mapped1".toSpdx(),
                "LicenseRef-toBeMapped2" to "LicenseRef-mapped2".toSpdx()
            ),
            unmapped = setOf("LicenseRef-unmapped1", "LicenseRef-unmapped2")
        ),
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
        description = "description",
        homepageUrl = "https://example.org/project",
        scopeNames = setOf("compile")
    )

    val pkg = Package(
        id = pkgIdentifier,
        purl = "Maven:com.example:package:1.0",
        cpe = "cpe:example",
        authors = setOf("Author One", "Author Two"),
        declaredLicenses = setOf(
            "LicenseRef-declared",
            "LicenseRef-package-declared",
            "LicenseRef-toBeMapped1",
            "LicenseRef-toBeMapped2",
            "LicenseRef-unmapped1",
            "LicenseRef-unmapped2"
        ),
        declaredLicensesProcessed = ProcessedDeclaredLicense(
            spdxExpression = (
                    "LicenseRef-declared OR LicenseRef-package-declared OR LicenseRef-mapped1 OR LicenseRef-mapped2"
                    ).toSpdx(),
            mapped = mapOf(
                "LicenseRef-toBeMapped1" to "LicenseRef-mapped1".toSpdx(),
                "LicenseRef-toBeMapped2" to "LicenseRef-mapped2".toSpdx()
            ),
            unmapped = setOf("LicenseRef-unmapped1", "LicenseRef-unmapped2")
        ),
        description = "Example description",
        homepageUrl = "https://example.org/package",
        binaryArtifact = RemoteArtifact(
            url = pkgBinaryArtifactUrl,
            hash = Hash(
                value = "123456",
                algorithm = HashAlgorithm.UNKNOWN
            )
        ),
        sourceArtifact = RemoteArtifact(
            url = pkgSourceArtifactUrl,
            hash = Hash(
                value = "654321",
                algorithm = HashAlgorithm.UNKNOWN
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

    val dependencyGraph = DependencyGraph(
        packages = listOf(pkgIdentifier),
        nodes = listOf(DependencyGraphNode(0)),
        edges = emptySet(),
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

    val advisorConfiguration = AdvisorConfiguration(
        config = mapOf(
            "GitHubDefects" to PluginConfig(
                options = mapOf(
                    "endpointUrl" to "https://github.com/defects",
                    "labelFilter" to "!any",
                    "maxNumberOfIssuesPerRepository" to "5",
                    "parallelRequests" to "2"
                ),
                secrets = mapOf("token" to "tokenValue")
            ),
            "NexusIQ" to PluginConfig(
                options = mapOf(
                    "serverUrl" to "https://example.org/nexus",
                    "browseUrl" to "https://example.org/nexus/browse"
                ),
                secrets = mapOf(
                    "username" to "user",
                    "password" to "pass"
                )
            ),
            "OSV" to PluginConfig(
                options = mapOf("serverUrl" to "https://google.com/osv"),
                secrets = emptyMap()
            ),
            "VulnerableCode" to PluginConfig(
                options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
                secrets = mapOf("apiKey" to "key")
            )
        )
    )

    val advisorResults = sortedMapOf(
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
                vulnerabilities = listOf(vulnerability)
            )
        )
    )

    val advisorRun = AdvisorRun(
        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        environment = environment,
        config = advisorConfiguration,
        results = advisorResults
    )

    val fileStorageConfiguration = FileStorageConfiguration(
        localFileStorage = LocalFileStorageConfiguration(
            directory = File("/path/to/storage"),
            compression = true
        )
    )

    val scannerConfiguration = ScannerConfiguration(
        skipConcluded = true,
        skipExcluded = true,
        archive = null,
        detectedLicenseMapping = mapOf("license-1" to "spdx-license-1", "license-2" to "spdx-license-2"),
        config = mapOf(
            "scanner-1" to PluginConfig(
                options = mapOf("option-key-1" to "option-value-1"),
                secrets = mapOf("secret-key-1" to "secret-value-1")
            ),
            "scanner-2" to PluginConfig(
                options = mapOf("option-key-1" to "option-value-1", "option-key-2" to "option-value-2"),
                secrets = mapOf("secret-key-1" to "secret-value-1", "secret-key-2" to "secret-value-2")
            )
        ),
        storages = null,
        storageReaders = null,
        storageWriters = null,
        ignorePatterns = listOf("pattern-1", "pattern-2"),
        provenanceStorage = null
    )

    val artifactProvenance = ArtifactProvenance(checkNotNull(pkgCuration.data.sourceArtifact))

    val provenanceResolutionResult = ProvenanceResolutionResult(
        id = pkgIdentifier,
        packageProvenance = artifactProvenance
    )

    val scanResult = ScanResult(
        provenance = artifactProvenance,
        scanner = ScannerDetails(
            // This has to be "ScanCode" because the value is currently hardcoded in `OrtServerMappings`.
            name = "ScanCode",
            version = "version",
            configuration = "configuration"
        ),
        summary = ScanSummary(
            startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
            licenseFindings = setOf(
                LicenseFinding(
                    license = "LicenseRef-detected-excluded",
                    location = TextLocation(path = "excluded/file", startLine = 1, endLine = 2)
                ),
                LicenseFinding(
                    license = "LicenseRef-detected1",
                    location = TextLocation(path = "file1", startLine = 1, endLine = 2)
                ),
                LicenseFinding(
                    license = "LicenseRef-detected2",
                    location = TextLocation(path = "file2", startLine = 1, endLine = 2)
                ),
                LicenseFinding(
                    license = "LicenseRef-detected3",
                    location = TextLocation(path = "file3", startLine = 1, endLine = 2)
                )
            ),
            copyrightFindings = setOf(),
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
        scanners = mapOf(provenanceResolutionResult.id to setOf(scanResult.scanner.name, "TestScanner"))
    )

    val resolvedConfiguration = ResolvedConfiguration(
        packageConfigurations = listOf(
            PackageConfiguration(
                id = pkgIdentifier,
                sourceArtifactUrl = pkgCuratedSourceArtifactUrl,
                pathExcludes = listOf(pathExclude),
                licenseFindingCurations = listOf(licenseFindingCuration)
            )
        ),
        packageCurations = listOf(
            ResolvedPackageCurations(
                provider = ResolvedPackageCurations.Provider(
                    id = ResolvedPackageCurations.REPOSITORY_CONFIGURATION_PROVIDER_ID
                ),
                curations = listOf(pkgCuration)
            )
        ),
        resolutions = Resolutions(
            issues = listOf(
                IssueResolution(
                    message = issue.message,
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "Test issue resolution."
                )
            ),
            ruleViolations = listOf(
                RuleViolationResolution(
                    message = ".*",
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "Test rule violation resolution."
                )
            ),
            vulnerabilities = listOf(
                VulnerabilityResolution(
                    id = vulnerability.id,
                    reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                    comment = "Test vulnerability resolution."
                )
            )
        )
    )

    val evaluatorRun = EvaluatorRun(
        startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
        violations = listOf(
            RuleViolation(
                rule = "rule",
                message = "message",
                severity = Severity.ERROR,
                howToFix = "howToFix",
                pkg = Identifier("Maven:com.example:package:1.0"),
                license = SpdxSingleLicenseExpression.parse("LicenseRef-detected1-concluded"),
                licenseSource = LicenseSource.CONCLUDED
            )
        ),
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
