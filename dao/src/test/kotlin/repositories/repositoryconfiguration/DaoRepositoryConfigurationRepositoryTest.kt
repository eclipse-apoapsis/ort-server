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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
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
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation

class DaoRepositoryConfigurationRepositoryTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var repositoryConfigurationRepository: DaoRepositoryConfigurationRepository
    lateinit var fixtures: Fixtures

    var ortRunId = -1L

    beforeEach {
        repositoryConfigurationRepository = dbExtension.fixtures.repositoryConfigurationRepository
        fixtures = Fixtures(dbExtension.db)

        ortRunId = fixtures.ortRun.id
    }

    "create" should {
        "create an entry in the database" {
            val createdRepositoryConfiguration = repositoryConfigurationRepository.create(ortRunId, repositoryConfig)

            val dbEntry = repositoryConfigurationRepository.get(createdRepositoryConfiguration.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe repositoryConfig.copy(id = createdRepositoryConfiguration.id, ortRunId = ortRunId)
        }

        "handle duplicates in license finding curations" {
            val packageConfigurationWithDuplicates = packageConfiguration.copy(
                licenseFindingCurations = listOf(licenseFindingCuration, licenseFindingCuration)
            )
            val config = repositoryConfig.copy(packageConfigurations = listOf(packageConfigurationWithDuplicates))

            val createdRepositoryConfiguration = repositoryConfigurationRepository.create(ortRunId, config)

            val dbEntry = repositoryConfigurationRepository.get(createdRepositoryConfiguration.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe repositoryConfig.copy(id = createdRepositoryConfiguration.id, ortRunId = ortRunId)
        }

        "handle duplicates in path excludes" {
            val packageConfigurationWithDuplicates = packageConfiguration.copy(
                pathExcludes = listOf(pathExclude, pathExclude)
            )
            val config = repositoryConfig.copy(packageConfigurations = listOf(packageConfigurationWithDuplicates))

            val createdRepositoryConfiguration = repositoryConfigurationRepository.create(ortRunId, config)

            val dbEntry = repositoryConfigurationRepository.get(createdRepositoryConfiguration.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe repositoryConfig.copy(id = createdRepositoryConfiguration.id, ortRunId = ortRunId)
        }
    }

    "get" should {
        "return null if the repository configuration does not exist" {
            repositoryConfigurationRepository.get(1L).shouldBeNull()
        }
    }
})

private fun DaoRepositoryConfigurationRepository.create(ortRunId: Long, repositoryConfig: RepositoryConfiguration) =
    create(
        ortRunId = ortRunId,
        analyzerConfig = repositoryConfig.analyzerConfig,
        excludes = repositoryConfig.excludes,
        resolutions = repositoryConfig.resolutions,
        curations = repositoryConfig.curations,
        packageConfigurations = repositoryConfig.packageConfigurations,
        licenseChoices = repositoryConfig.licenseChoices,
        provenanceSnippetChoices = repositoryConfig.provenanceSnippetChoices
    )

private val repositoryAnalyzerConfig = RepositoryAnalyzerConfiguration(
    allowDynamicVersions = true,
    enabledPackageManagers = listOf("Gradle", "Maven"),
    disabledPackageManagers = listOf("NPM", "Yarn"),
    packageManagers = mapOf(
        "Gradle" to PackageManagerConfiguration(
            mustRunAfter = listOf("Maven")
        ),
        "Maven" to PackageManagerConfiguration(
            options = emptyMap()
        )
    ),
    skipExcluded = true
)

private val pathExclude = PathExclude(
    pattern = "**/file.txt",
    reason = "BUILD_TOOL_OF",
    comment = "Test path exclude."
)

private val scopeExclude = ScopeExclude(
    pattern = "scope1",
    reason = "PROVIDED_DEPENDENCY_OF",
    comment = "Test scope exclude."
)

private val issueResolution = IssueResolution(
    message = "issue message",
    reason = "CANT_FIX_ISSUE",
    comment = "Test issue resolution."
)

private val ruleViolationResolution = RuleViolationResolution(
    message = "rule violation message",
    reason = "CANT_FIX_EXCEPTION",
    comment = "Test rule violation resolution."
)

private val vulnerabilityResolution = VulnerabilityResolution(
    externalId = "vulnerability id",
    reason = "INEFFECTIVE_VULNERABILITY",
    comment = "Test vulnerability resolution."
)

private val packageCurationData = PackageCurationData(
    comment = "Test package curation.",
    purl = "pkg:type/namespace/name@version",
    cpe = "cpe:2.3:a:",
    authors = setOf("author 1", "author 2"),
    declaredLicenseMapping = mapOf("license a" to "Apache-2.0"),
    concludedLicense = "license1 OR license2",
    description = "test description",
    homepageUrl = "https://example.org",
    binaryArtifact = RemoteArtifact(
        url = "https://example.org/binary.artifact",
        hashValue = "binaryHashValue",
        hashAlgorithm = "SHA1"
    ),
    sourceArtifact = RemoteArtifact(
        url = "https://example.org/source.artifact",
        hashValue = "sourceHashValue",
        hashAlgorithm = "SHA1"
    ),
    vcs = VcsInfoCurationData(
        type = RepositoryType.GIT,
        url = "https://example.org/curation.git",
        revision = "main",
        path = "path"
    ),
    isMetadataOnly = true,
    isModified = true
)

private val identifier = Identifier(
    type = "type",
    namespace = "namespace",
    name = "name",
    version = "version"
)

private val packageCuration = PackageCuration(
    id = identifier,
    data = packageCurationData
)

private val licenseFindingCuration = LicenseFindingCuration(
    path = "a/path",
    startLines = listOf(7, 8),
    lineCount = 5,
    detectedLicense = "MIT",
    concludedLicense = "license1 OR license2",
    reason = "INCORRECT",
    comment = "Test license finding curation"
)

private val packageConfiguration = PackageConfiguration(
    id = identifier,
    sourceArtifactUrl = "https://example.org/source.artifact.url",
    vcs = VcsMatcher(
        type = RepositoryType.GIT,
        url = "https://example.org/vcs.matcher.url",
        revision = "revision"
    ),
    pathExcludes = listOf(pathExclude),
    licenseFindingCurations = listOf(licenseFindingCuration)
)

private val spdxLicenseChoice = SpdxLicenseChoice(
    given = "LicenseRef-a OR LicenseRef-b",
    choice = "LicenseRef-b"
)

private val packageLicenseChoice = PackageLicenseChoice(
    identifier = identifier,
    licenseChoices = listOf(spdxLicenseChoice)
)

private val provenanceSnippetChoices = ProvenanceSnippetChoices(
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

private val repositoryConfig = RepositoryConfiguration(
    id = -1L,
    ortRunId = -1L,
    analyzerConfig = repositoryAnalyzerConfig,
    excludes = Excludes(
        paths = listOf(pathExclude),
        scopes = listOf(scopeExclude)
    ),
    resolutions = Resolutions(
        issues = listOf(issueResolution),
        ruleViolations = listOf(ruleViolationResolution),
        vulnerabilities = listOf(vulnerabilityResolution)
    ),
    curations = Curations(
        packages = listOf(packageCuration),
        licenseFindings = listOf(licenseFindingCuration)
    ),
    packageConfigurations = listOf(packageConfiguration),
    licenseChoices = LicenseChoices(
        repositoryLicenseChoices = listOf(spdxLicenseChoice),
        packageLicenseChoices = listOf(packageLicenseChoice)
    ),
    provenanceSnippetChoices = listOf(provenanceSnippetChoices)
)
