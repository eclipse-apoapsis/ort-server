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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File
import java.net.URI

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

import org.ossreviewtoolkit.model.AdvisorCapability as OrtAdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails as OrtAdvisorDetails
import org.ossreviewtoolkit.model.AdvisorRecord as OrtAdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult as OrtAdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun as OrtAdvisorRun
import org.ossreviewtoolkit.model.AdvisorSummary as OrtAdvisorSummary
import org.ossreviewtoolkit.model.AnalyzerResult as OrtAnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun as OrtAnalyzerRun
import org.ossreviewtoolkit.model.DependencyGraph as OrtDependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphNode as OrtDependencyGraphNode
import org.ossreviewtoolkit.model.Hash as OrtHash
import org.ossreviewtoolkit.model.HashAlgorithm as OrtHashAlgorithm
import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package as OrtPackage
import org.ossreviewtoolkit.model.Project as OrtProject
import org.ossreviewtoolkit.model.RemoteArtifact as OrtRemoteArtifact
import org.ossreviewtoolkit.model.Repository as OrtRepository
import org.ossreviewtoolkit.model.RepositoryProvenance as OrtRepositoryProvenance
import org.ossreviewtoolkit.model.RootDependencyIndex as OrtRootDependencyIndex
import org.ossreviewtoolkit.model.ScanResult as OrtScanResult
import org.ossreviewtoolkit.model.ScanSummary as OrtScanSummary
import org.ossreviewtoolkit.model.ScannerDetails as OrtScannerDetails
import org.ossreviewtoolkit.model.ScannerRun as OrtScannerRun
import org.ossreviewtoolkit.model.Severity as OrtSeverity
import org.ossreviewtoolkit.model.VcsInfo as OrtVcsInfo
import org.ossreviewtoolkit.model.VcsType.Companion as OrtVcsType
import org.ossreviewtoolkit.model.Vulnerability as OrtVulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference as OrtVulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration as OrtAdvisorConfiguration
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration as OrtAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration as OrtFileArchiverConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration as OrtFileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileStorageConfiguration as OrtFileStorageConfiguration
import org.ossreviewtoolkit.model.config.GitHubDefectsConfiguration as OrtGithubDefectsConfiguration
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration as OrtLocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.NexusIqConfiguration as OrtNexusIqConfiguration
import org.ossreviewtoolkit.model.config.OsvConfiguration as OrtOsvConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration as OrtPackageManagerConfiguration
import org.ossreviewtoolkit.model.config.ProvenanceStorageConfiguration as OrtProvenanceStorageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration as OrtRepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration as OrtScannerConfiguration
import org.ossreviewtoolkit.model.config.StorageType
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration as OrtVulnerableCodeConfiguration
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.DependencyGraph
import org.ossreviewtoolkit.server.model.runs.DependencyGraphNode
import org.ossreviewtoolkit.server.model.runs.DependencyGraphRoot
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue as OrtServerIssue
import org.ossreviewtoolkit.server.model.runs.Package
import org.ossreviewtoolkit.server.model.runs.PackageManagerConfiguration
import org.ossreviewtoolkit.server.model.runs.Project
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorResult
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.advisor.GithubDefectsConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.NexusIqConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.OsvConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.Vulnerability
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerabilityReference
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerableCodeConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileArchiveConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileBasedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.LocalFileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.NestedProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.NestedProvenanceScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.RepositoryProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.ScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.ScanSummary
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerDetail
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.Environment as OrtEnvironment

private const val TIME_STAMP_SECONDS = 1678119934L

class OrtServerMappingsTest : WordSpec({
    "OrtServerMappings" should {
        "map ORT server objects to ORT objects" {
            // Initialization of ORT server objects.
            val repository = Repository(
                id = 1L,
                organizationId = 1L,
                productId = 1L,
                type = RepositoryType.GIT,
                url = "https://github.com/org/repo.git"
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

            val ortRun = OrtRun(
                id = 1L,
                index = 1L,
                repositoryId = repository.id,
                revision = "abc123",
                createdAt = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                jobs = JobConfigurations(),
                status = OrtRunStatus.CREATED,
                mapOf("label key" to "label value")
            )

            val analyzerJob = AnalyzerJob(
                id = 1L,
                ortRunId = ortRun.id,
                createdAt = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                startedAt = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                finishedAt = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                configuration = AnalyzerJobConfiguration(),
                status = JobStatus.FINISHED,
                repositoryUrl = repository.url,
                repositoryRevision = ortRun.revision
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
                identifier = Identifier(
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
                    type = RepositoryType.GIT,
                    url = "git@github.com/org/project.git",
                    revision = "",
                    path = ""
                ),
                vcsProcessed = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "https://github.com/org.project.git",
                    revision = "abc123",
                    path = ""
                ),
                homepageUrl = "https://example-homepage.com",
                scopeNames = setOf("compile")
            )

            val pkgIdentifier = Identifier(
                type = "Maven",
                namespace = "com.example",
                name = "package",
                version = "1.0"
            )

            val pkg = Package(
                identifier = pkgIdentifier,
                purl = "Maven:com.example:package:1.0",
                cpe = "cpe:example",
                authors = setOf("Author One", "Author Two"),
                declaredLicenses = setOf("Eclipse Public License 1.0"),
                description = "Example description",
                homepageUrl = "https://package-homepage.com",
                binaryArtifact = RemoteArtifact(
                    url = "https://repo.com/package-1.0.jar",
                    hashValue = "123456",
                    hashAlgorithm = "SHA-1"
                ),
                sourceArtifact = RemoteArtifact(
                    url = "https://repo.com/package-1.0-sources.jar",
                    hashValue = "654321",
                    hashAlgorithm = "SHA-1"
                ),
                vcs = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "git://github.com/org/package.git",
                    revision = "1.0",
                    path = ""
                ),
                vcsProcessed = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "https://github.com/org/package.git",
                    revision = "1.0",
                    path = ""
                ),
                isMetadataOnly = true,
                isModified = true
            )

            val issue = OrtServerIssue(
                timestamp = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                source = "tool-x",
                message = "An issue occured.",
                severity = "ERROR"
            )

            val dependencyGraph = DependencyGraph(
                packages = listOf(pkgIdentifier),
                nodes = listOf(DependencyGraphNode(0, 0, "DYNAMIC", emptyList())),
                edges = emptyList(),
                scopes = mapOf(
                    "com.example:project:1.0:compile" to listOf(DependencyGraphRoot(0, 0))
                )
            )

            val analyzerRun = AnalyzerRun(
                id = 1L,
                analyzerJobId = analyzerJob.id,
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                environment = environment,
                config = analyzerConfiguration,
                projects = setOf(project),
                packages = setOf(pkg),
                issues = mapOf(pkgIdentifier to listOf(issue)),
                dependencyGraphs = mapOf("Maven" to dependencyGraph)
            )

            val vulnerableCodeConfiguration = VulnerableCodeConfiguration(
                serverUrl = "https://vulnerablecode.com"
            )

            val osvConfiguration = OsvConfiguration(
                serverUrl = "https://osv.com"
            )

            val githubDefectsConfiguration = GithubDefectsConfiguration(
                endpointUrl = "https://github.com",
                labelFilter = listOf("filter-1", "filter-2"),
                maxNumberOfIssuesPerRepository = 5,
                parallelRequests = 2
            )

            val nexusIqConfiguration = NexusIqConfiguration(
                serverUrl = "https://nexusiq.com",
                browseUrl = "https://nexusiq.com/browse"
            )

            val advisorConfiguration = AdvisorConfiguration(
                osvConfiguration = osvConfiguration,
                githubDefectsConfiguration = githubDefectsConfiguration,
                nexusIqConfiguration = nexusIqConfiguration,
                vulnerableCodeConfiguration = vulnerableCodeConfiguration,
                options = emptyMap()
            )

            val vulnerability = Vulnerability(
                externalId = "CVE-2023-0001",
                summary = "Example summary.",
                description = "Example description.",
                references = listOf(
                    VulnerabilityReference(
                        url = "http://cve.mitre.org",
                        scoringSystem = "CVSS3",
                        severity = "5.5"
                    )
                )
            )

            val advisorResult = AdvisorResult(
                advisorName = "VulnerableCode",
                capabilities = listOf("VULNERABILITIES"),
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                issues = listOf(issue),
                defects = emptyList(),
                vulnerabilities = listOf(vulnerability)

            )

            val advisorRun = AdvisorRun(
                id = 1L,
                advisorJobId = 1L,
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                environment = environment,
                config = advisorConfiguration,
                advisorRecords = mapOf(pkgIdentifier to listOf(advisorResult))
            )

            val fileStorageConfiguration = FileStorageConfiguration(
                localFileStorage = LocalFileStorageConfiguration(
                    directory = "/path/to/storage",
                    compression = true
                )
            )

            val scannerConfiguration = ScannerConfiguration(
                skipConcluded = true,
                archive = FileArchiveConfiguration(
                    enabled = true,
                    fileStorage = fileStorageConfiguration
                ),
                createMissingArchives = true,
                detectedLicenseMappings = mapOf("license-1" to "spdx-license-1", "license-2" to "spdx-license-2"),
                options = mapOf("scanner-1" to mapOf("option-key-1" to "option-value-1")),
                storages = mapOf(
                    "local" to FileBasedStorageConfiguration(fileStorageConfiguration, "PROVENANCE_BASED")
                ),
                storageReaders = listOf("reader-1", "reader-2"),
                storageWriters = listOf("writer-1", "writer-2"),
                ignorePatterns = listOf("pattern-1", "pattern-2"),
                provenanceStorage = ProvenanceStorageConfiguration(
                    fileStorage = fileStorageConfiguration
                )
            )

            val repositoryProvenance = RepositoryProvenance(pkg.vcsProcessed, pkg.vcsProcessed.revision)

            val scanResult = ScanResult(
                provenance = repositoryProvenance,
                scanner = ScannerDetail(
                    name = "name",
                    version = "version",
                    configuration = "configuration"
                ),
                summary = ScanSummary(
                    startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                    endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                    packageVerificationCode = "package-verification-code",
                    licenseFindings = emptySet(),
                    copyrightFindings = emptySet(),
                    issues = listOf(issue)
                ),
                additionalData = mapOf("data-1" to "value-1")
            )

            val nestedProvenanceScanResult = NestedProvenanceScanResult(
                nestedProvenance = NestedProvenance(
                    root = repositoryProvenance,
                    subRepositories = emptyMap()
                ),
                scanResults = mapOf(repositoryProvenance to listOf(scanResult))
            )

            val scannerRun = ScannerRun(
                id = 1L,
                scannerJobId = 1L,
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                environment = environment,
                config = scannerConfiguration,
                scanResults = mapOf(pkg.identifier to listOf(nestedProvenanceScanResult))
            )

            // Initialization of ORT objects.
            val ortRepository = OrtRepository(
                vcs = OrtVcsInfo(
                    type = OrtVcsType.GIT,
                    url = "https://github.com/org/repo.git",
                    revision = "abc123",
                    path = ""
                ),
                nestedRepositories = emptyMap(),
                config = OrtRepositoryConfiguration()
            )

            val ortEnvironment = OrtEnvironment(
                os = "Linux",
                ortVersion = "def456",
                javaVersion = "17",
                processors = 8,
                maxMemory = 12884901888L,
                variables = mapOf("JAVA_HOME" to "/opt/java/openjdk"),
                toolVersions = emptyMap()
            )

            val ortAnalyzerConfiguration = OrtAnalyzerConfiguration(
                allowDynamicVersions = false,
                enabledPackageManagers = listOf("Maven", "Gradle"),
                disabledPackageManagers = listOf("NPM"),
                packageManagers = mapOf(
                    "DotNet" to OrtPackageManagerConfiguration(options = mapOf("directDependenciesOnly" to "true")),
                    "NPM" to OrtPackageManagerConfiguration(options = mapOf("legacyPeerDeps" to "true")),
                    "NuGet" to OrtPackageManagerConfiguration(options = mapOf("directDependenciesOnly" to "true"))
                )
            )

            val ortProject = OrtProject(
                id = OrtIdentifier(
                    type = "Maven",
                    namespace = "com.example",
                    name = "project",
                    version = "1.0"
                ),
                cpe = "cpe:example",
                definitionFilePath = "pom.xml",
                authors = setOf("Author One", "Author Two"),
                declaredLicenses = setOf("The MIT License", "Eclipse Public License 1.0"),
                vcs = OrtVcsInfo(
                    type = OrtVcsType.GIT,
                    url = "git@github.com/org/project.git",
                    revision = "",
                    path = ""
                ),
                vcsProcessed = OrtVcsInfo(
                    type = OrtVcsType.GIT,
                    url = "https://github.com/org.project.git",
                    revision = "abc123",
                    path = ""
                ),
                homepageUrl = "https://example-homepage.com",
                scopeNames = sortedSetOf("compile")
            )

            val ortPkgIdentifier = OrtIdentifier(
                type = "Maven",
                namespace = "com.example",
                name = "package",
                version = "1.0"
            )

            val ortPkg = OrtPackage(
                id = ortPkgIdentifier,
                purl = "Maven:com.example:package:1.0",
                cpe = "cpe:example",
                authors = setOf("Author One", "Author Two"),
                declaredLicenses = setOf("Eclipse Public License 1.0"),
                description = "Example description",
                homepageUrl = "https://package-homepage.com",
                binaryArtifact = OrtRemoteArtifact(
                    url = "https://repo.com/package-1.0.jar",
                    hash = OrtHash(
                        value = "123456",
                        algorithm = OrtHashAlgorithm.SHA1
                    )
                ),
                sourceArtifact = OrtRemoteArtifact(
                    url = "https://repo.com/package-1.0-sources.jar",
                    hash = OrtHash(
                        value = "654321",
                        algorithm = OrtHashAlgorithm.SHA1
                    )
                ),
                vcs = OrtVcsInfo(
                    type = OrtVcsType.GIT,
                    url = "git://github.com/org/package.git",
                    revision = "1.0",
                    path = ""
                ),
                vcsProcessed = OrtVcsInfo(
                    type = OrtVcsType.GIT,
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
                severity = OrtSeverity.ERROR
            )

            val ortDependencyGraph = OrtDependencyGraph(
                packages = listOf(ortPkgIdentifier),
                nodes = listOf(OrtDependencyGraphNode(0)),
                edges = emptyList(),
                scopes = mapOf(
                    "com.example:project:1.0:compile" to listOf(OrtRootDependencyIndex(0))
                )
            )

            val ortAnalyzerRun = OrtAnalyzerRun(
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                environment = ortEnvironment,
                config = ortAnalyzerConfiguration,
                result = OrtAnalyzerResult(
                    projects = setOf(ortProject),
                    packages = setOf(ortPkg),
                    issues = mapOf(ortPkgIdentifier to listOf(ortIssue)),
                    dependencyGraphs = mapOf("Maven" to ortDependencyGraph)
                )
            )

            val ortOsvConfiguration = OrtOsvConfiguration(
                serverUrl = "https://osv.com"
            )

            val ortGithubDefectsConfiguration = OrtGithubDefectsConfiguration(
                token = null,
                endpointUrl = "https://github.com",
                labelFilter = listOf("filter-1", "filter-2"),
                maxNumberOfIssuesPerRepository = 5,
                parallelRequests = 2
            )

            val ortVulnerableCodeConfiguration = OrtVulnerableCodeConfiguration(
                serverUrl = "https://vulnerablecode.com",
                apiKey = null
            )

            val ortNexusIqConfiguration = OrtNexusIqConfiguration(
                serverUrl = "https://nexusiq.com",
                browseUrl = "https://nexusiq.com/browse",
                username = null,
                password = null
            )

            val ortAdvisorConfiguration = OrtAdvisorConfiguration(
                osv = ortOsvConfiguration,
                gitHubDefects = ortGithubDefectsConfiguration,
                vulnerableCode = ortVulnerableCodeConfiguration,
                nexusIq = ortNexusIqConfiguration,
                options = emptyMap()
            )

            val ortAdvisorRecord = OrtAdvisorRecord(
                advisorResults = sortedMapOf(
                    ortPkgIdentifier to listOf(
                        OrtAdvisorResult(
                            advisor = OrtAdvisorDetails(
                                name = "VulnerableCode",
                                capabilities = enumSetOf(OrtAdvisorCapability.VULNERABILITIES)
                            ),
                            summary = OrtAdvisorSummary(
                                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                                issues = listOf(ortIssue)
                            ),
                            defects = emptyList(),
                            vulnerabilities = listOf(
                                OrtVulnerability(
                                    id = "CVE-2023-0001",
                                    summary = "Example summary.",
                                    description = "Example description.",
                                    references = listOf(
                                        OrtVulnerabilityReference(
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

            val ortAdvisorRun = OrtAdvisorRun(
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                environment = ortEnvironment,
                config = ortAdvisorConfiguration,
                results = ortAdvisorRecord
            )

            val ortFileStorageConfiguration = OrtFileStorageConfiguration(
                localFileStorage = OrtLocalFileStorageConfiguration(
                    directory = File("/path/to/storage"),
                    compression = true
                )
            )

            val ortScannerConfiguration = OrtScannerConfiguration(
                skipConcluded = true,
                archive = OrtFileArchiverConfiguration(
                    enabled = true,
                    fileStorage = ortFileStorageConfiguration
                ),
                createMissingArchives = true,
                detectedLicenseMapping = mapOf("license-1" to "spdx-license-1", "license-2" to "spdx-license-2"),
                options = mapOf("scanner-1" to mapOf("option-key-1" to "option-value-1")),
                storages = mapOf(
                    "local" to OrtFileBasedStorageConfiguration(
                        backend = ortFileStorageConfiguration,
                        type = StorageType.PROVENANCE_BASED
                    )
                ),
                storageReaders = listOf("reader-1", "reader-2"),
                storageWriters = listOf("writer-1", "writer-2"),
                ignorePatterns = listOf("pattern-1", "pattern-2"),
                provenanceStorage = OrtProvenanceStorageConfiguration(
                    fileStorage = ortFileStorageConfiguration
                )
            )

            val ortRepositoryProvenance = OrtRepositoryProvenance(ortPkg.vcsProcessed, ortPkg.vcsProcessed.revision)

            val ortScanResult = OrtScanResult(
                provenance = ortRepositoryProvenance,
                scanner = OrtScannerDetails(
                    name = "name",
                    version = "version",
                    configuration = "configuration"
                ),
                summary = OrtScanSummary(
                    startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                    endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                    packageVerificationCode = "",
                    licenseFindings = sortedSetOf(),
                    copyrightFindings = sortedSetOf(),
                    issues = listOf(ortIssue)
                ),
                additionalData = mapOf("data-1" to "value-1")
            )

            val ortScannerRun = OrtScannerRun(
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS).toJavaInstant(),
                environment = ortEnvironment,
                config = ortScannerConfiguration,
                scanResults = sortedMapOf(ortPkgIdentifier to listOf(ortScanResult))
            )

            val ortResult = OrtResult(
                repository = ortRepository,
                analyzer = ortAnalyzerRun,
                advisor = ortAdvisorRun,
                scanner = ortScannerRun,
                evaluator = null,
                labels = emptyMap()
            )

            val mappedOrtResult = ortRun.mapToOrt(
                repository = repository.mapToOrt(ortRun.revision),
                analyzerRun = analyzerRun.mapToOrt(),
                advisorRun = advisorRun.mapToOrt(),
                scannerRun = scannerRun.mapToOrt()
            )

            mappedOrtResult shouldBe ortResult
        }
    }
})
