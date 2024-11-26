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

package org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.tables.resolvedconfiguration.ResolvedConfigurationDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.LicenseFindingCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.PathExclude
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution

class DaoResolvedConfigurationRepositoryTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var resolvedConfigurationRepository: DaoResolvedConfigurationRepository
    lateinit var fixtures: Fixtures

    var ortRunId = -1L

    beforeEach {
        resolvedConfigurationRepository = dbExtension.fixtures.resolvedConfigurationRepository
        fixtures = Fixtures(dbExtension.db)

        ortRunId = fixtures.ortRun.id
    }

    "get" should {
        "return the resolved configuration" {
            dbExtension.db.dbQuery {
                val resolvedConfiguration = ResolvedConfigurationDao.getOrPut(ortRunId)

                resolvedConfigurationRepository.get(resolvedConfiguration.id.value) shouldBe
                        resolvedConfiguration.mapToModel()
            }
        }

        "return null if the resolved configuration does not exist" {
            resolvedConfigurationRepository.get(-1L) shouldBe null
        }
    }

    "getForOrtRun" should {
        "return the resolved configuration for the ORT run" {
            val resolvedConfiguration = dbExtension.db.dbQuery {
                ResolvedConfigurationDao.getOrPut(ortRunId).mapToModel()
            }

            resolvedConfigurationRepository.getForOrtRun(ortRunId) shouldBe resolvedConfiguration
        }

        "return null if the ORT run does not exist" {
            resolvedConfigurationRepository.getForOrtRun(-1L) shouldBe null
        }

        "return null if the resolved configuration does not exist" {
            resolvedConfigurationRepository.getForOrtRun(ortRunId) shouldBe null
        }
    }

    "addPackageConfigurations" should {
        "add the provided package configurations" {
            resolvedConfigurationRepository.addPackageConfigurations(
                ortRunId,
                listOf(packageConfiguration1, packageConfiguration2)
            )

            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.packageConfigurations should containExactlyInAnyOrder(
                packageConfiguration1,
                packageConfiguration2
            )
        }

        "not overwrite previously added package configurations" {
            resolvedConfigurationRepository.addPackageConfigurations(ortRunId, listOf(packageConfiguration1))
            resolvedConfigurationRepository.addPackageConfigurations(ortRunId, listOf(packageConfiguration2))

            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.packageConfigurations should containExactlyInAnyOrder(
                packageConfiguration1,
                packageConfiguration2
            )
        }
    }

    "addPackageCurations" should {
        "add the provided package curations" {
            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1, packageCurations2))

            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.packageCurations should containExactly(packageCurations1, packageCurations2)
        }

        "append to previously added package curations" {
            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1))
            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations2))

            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.packageCurations should containExactly(packageCurations1, packageCurations2)
        }
    }

    "addResolutions" should {
        "add the provided resolutions" {
            resolvedConfigurationRepository.addResolutions(
                ortRunId = ortRunId,
                resolutions = Resolutions(
                    issues = listOf(issueResolution1, issueResolution2),
                    ruleViolations = listOf(ruleViolationResolution1, ruleViolationResolution2),
                    vulnerabilities = listOf(vulnerabilityResolution1, vulnerabilityResolution2)
                )
            )

            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            with(resolvedConfiguration.resolutions) {
                issues should containExactlyInAnyOrder(issueResolution1, issueResolution2)
                ruleViolations should containExactlyInAnyOrder(ruleViolationResolution1, ruleViolationResolution2)
                vulnerabilities should containExactlyInAnyOrder(vulnerabilityResolution1, vulnerabilityResolution2)
            }
        }

        "not overwrite previously added resolutions" {
            resolvedConfigurationRepository.addResolutions(
                ortRunId = ortRunId,
                resolutions = Resolutions(
                    issues = listOf(issueResolution1),
                    ruleViolations = listOf(ruleViolationResolution1),
                    vulnerabilities = listOf(vulnerabilityResolution1)
                )
            )

            resolvedConfigurationRepository.addResolutions(
                ortRunId = ortRunId,
                resolutions = Resolutions(
                    issues = listOf(issueResolution2),
                    ruleViolations = listOf(ruleViolationResolution2),
                    vulnerabilities = listOf(vulnerabilityResolution2)
                )
            )

            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            with(resolvedConfiguration.resolutions) {
                issues should containExactlyInAnyOrder(issueResolution1, issueResolution2)
                ruleViolations should containExactlyInAnyOrder(ruleViolationResolution1, ruleViolationResolution2)
                vulnerabilities should containExactlyInAnyOrder(vulnerabilityResolution1, vulnerabilityResolution2)
            }
        }
    }
})

private val identifier1 = Identifier("Maven", "org.example", "package1", "1.0")
private val identifier2 = Identifier("Maven", "org.example", "package2", "1.0")

private val packageConfiguration1 = PackageConfiguration(
    id = identifier1,
    sourceArtifactUrl = "https://example.org/artifact1.zip",
    pathExcludes = listOf(
        PathExclude(pattern = "test/**", reason = "TEST_OF"),
        PathExclude(pattern = "examples/**", reason = "EXAMPLE_OF")
    )
)

private val packageConfiguration2 = PackageConfiguration(
    id = identifier2,
    sourceArtifactUrl = "https://example.org/artifact2.zip",
    licenseFindingCurations = listOf(
        LicenseFindingCuration(
            path = "README.md",
            startLines = listOf(1),
            concludedLicense = "Apache-2.0",
            reason = "INCORRECT"
        )
    )
)

private val packageCurations1 = ResolvedPackageCurations(
    provider = PackageCurationProviderConfig(name = "provider1"),
    curations = listOf(
        PackageCuration(
            id = identifier1,
            data = PackageCurationData(comment = "comment1")
        ),
        PackageCuration(
            id = identifier2,
            data = PackageCurationData(comment = "comment2")
        )
    )
)

private val packageCurations2 = ResolvedPackageCurations(
    provider = PackageCurationProviderConfig(name = "provider2"),
    curations = listOf(
        PackageCuration(
            id = identifier1,
            data = PackageCurationData(homepageUrl = "https://example.org/package1")
        ),
        PackageCuration(
            id = identifier2,
            data = PackageCurationData(homepageUrl = "https://example.org/package2")
        )
    )
)

private val issueResolution1 = IssueResolution(
    message = "issue1",
    reason = "CANT_FIX_ISSUE",
    comment = "comment1"
)

private val issueResolution2 = IssueResolution(
    message = "issue2",
    reason = "SCANNER_ISSUE",
    comment = "comment2"
)

private val ruleViolationResolution1 = RuleViolationResolution(
    message = "ruleViolation1",
    reason = "CANT_FIX_EXCEPTION",
    comment = "comment1"
)

private val ruleViolationResolution2 = RuleViolationResolution(
    message = "ruleViolation2",
    reason = "EXAMPLE_OF_EXCEPTION",
    comment = "comment2"
)

private val vulnerabilityResolution1 = VulnerabilityResolution(
    externalId = "vulnerability1",
    reason = "CANT_FIX_VULNERABILITY",
    comment = "comment1"
)

private val vulnerabilityResolution2 = VulnerabilityResolution(
    externalId = "vulnerability2",
    reason = "INEFFECTIVE_VULNERABILITY",
    comment = "comment2"
)
