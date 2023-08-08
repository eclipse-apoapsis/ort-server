/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.analyzer

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.toSpdx

private val projectDir = File("src/test/resources/mavenProject/").absoluteFile

class AnalyzerRunnerTest : WordSpec({
    val runner = AnalyzerRunner()

    "run" should {
        "return the correct repository information" {
            val result = runner.run(projectDir, AnalyzerJobConfiguration()).repository

            result.config shouldBe RepositoryConfiguration(
                analyzer = RepositoryAnalyzerConfiguration(
                    allowDynamicVersions = true,
                    skipExcluded = true
                ),
                excludes = Excludes(
                    paths = listOf(
                        PathExclude(
                            pattern = "**/path",
                            reason = PathExcludeReason.EXAMPLE_OF,
                            comment = "This is only an example path exclude."
                        )
                    ),
                    scopes = listOf(
                        ScopeExclude(
                            pattern = "test.*",
                            reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                            comment = "This is only an example scope exclude."
                        )
                    )
                ),
                resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = "Error message .*",
                            reason = IssueResolutionReason.SCANNER_ISSUE,
                            comment = "This is only an example issue resolution."
                        )
                    ),
                    ruleViolations = listOf(
                        RuleViolationResolution(
                            message = "Rule Violation .*",
                            reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                            comment = "This is only an example rule violation resolution."
                        )
                    ),
                    vulnerabilities = listOf(
                        VulnerabilityResolution(
                            id = "CVE-ID-1234",
                            reason = VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
                            comment = "This is only an example vulnerability resolution."
                        )
                    )
                ),
                curations = Curations(
                    packages = listOf(
                        PackageCuration(
                            id = Identifier("Maven:org.example:name:1.0.0"),
                            data = PackageCurationData(
                                comment = "This is only an example curation.",
                                vcs = VcsInfoCurationData(
                                    type = VcsType.GIT,
                                    url = "https://example.org/name.git",
                                    revision = "123456789"
                                )
                            )
                        )
                    ),
                    licenseFindings = listOf(
                        LicenseFindingCuration(
                            path = "README.md",
                            lineCount = 1,
                            detectedLicense = "GPL-1.0-or-later".toSpdx(),
                            concludedLicense = "NONE".toSpdx(),
                            reason = LicenseFindingCurationReason.DOCUMENTATION_OF,
                            comment = "This is only an example license finding curation."
                        )
                    )
                ),
                packageConfigurations = listOf(
                    PackageConfiguration(
                        id = Identifier("Maven:org.example:name:1.0.0"),
                        sourceArtifactUrl = "https://example.org/name-1.0.0-sources.jar"
                    )
                ),
                licenseChoices = LicenseChoices(
                    repositoryLicenseChoices = listOf(
                        SpdxLicenseChoice(
                            given = "LicenseRef-a OR LicenseRef-b".toSpdx(),
                            choice = "LicenseRef-b".toSpdx()
                        )
                    ),
                    packageLicenseChoices = listOf(
                        PackageLicenseChoice(
                            packageId = Identifier("Maven:org.example:name:1.0.0"),
                            licenseChoices = listOf(
                                SpdxLicenseChoice(
                                    given = "LicenseRef-a OR LicenseRef-b".toSpdx(),
                                    choice = "LicenseRef-a".toSpdx()
                                )
                            )
                        )
                    )
                )
            )

            result.vcs shouldNotBe VcsInfo.EMPTY
            result.vcsProcessed shouldNotBe VcsInfo.EMPTY
            result.nestedRepositories should beEmpty()
        }

        "return an unmanaged project for a directory with only an empty subdirectory" {
            val inputDir = createOrtTempDir().resolve("project")
            inputDir.resolve("subdirectory").safeMkdirs()

            val result = runner.run(inputDir, AnalyzerJobConfiguration()).analyzer?.result

            result.shouldNotBeNull()
            result.projects.map { it.id } should containExactly(Identifier("Unmanaged::project"))
        }
    }
})
