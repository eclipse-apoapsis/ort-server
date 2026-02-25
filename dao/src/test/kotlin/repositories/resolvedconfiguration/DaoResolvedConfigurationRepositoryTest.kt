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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.ResolvedVulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.ResolvedRuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.ResolvedIssuesTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.AppliedPackageCurationRef
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.LicenseFindingCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.PathExclude
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolutionReason

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

@Suppress("LargeClass")
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

    "addPackageCurationAssociations" should {
        "store associations between packages and their applied curations" {
            val package1 = fixtures.generatePackage(identifier1)
            val package2 = fixtures.generatePackage(identifier2)
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                packages = setOf(package1, package2)
            )

            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1, packageCurations2))

            val associations = mapOf(
                identifier1 to listOf(
                    AppliedPackageCurationRef(providerName = "provider1", curationRank = 0),
                    AppliedPackageCurationRef(providerName = "provider2", curationRank = 0)
                ),
                identifier2 to listOf(
                    AppliedPackageCurationRef(providerName = "provider1", curationRank = 1)
                )
            )

            resolvedConfigurationRepository.addPackageCurationAssociations(ortRunId, associations)

            val packageIds = dbExtension.db.dbQuery {
                PackagesTable
                    .innerJoin(IdentifiersTable)
                    .innerJoin(PackagesAnalyzerRunsTable)
                    .innerJoin(AnalyzerRunsTable)
                    .innerJoin(AnalyzerJobsTable)
                    .select(
                        PackagesTable.id,
                        IdentifiersTable.type,
                        IdentifiersTable.namespace,
                        IdentifiersTable.name,
                        IdentifiersTable.version
                    )
                    .where { AnalyzerJobsTable.ortRunId eq ortRunId }
                    .associate { row ->
                        Identifier(
                            type = row[IdentifiersTable.type],
                            namespace = row[IdentifiersTable.namespace],
                            name = row[IdentifiersTable.name],
                            version = row[IdentifiersTable.version]
                        ) to row[PackagesTable.id].value
                    }
            }

            val curationIds = dbExtension.db.dbQuery {
                ResolvedPackageCurationsTable
                    .innerJoin(ResolvedPackageCurationProvidersTable)
                    .innerJoin(PackageCurationProviderConfigsTable)
                    .innerJoin(ResolvedConfigurationsTable)
                    .select(
                        ResolvedPackageCurationsTable.id,
                        PackageCurationProviderConfigsTable.name,
                        ResolvedPackageCurationsTable.rank
                    )
                    .where { ResolvedConfigurationsTable.ortRunId eq ortRunId }
                    .associate { row ->
                        (row[PackageCurationProviderConfigsTable.name] to row[ResolvedPackageCurationsTable.rank]) to
                            row[ResolvedPackageCurationsTable.id].value
                    }
            }

            val storedRows = dbExtension.db.dbQuery {
                CuratedPackagesTable.selectAll()
                    .where { CuratedPackagesTable.ortRunId eq ortRunId }
                    .toList()
            }

            storedRows.map { row ->
                row[CuratedPackagesTable.packageId].value to row[CuratedPackagesTable.resolvedPackageCurationId].value
            } should containExactlyInAnyOrder(
                packageIds.getValue(identifier1) to curationIds.getValue("provider1" to 0),
                packageIds.getValue(identifier1) to curationIds.getValue("provider2" to 0),
                packageIds.getValue(identifier2) to curationIds.getValue("provider1" to 1)
            )
        }

        "handle multiple curations applied to the same package" {
            val package1 = fixtures.generatePackage(identifier1)
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                packages = setOf(package1)
            )

            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1, packageCurations2))

            resolvedConfigurationRepository.addPackageCurationAssociations(
                ortRunId = ortRunId,
                packageCurationAssociations = mapOf(
                    identifier1 to listOf(
                        AppliedPackageCurationRef(providerName = "provider1", curationRank = 0),
                        AppliedPackageCurationRef(providerName = "provider2", curationRank = 0)
                    )
                )
            )

            val curationIds = dbExtension.db.dbQuery {
                ResolvedPackageCurationsTable
                    .innerJoin(ResolvedPackageCurationProvidersTable)
                    .innerJoin(PackageCurationProviderConfigsTable)
                    .innerJoin(ResolvedConfigurationsTable)
                    .select(
                        ResolvedPackageCurationsTable.id,
                        PackageCurationProviderConfigsTable.name,
                        ResolvedPackageCurationsTable.rank
                    )
                    .where { ResolvedConfigurationsTable.ortRunId eq ortRunId }
                    .associate { row ->
                        (row[PackageCurationProviderConfigsTable.name] to row[ResolvedPackageCurationsTable.rank]) to
                            row[ResolvedPackageCurationsTable.id].value
                    }
            }

            val storedRows = dbExtension.db.dbQuery {
                CuratedPackagesTable.selectAll()
                    .where { CuratedPackagesTable.ortRunId eq ortRunId }
                    .toList()
            }

            storedRows.size shouldBe 2
            storedRows.map { it[CuratedPackagesTable.resolvedPackageCurationId].value } should containExactlyInAnyOrder(
                curationIds.getValue("provider1" to 0),
                curationIds.getValue("provider2" to 0)
            )
        }

        "handle duplicate calls idempotently (upsert)" {
            val package1 = fixtures.generatePackage(identifier1)
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                packages = setOf(package1)
            )

            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1))

            val associations = mapOf(
                identifier1 to listOf(
                    AppliedPackageCurationRef(providerName = "provider1", curationRank = 0)
                )
            )

            resolvedConfigurationRepository.addPackageCurationAssociations(ortRunId, associations)
            resolvedConfigurationRepository.addPackageCurationAssociations(ortRunId, associations)

            val storedRows = dbExtension.db.dbQuery {
                CuratedPackagesTable.selectAll()
                    .where { CuratedPackagesTable.ortRunId eq ortRunId }
                    .toList()
            }

            storedRows.shouldBeSingleton { }
        }

        "fail if a package is not found in the run" {
            val package1 = fixtures.generatePackage(identifier1)
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                packages = setOf(package1)
            )

            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1))

            val missingIdentifier = Identifier("Maven", "org.example", "missing", "1.0")

            val exception = shouldThrow<IllegalArgumentException> {
                resolvedConfigurationRepository.addPackageCurationAssociations(
                    ortRunId = ortRunId,
                    packageCurationAssociations = mapOf(
                        missingIdentifier to listOf(
                            AppliedPackageCurationRef(providerName = "provider1", curationRank = 0)
                        )
                    )
                )
            }

            exception.message shouldContain "No package found for identifier"
            exception.message shouldContain "missing"
        }

        "fail if a curation is not found in the resolved configuration" {
            val package1 = fixtures.generatePackage(identifier1)
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                packages = setOf(package1)
            )

            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1))

            val exception = shouldThrow<IllegalArgumentException> {
                resolvedConfigurationRepository.addPackageCurationAssociations(
                    ortRunId = ortRunId,
                    packageCurationAssociations = mapOf(
                        identifier1 to listOf(
                            AppliedPackageCurationRef(providerName = "unknown-provider", curationRank = 0)
                        )
                    )
                )
            }

            exception.message shouldContain "No resolved package curation found for provider"
            exception.message shouldContain "unknown-provider"
        }
    }

    "addResolutions" should {
        "add the provided resolutions from ResolvedItemsResult" {
            // Create test issues and use dummy items to store resolutions
            val dummyIssue = Issue(
                timestamp = Clock.System.now(),
                source = "Test",
                message = "dummy",
                severity = Severity.WARNING
            )
            val dummyViolation = fixtures.ruleViolation
            val dummyVulnerability = Vulnerability(
                externalId = "dummy",
                summary = "dummy",
                description = "dummy",
                references = emptyList()
            )

            resolvedConfigurationRepository.addResolutions(
                ortRunId = ortRunId,
                resolvedItems = ResolvedItemsResult(
                    issues = mapOf(dummyIssue to listOf(issueResolution1, issueResolution2)),
                    ruleViolations = mapOf(
                        dummyViolation to listOf(ruleViolationResolution1, ruleViolationResolution2)
                    ),
                    vulnerabilities = mapOf(
                        dummyVulnerability to listOf(vulnerabilityResolution1, vulnerabilityResolution2)
                    )
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
            val dummyIssue1 = Issue(Clock.System.now(), "Test", "dummy1", Severity.WARNING)
            val dummyIssue2 = Issue(Clock.System.now(), "Test", "dummy2", Severity.WARNING)
            val dummyViolation = fixtures.ruleViolation
            val dummyVulnerability = Vulnerability("dummy", "dummy", "dummy", emptyList())

            resolvedConfigurationRepository.addResolutions(
                ortRunId = ortRunId,
                resolvedItems = ResolvedItemsResult(
                    issues = mapOf(dummyIssue1 to listOf(issueResolution1)),
                    ruleViolations = mapOf(dummyViolation to listOf(ruleViolationResolution1)),
                    vulnerabilities = mapOf(dummyVulnerability to listOf(vulnerabilityResolution1))
                )
            )

            resolvedConfigurationRepository.addResolutions(
                ortRunId = ortRunId,
                resolvedItems = ResolvedItemsResult(
                    issues = mapOf(dummyIssue2 to listOf(issueResolution2)),
                    ruleViolations = mapOf(dummyViolation to listOf(ruleViolationResolution2)),
                    vulnerabilities = mapOf(dummyVulnerability to listOf(vulnerabilityResolution2))
                )
            )

            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            with(resolvedConfiguration.resolutions) {
                issues should containExactlyInAnyOrder(issueResolution1, issueResolution2)
                ruleViolations should containExactlyInAnyOrder(ruleViolationResolution1, ruleViolationResolution2)
                vulnerabilities should containExactlyInAnyOrder(vulnerabilityResolution1, vulnerabilityResolution2)
            }
        }

        "store resolved issues with their mappings" {
            // Create an issue in the database first
            val issue = Issue(
                timestamp = Clock.System.now(),
                source = "Analyzer",
                message = "Test issue message",
                severity = Severity.WARNING
            )
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                issues = listOf(issue)
            )

            val resolvedItems = ResolvedItemsResult(
                issues = mapOf(issue to listOf(issueResolution1)),
                ruleViolations = emptyMap(),
                vulnerabilities = emptyMap()
            )

            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Verify the resolved issue mapping was stored
            val storedResolvedIssues = dbExtension.db.dbQuery {
                ResolvedIssuesTable.selectAll()
                    .where { ResolvedIssuesTable.ortRunId eq ortRunId }
                    .toList()
            }

            storedResolvedIssues.size shouldBe 1

            // Verify the resolution was also stored in the resolved configuration
            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.resolutions.issues should containExactly(issueResolution1)
        }

        "store resolved rule violations with their mappings" {
            // Create a rule violation via evaluator run
            val ruleViolation = fixtures.ruleViolation

            fixtures.evaluatorRunRepository.create(
                evaluatorJobId = fixtures.evaluatorJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = Environment.EMPTY,
                violations = listOf(ruleViolation)
            )

            val resolvedItems = ResolvedItemsResult(
                issues = emptyMap(),
                ruleViolations = mapOf(ruleViolation to listOf(ruleViolationResolution1)),
                vulnerabilities = emptyMap()
            )

            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Verify the resolved rule violation mapping was stored
            val storedResolvedViolations = dbExtension.db.dbQuery {
                ResolvedRuleViolationsTable.selectAll()
                    .where { ResolvedRuleViolationsTable.ortRunId eq ortRunId }
                    .toList()
            }

            storedResolvedViolations.size shouldBe 1

            // Verify the resolution was also stored in the resolved configuration
            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.resolutions.ruleViolations should containExactly(ruleViolationResolution1)
        }

        "store resolved vulnerabilities with their mappings" {
            // Create a vulnerability via advisor run
            val vulnerability = Vulnerability(
                externalId = "CVE-2023-12345",
                summary = "Test vulnerability",
                description = "A test vulnerability",
                references = listOf(
                    VulnerabilityReference(
                        url = "https://example.com/vuln",
                        scoringSystem = "CVSS3",
                        severity = "HIGH",
                        score = 7.5f,
                        vector = "AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N"
                    )
                )
            )

            val advisorResult = AdvisorResult(
                advisorName = "TestAdvisor",
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                issues = emptyList(),
                vulnerabilities = listOf(vulnerability)
            )

            fixtures.createAdvisorRun(
                advisorJobId = fixtures.advisorJob.id,
                results = mapOf(fixtures.identifier to listOf(advisorResult))
            )

            val vulnerabilityResolution = VulnerabilityResolution(
                externalId = "CVE-2023-12345",
                reason = VulnerabilityResolutionReason.WILL_NOT_FIX_VULNERABILITY,
                comment = "Not applicable"
            )

            val resolvedItems = ResolvedItemsResult(
                issues = emptyMap(),
                ruleViolations = emptyMap(),
                vulnerabilities = mapOf(vulnerability to listOf(vulnerabilityResolution))
            )

            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Verify the resolved vulnerability mapping was stored
            val storedResolvedVulnerabilities = dbExtension.db.dbQuery {
                ResolvedVulnerabilitiesTable.selectAll()
                    .where { ResolvedVulnerabilitiesTable.ortRunId eq ortRunId }
                    .toList()
            }

            storedResolvedVulnerabilities.size shouldBe 1

            // Verify the resolution was also stored in the resolved configuration
            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.resolutions.vulnerabilities should containExactly(vulnerabilityResolution)
        }

        "handle empty resolved items" {
            val resolvedItems = ResolvedItemsResult.EMPTY

            // Should not throw
            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Verify nothing was stored
            val storedResolvedIssues = dbExtension.db.dbQuery {
                ResolvedIssuesTable.selectAll()
                    .where { ResolvedIssuesTable.ortRunId eq ortRunId }
                    .toList()
            }

            storedResolvedIssues.size shouldBe 0
        }

        "handle duplicate addResolutions calls without constraint violation" {
            // This simulates both the evaluator and reporter calling storeResolvedItems()
            // for the same ORT run with overlapping data.
            val issue = Issue(
                timestamp = Clock.System.now(),
                source = "Analyzer",
                message = "Duplicate test issue",
                severity = Severity.WARNING
            )
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                issues = listOf(issue)
            )

            val resolvedItems = ResolvedItemsResult(
                issues = mapOf(issue to listOf(issueResolution1)),
                ruleViolations = emptyMap(),
                vulnerabilities = emptyMap()
            )

            // First call (e.g. evaluator)
            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Second call with the same data (e.g. reporter) should not throw
            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Verify only one mapping was stored (upsert, not duplicate)
            val storedResolvedIssues = dbExtension.db.dbQuery {
                ResolvedIssuesTable.selectAll()
                    .where { ResolvedIssuesTable.ortRunId eq ortRunId }
                    .toList()
            }
            storedResolvedIssues.size shouldBe 1
        }

        "handle same resolution matching multiple issues without constraint violation" {
            // Create two issues in the database
            val issue1 = Issue(
                timestamp = Clock.System.now(),
                source = "Analyzer",
                message = "First test issue",
                severity = Severity.WARNING
            )
            val issue2 = Issue(
                timestamp = Clock.System.now(),
                source = "Analyzer",
                message = "Second test issue",
                severity = Severity.WARNING
            )
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                issues = listOf(issue1, issue2)
            )

            // Use the same resolution for both issues (simulating a regex pattern match)
            val sharedResolution = IssueResolution(
                message = ".*test issue.*",
                reason = IssueResolutionReason.CANT_FIX_ISSUE,
                comment = "Matches multiple issues"
            )

            val resolvedItems = ResolvedItemsResult(
                issues = mapOf(
                    issue1 to listOf(sharedResolution),
                    issue2 to listOf(sharedResolution)
                ),
                ruleViolations = emptyMap(),
                vulnerabilities = emptyMap()
            )

            // Should not throw unique constraint violation
            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Verify the resolution is stored only once in the resolved configuration
            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.resolutions.issues should containExactly(sharedResolution)

            // Verify mappings were stored for both issues
            val storedResolvedIssues = dbExtension.db.dbQuery {
                ResolvedIssuesTable.selectAll()
                    .where { ResolvedIssuesTable.ortRunId eq ortRunId }
                    .toList()
            }
            storedResolvedIssues.size shouldBe 2
        }

        "handle same resolution matching multiple rule violations without constraint violation" {
            // Create two rule violations via evaluator run
            val ruleViolation1 = fixtures.ruleViolation
            val ruleViolation2 = fixtures.ruleViolation.copy(
                message = "Second rule violation message"
            )

            fixtures.evaluatorRunRepository.create(
                evaluatorJobId = fixtures.evaluatorJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = Environment.EMPTY,
                violations = listOf(ruleViolation1, ruleViolation2)
            )

            // Use the same resolution for both violations
            val sharedResolution = RuleViolationResolution(
                message = ".*",
                reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                comment = "Matches multiple violations"
            )

            val resolvedItems = ResolvedItemsResult(
                issues = emptyMap(),
                ruleViolations = mapOf(
                    ruleViolation1 to listOf(sharedResolution),
                    ruleViolation2 to listOf(sharedResolution)
                ),
                vulnerabilities = emptyMap()
            )

            // Should not throw unique constraint violation
            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Verify the resolution is stored only once
            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.resolutions.ruleViolations should containExactly(sharedResolution)

            // Verify mappings were stored for both violations
            val storedResolvedViolations = dbExtension.db.dbQuery {
                ResolvedRuleViolationsTable.selectAll()
                    .where { ResolvedRuleViolationsTable.ortRunId eq ortRunId }
                    .toList()
            }
            storedResolvedViolations.size shouldBe 2
        }

        "handle same resolution matching multiple vulnerabilities without constraint violation" {
            // Create two vulnerabilities via advisor run
            val vulnerability1 = Vulnerability(
                externalId = "CVE-2023-0001",
                summary = "First vulnerability",
                description = "First test vulnerability",
                references = emptyList()
            )
            val vulnerability2 = Vulnerability(
                externalId = "CVE-2023-0002",
                summary = "Second vulnerability",
                description = "Second test vulnerability",
                references = emptyList()
            )

            val advisorResult1 = AdvisorResult(
                advisorName = "TestAdvisor",
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                issues = emptyList(),
                vulnerabilities = listOf(vulnerability1, vulnerability2)
            )

            fixtures.createAdvisorRun(
                advisorJobId = fixtures.advisorJob.id,
                results = mapOf(fixtures.identifier to listOf(advisorResult1))
            )

            // Use the same resolution for both vulnerabilities (regex pattern)
            val sharedResolution = VulnerabilityResolution(
                externalId = "CVE-2023-.*",
                reason = VulnerabilityResolutionReason.WILL_NOT_FIX_VULNERABILITY,
                comment = "Matches multiple vulnerabilities"
            )

            val resolvedItems = ResolvedItemsResult(
                issues = emptyMap(),
                ruleViolations = emptyMap(),
                vulnerabilities = mapOf(
                    vulnerability1 to listOf(sharedResolution),
                    vulnerability2 to listOf(sharedResolution)
                )
            )

            // Should not throw unique constraint violation
            resolvedConfigurationRepository.addResolutions(ortRunId, resolvedItems)

            // Verify the resolution is stored only once
            val resolvedConfiguration = resolvedConfigurationRepository.getForOrtRun(ortRunId).shouldNotBeNull()
            resolvedConfiguration.resolutions.vulnerabilities should containExactly(sharedResolution)

            // Verify mappings were stored for both vulnerabilities
            val storedResolvedVulnerabilities = dbExtension.db.dbQuery {
                ResolvedVulnerabilitiesTable.selectAll()
                    .where { ResolvedVulnerabilitiesTable.ortRunId eq ortRunId }
                    .toList()
            }
            storedResolvedVulnerabilities.size shouldBe 2
        }
    }

    "getForOrtRunId" should {
        "return curations grouped by package identifier" {
            val package1 = fixtures.generatePackage(identifier1)
            val package2 = fixtures.generatePackage(identifier2)
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                packages = setOf(package1, package2)
            )

            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1, packageCurations2))

            val associations = mapOf(
                identifier1 to listOf(
                    AppliedPackageCurationRef(providerName = "provider1", curationRank = 0),
                    AppliedPackageCurationRef(providerName = "provider2", curationRank = 0)
                ),
                identifier2 to listOf(
                    AppliedPackageCurationRef(providerName = "provider1", curationRank = 1)
                )
            )

            resolvedConfigurationRepository.addPackageCurationAssociations(ortRunId, associations)

            val result = dbExtension.db.dbQuery {
                CuratedPackagesTable.getForOrtRunId(ortRunId)
            }

            result.keys should containExactlyInAnyOrder(identifier1, identifier2)

            val id1Curations = result.getValue(identifier1)
            id1Curations.map { it.first } should containExactlyInAnyOrder("provider1", "provider2")
            id1Curations.first { it.first == "provider1" }.second shouldBe packageCurations1.curations[0]
            id1Curations.first { it.first == "provider2" }.second shouldBe packageCurations2.curations[0]

            val id2Curations = result.getValue(identifier2)
            id2Curations.shouldBeSingleton { }
            id2Curations[0].first shouldBe "provider1"
            id2Curations[0].second shouldBe packageCurations1.curations[1]
        }

        "return empty map when no curations exist" {
            dbExtension.db.dbQuery {
                CuratedPackagesTable.getForOrtRunId(ortRunId)
            } shouldBe emptyMap()
        }

        "not include packages without curations" {
            val package1 = fixtures.generatePackage(identifier1)
            val package2 = fixtures.generatePackage(identifier2)
            fixtures.createAnalyzerRun(
                analyzerJobId = fixtures.analyzerJob.id,
                packages = setOf(package1, package2)
            )

            resolvedConfigurationRepository.addPackageCurations(ortRunId, listOf(packageCurations1))

            val associations = mapOf(
                identifier1 to listOf(
                    AppliedPackageCurationRef(providerName = "provider1", curationRank = 0)
                )
            )

            resolvedConfigurationRepository.addPackageCurationAssociations(ortRunId, associations)

            val result = dbExtension.db.dbQuery {
                CuratedPackagesTable.getForOrtRunId(ortRunId)
            }

            result.keys should containExactly(identifier1)
            result[identifier2] shouldBe null
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
            data = PackageCurationData(
                comment = "comment1",
                labels = mapOf("key1" to "value1", "key2" to "value2"),
                sourceCodeOrigins = listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
            )
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
    reason = IssueResolutionReason.CANT_FIX_ISSUE,
    comment = "comment1"
)

private val issueResolution2 = IssueResolution(
    message = "issue2",
    reason = IssueResolutionReason.SCANNER_ISSUE,
    comment = "comment2"
)

private val ruleViolationResolution1 = RuleViolationResolution(
    message = "ruleViolation1",
    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
    comment = "comment1"
)

private val ruleViolationResolution2 = RuleViolationResolution(
    message = "ruleViolation2",
    reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
    comment = "comment2"
)

private val vulnerabilityResolution1 = VulnerabilityResolution(
    externalId = "vulnerability1",
    reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
    comment = "comment1"
)

private val vulnerabilityResolution2 = VulnerabilityResolution(
    externalId = "vulnerability2",
    reason = VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
    comment = "comment2"
)
