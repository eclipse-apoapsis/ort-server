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

package org.eclipse.apoapsis.ortserver.workers.common.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.NestedRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ResolvedConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.EvaluatorRun
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.OrtRuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.reporter.ReporterRun
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.CopyrightFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.LicenseFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ProvenanceResolutionResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.RepositoryProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanSummary
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerDetail
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.model.runs.scanner.SnippetFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.PackageCuration as OrtPackageCuration
import org.ossreviewtoolkit.model.PackageCurationData as OrtPackageCurationData
import org.ossreviewtoolkit.model.RemoteArtifact as OrtRemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ResolvedPackageCurations as OrtResolvedPackageCurations
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.IssueResolution as OrtIssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.PackageConfiguration as OrtPackageConfiguration
import org.ossreviewtoolkit.model.config.RuleViolationResolution as OrtRuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.model.config.VcsMatcher
import org.ossreviewtoolkit.model.config.VulnerabilityResolution as OrtVulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason
import org.ossreviewtoolkit.utils.common.gibibytes
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

@Suppress("LargeClass")
class OrtRunServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures

    lateinit var repositoryConfigRepository: RepositoryConfigurationRepository
    lateinit var resolvedConfigurationRepository: ResolvedConfigurationRepository

    lateinit var service: OrtRunService

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures

        repositoryConfigRepository = dbExtension.fixtures.repositoryConfigurationRepository
        resolvedConfigurationRepository = dbExtension.fixtures.resolvedConfigurationRepository

        service = OrtRunService(
            db,
            fixtures.advisorJobRepository,
            fixtures.advisorRunRepository,
            fixtures.analyzerJobRepository,
            fixtures.analyzerRunRepository,
            fixtures.evaluatorJobRepository,
            fixtures.evaluatorRunRepository,
            fixtures.ortRunRepository,
            fixtures.reporterJobRepository,
            fixtures.reporterRunRepository,
            fixtures.notifierJobRepository,
            fixtures.notifierRunRepository,
            repositoryConfigRepository,
            fixtures.repositoryRepository,
            resolvedConfigurationRepository,
            fixtures.scannerJobRepository,
            fixtures.scannerRunRepository
        )
    }

    "createScannerRun" should {
        "create an empty scanner run" {
            fixtures.scannerJob
            service.createScannerRun(fixtures.scannerJob.id).run {
                scannerJobId shouldBe fixtures.scannerJob.id
                startTime should beNull()
                endTime should beNull()
                environment should beNull()
                config should beNull()
                provenances should beEmpty()
                scanResults should beEmpty()
            }
        }

        "fail if the scanner job does not exist" {
            shouldThrow<EntityNotFoundException> {
                service.createScannerRun(-1L)
            }
        }
    }

    "finalizeScannerRun" should {
        "update the run correctly" {
            val identifier1 = Identifier("type-1", "namespace-1", "name-1", "version-1")
            val identifier2 = Identifier("type-2", "namespace-2", "name-2", "version-2")
            val scannerRun = ScannerRun(
                id = 1L,
                scannerJobId = fixtures.scannerJob.id,
                startTime = Clock.System.now().toDatabasePrecision(),
                endTime = Clock.System.now().toDatabasePrecision(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = ScannerConfiguration(
                    skipConcluded = true,
                    skipExcluded = true,
                    detectedLicenseMappings = mapOf("license-1" to "spdx-license-1"),
                    config = emptyMap(),
                    ignorePatterns = listOf("pattern-1")
                ),
                provenances = emptySet(),
                scanResults = emptySet(),
                scanners = mapOf(
                    identifier1 to setOf("scanner-1"),
                    identifier2 to setOf("scanner-2")
                )
            )

            val issues = listOf(
                Issue(
                    timestamp = Clock.System.now().minus(2.minutes).toDatabasePrecision(),
                    source = "TestScanner",
                    message = "some error message",
                    severity = Severity.WARNING,
                    identifier = identifier1,
                    worker = "scanner"
                    ),
                Issue(
                    timestamp = Clock.System.now().minus(50.seconds).toDatabasePrecision(),
                    source = "TestScanner2",
                    message = "another error message",
                    severity = Severity.ERROR,
                    identifier = identifier2
                )
            )

            service.createScannerRun(scannerRun.scannerJobId)
            service.finalizeScannerRun(scannerRun, issues)

            fixtures.scannerRunRepository.getByJobId(fixtures.scannerJob.id) shouldBe scannerRun

            fixtures.ortRunRepository.get(fixtures.scannerJob.ortRunId)?.issues shouldContainExactlyInAnyOrder issues
        }
    }

    "getAdvisorJob" should {
        "return the advisor job" {
            fixtures.advisorJob
            service.getAdvisorJob(fixtures.advisorJob.id) shouldBe fixtures.advisorJob
        }

        "return null if the job does not exist" {
            service.getAdvisorJob(-1L) should beNull()
        }
    }

    "getAdvisorJobForOrtRun" should {
        "return the advisor job for the ORT run" {
            fixtures.advisorJob
            service.getAdvisorJobForOrtRun(fixtures.ortRun.id) shouldBe fixtures.advisorJob
        }

        "return null if no advisor job for the ORT run exists" {
            service.getAdvisorJobForOrtRun(-1L) should beNull()
        }
    }

    "getAdvisorRunForOrtRun" should {
        "return the advisor run for the ORT run" {
            val createdAdvisorRun = dbExtension.fixtures.advisorRunRepository.create(
                advisorJobId = fixtures.advisorJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = AdvisorConfiguration(emptyMap()),
                results = emptyMap()
            )

            service.getAdvisorRunForOrtRun(fixtures.ortRun.id) shouldBe createdAdvisorRun
        }

        "return null if no advisor run for the ORT run exists" {
            service.getAdvisorRunForOrtRun(-1L) should beNull()
        }
    }

    "getAnalyzerJob" should {
        "return the analyzer job" {
            fixtures.analyzerJob
            service.getAnalyzerJob(fixtures.analyzerJob.id) shouldBe fixtures.analyzerJob
        }

        "return null if the job does not exist" {
            service.getAnalyzerJob(-1L) should beNull()
        }
    }

    "getAnalyzerJobForOrtRun" should {
        "return the analyzer job for the ORT run" {
            fixtures.analyzerJob
            service.getAnalyzerJobForOrtRun(fixtures.ortRun.id) shouldBe fixtures.analyzerJob
        }

        "return null if no advisor job for the ORT run exists" {
            service.getAnalyzerJobForOrtRun(-1L) should beNull()
        }
    }

    "getAnalyzerRunForOrtRun" should {
        "return the analyzer run for the ORT run" {
            val createdAnalyzerRun = dbExtension.fixtures.analyzerRunRepository.create(
                analyzerJobId = fixtures.analyzerJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = AnalyzerConfiguration(
                    allowDynamicVersions = true,
                    enabledPackageManagers = emptyList(),
                    disabledPackageManagers = emptyList(),
                    packageManagers = emptyMap(),
                    skipExcluded = true
                ),
                projects = emptySet(),
                packages = emptySet(),
                issues = emptyList(),
                dependencyGraphs = emptyMap()
            )

            service.getAnalyzerRunForOrtRun(fixtures.ortRun.id) shouldBe createdAnalyzerRun
        }

        "return null if the analyzer run for the ORT run exists" {
            service.getAnalyzerRunForOrtRun(-1L) should beNull()
        }
    }

    "getEvaluatorJob" should {
        "return the evaluator job" {
            fixtures.evaluatorJob
            service.getEvaluatorJob(fixtures.evaluatorJob.id) shouldBe fixtures.evaluatorJob
        }

        "return null if the job does not exist" {
            service.getEvaluatorJob(-1L) should beNull()
        }
    }

    "getEvaluatorJobForOrtRun" should {
        "return the evaluator job for the ORT run" {
            fixtures.evaluatorJob
            service.getEvaluatorJobForOrtRun(fixtures.ortRun.id) shouldBe fixtures.evaluatorJob
        }

        "return null if no evaluator job for the ORT run exists" {
            service.getEvaluatorJobForOrtRun(-1L) should beNull()
        }
    }

    "getEvaluatorRunForOrtRun" should {
        "return the evaluator run for the ORT run" {
            val createdEvaluatorRun = dbExtension.fixtures.evaluatorRunRepository.create(
                evaluatorJobId = fixtures.evaluatorJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                violations = emptyList()
            )

            service.getEvaluatorRunForOrtRun(fixtures.ortRun.id) shouldBe createdEvaluatorRun
        }

        "return null if no evaluator run for the ORT run exists" {
            service.getEvaluatorRunForOrtRun(-1L) should beNull()
        }
    }

    "getHierarchy" should {
        "return the hierarchy for the ORT run" {
            service.getHierarchyForOrtRun(fixtures.ortRun.id) shouldBe
                    Hierarchy(fixtures.repository, fixtures.product, fixtures.organization)
        }

        "return null if the ORT run does not exist" {
            service.getHierarchyForOrtRun(-1L) should beNull()
        }
    }

    "getOrtRepositoryInformation" should {
        "return ORT repository object" {
            val vcsInfo = createVcsInfo("https://example.com/repo.git")
            val processedVcsInfo = createVcsInfo("https://example.com/repo-processed.git")
            val nestedVcsInfo1 = createVcsInfo("https://example.com/repo-nested-1.git")
            val nestedVcsInfo2 = createVcsInfo("https://example.com/repo-nested-2.git")

            val ortRun = createOrtRun(
                db,
                vcsInfo,
                processedVcsInfo,
                nestedVcsInfo1,
                nestedVcsInfo2,
                fixtures,
                repositoryConfigRepository
            )

            service.getOrtRepositoryInformation(ortRun) shouldBe Repository(
                vcsInfo.mapToOrt(),
                processedVcsInfo.mapToOrt(),
                mapOf("nested-1" to nestedVcsInfo1.mapToOrt(), "nested-2" to nestedVcsInfo2.mapToOrt()),
                OrtTestData.repository.config
            )
        }

        "throw exception if VCS information is not present in ORT run" {
            val processedVcsInfo = createVcsInfo("https://example.com/repo-processed.git")
            val nestedVcsInfo1 = createVcsInfo("https://example.com/repo-nested-1.git")
            val nestedVcsInfo2 = createVcsInfo("https://example.com/repo-nested-2.git")

            val ortRun = createOrtRun(
                db,
                null,
                processedVcsInfo,
                nestedVcsInfo1,
                nestedVcsInfo2,
                fixtures,
                repositoryConfigRepository
            )

            val exception = shouldThrow<IllegalArgumentException> {
                service.getOrtRepositoryInformation(ortRun)
            }

            exception.message shouldBe "VCS information is missing from ORT run '1'."
        }

        "throw exception if processed VCS information is not present in ORT run" {
            val vcsInfo = createVcsInfo("https://example.com/repo.git")
            val nestedVcsInfo1 = createVcsInfo("https://example.com/repo-nested-1.git")
            val nestedVcsInfo2 = createVcsInfo("https://example.com/repo-nested-2.git")

            val ortRun = createOrtRun(
                db,
                vcsInfo,
                null,
                nestedVcsInfo1,
                nestedVcsInfo2,
                fixtures,
                repositoryConfigRepository
            )

            val exception = shouldThrow<IllegalArgumentException> {
                service.getOrtRepositoryInformation(ortRun)
            }

            exception.message shouldBe "VCS processed information is missing from ORT run '1'."
        }

        "return an empty Repository if no VCS information is present and failIfMissing is false" {
            val repository = service.getOrtRepositoryInformation(fixtures.ortRun, failIfMissing = false)

            repository shouldBe Repository.EMPTY
        }
    }

    "getOrtRun" should {
        "return the ORT run" {
            service.getOrtRun(fixtures.ortRun.id) shouldBe fixtures.ortRun
        }

        "return null if the ORT run does not exist" {
            service.getOrtRun(-1L) should beNull()
        }
    }

    "getReporterJob" should {
        "return the reporter job" {
            fixtures.reporterJob
            service.getReporterJob(fixtures.reporterJob.id) shouldBe fixtures.reporterJob
        }

        "return null if the job does not exist" {
            service.getReporterJob(-1L) should beNull()
        }
    }

    "getReporterJobForOrtRun" should {
        "return the reporter job for the ORT run" {
            fixtures.reporterJob
            service.getReporterJobForOrtRun(fixtures.ortRun.id) shouldBe fixtures.reporterJob
        }

        "return null if no reporter job for the ORT run exists" {
            service.getReporterJobForOrtRun(-1L) should beNull()
        }
    }

    "getReporterRunForOrtRun" should {
        "return the reporter run for the ORT run" {
            val createdReporterRun = dbExtension.fixtures.reporterRunRepository.create(
                reporterJobId = fixtures.reporterJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                reports = emptyList()
            )

            service.getReporterRunForOrtRun(fixtures.ortRun.id) shouldBe createdReporterRun
        }

        "return null if no reporter run for the ORT run exists" {
            service.getReporterRunForOrtRun(-1L) should beNull()
        }
    }

    "getResolvedConfiguration" should {
        "return the resolved configuration" {
            val ortRun = fixtures.ortRun

            val id = Identifier("type", "namespace", "name", "version")

            val packageConfigurations = listOf(PackageConfiguration(id = id))
            resolvedConfigurationRepository.addPackageConfigurations(ortRun.id, packageConfigurations)

            val packageCurations = listOf(
                ResolvedPackageCurations(
                    provider = PackageCurationProviderConfig(name = "name"),
                    curations = listOf(PackageCuration(id = id, PackageCurationData()))
                )
            )
            resolvedConfigurationRepository.addPackageCurations(ortRun.id, packageCurations)

            val resolutions = Resolutions(
                issues = listOf(IssueResolution(message = "message", reason = "reason", comment = "comment"))
            )
            resolvedConfigurationRepository.addResolutions(ortRun.id, resolutions)

            service.getResolvedConfiguration(ortRun) shouldBe
                    ResolvedConfiguration(packageConfigurations, packageCurations, resolutions)
        }

        "return an empty resolved configuration if no resolved configuration was stored" {
            service.getResolvedConfiguration(fixtures.ortRun) shouldBe ResolvedConfiguration()
        }
    }

    "getScannerJob" should {
        "return the scanner job" {
            fixtures.scannerJob
            service.getScannerJob(fixtures.scannerJob.id) shouldBe fixtures.scannerJob
        }

        "return null if the job does not exist" {
            service.getScannerJob(-1L) should beNull()
        }
    }

    "getScannerJobForOrtRun" should {
        "return the scanner job for the ORT run" {
            fixtures.scannerJob
            service.getScannerJobForOrtRun(fixtures.ortRun.id) shouldBe fixtures.scannerJob
        }

        "return null if no scanner job for the ORT run exists" {
            service.getScannerJobForOrtRun(-1L) should beNull()
        }
    }

    "getScannerRunForOrtRun" should {
        "return the scanner run for the ORT run" {
            val createdScannerRun = fixtures.scannerRunRepository.update(
                id = fixtures.scannerRunRepository.create(fixtures.scannerJob.id).id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = ScannerConfiguration(
                    skipConcluded = true,
                    skipExcluded = true,
                    detectedLicenseMappings = emptyMap(),
                    config = emptyMap(),
                    ignorePatterns = emptyList()
                ),
                scanners = emptyMap()
            )

            service.getScannerRunForOrtRun(fixtures.ortRun.id) shouldBe createdScannerRun
        }

        "return null if no scanner run for the ORT run exists" {
            service.getScannerRunForOrtRun(-1L) should beNull()
        }
    }

    "startAdvisorJob" should {
        "start the job" {
            service.startAdvisorJob(fixtures.advisorJob.id) shouldNotBeNull {
                startedAt shouldNot beNull()
                status shouldBe JobStatus.RUNNING
            }
        }

        "return null if the job was already started" {
            service.startAdvisorJob(fixtures.advisorJob.id)

            service.startAdvisorJob(fixtures.advisorJob.id) should beNull()
        }

        "fail if the job does not exist" {
            shouldThrow<IllegalArgumentException> {
                service.startAdvisorJob(-1) should beNull()
            }
        }
    }

    "startAnalyzerJob" should {
        "start the job" {
            service.startAnalyzerJob(fixtures.analyzerJob.id) shouldNotBeNull {
                startedAt shouldNot beNull()
                status shouldBe JobStatus.RUNNING
            }
        }

        "return null if the job was already started" {
            service.startAnalyzerJob(fixtures.analyzerJob.id)

            service.startAnalyzerJob(fixtures.analyzerJob.id) should beNull()
        }

        "fail if the job does not exist" {
            shouldThrow<IllegalArgumentException> {
                service.startAnalyzerJob(-1) should beNull()
            }
        }
    }

    "startEvaluatorJob" should {
        "start the job" {
            service.startEvaluatorJob(fixtures.evaluatorJob.id) shouldNotBeNull {
                startedAt shouldNot beNull()
                status shouldBe JobStatus.RUNNING
            }
        }

        "return null if the job was already started" {
            service.startEvaluatorJob(fixtures.evaluatorJob.id)

            service.startEvaluatorJob(fixtures.evaluatorJob.id) should beNull()
        }

        "fail if the job does not exist" {
            shouldThrow<IllegalArgumentException> {
                service.startEvaluatorJob(-1) should beNull()
            }
        }
    }

    "startReporterJob" should {
        "start the job" {
            service.startReporterJob(fixtures.reporterJob.id) shouldNotBeNull {
                startedAt shouldNot beNull()
                status shouldBe JobStatus.RUNNING
            }
        }

        "return null if the job was already started" {
            service.startReporterJob(fixtures.reporterJob.id)

            service.startReporterJob(fixtures.reporterJob.id) should beNull()
        }

        "fail if the job does not exist" {
            shouldThrow<IllegalArgumentException> {
                service.startReporterJob(-1) should beNull()
            }
        }
    }

    "startScannerJob" should {
        "start the job" {
            service.startScannerJob(fixtures.scannerJob.id) shouldNotBeNull {
                startedAt shouldNot beNull()
                status shouldBe JobStatus.RUNNING
            }
        }

        "return null if the job was already started" {
            service.startScannerJob(fixtures.scannerJob.id)

            service.startScannerJob(fixtures.scannerJob.id) should beNull()
        }

        "fail if the job does not exist" {
            shouldThrow<IllegalArgumentException> {
                service.startScannerJob(-1) should beNull()
            }
        }
    }

    "storeAdvisorRun" should {
        "store the run correctly" {
            val advisorRun = AdvisorRun(
                id = 1,
                advisorJobId = fixtures.advisorJob.id,
                startTime = Clock.System.now().toDatabasePrecision(),
                endTime = Clock.System.now().toDatabasePrecision(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = AdvisorConfiguration(emptyMap()),
                results = emptyMap()
            )

            service.storeAdvisorRun(advisorRun)

            fixtures.advisorRunRepository.getByJobId(fixtures.advisorJob.id) shouldBe advisorRun
        }
    }

    "storeAnalyzerRun" should {
        "store the run correctly" {
            val analyzerRun = AnalyzerRun(
                id = 1L,
                analyzerJobId = fixtures.analyzerJob.id,
                startTime = Clock.System.now().toDatabasePrecision(),
                endTime = Clock.System.now().toDatabasePrecision(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = AnalyzerConfiguration(
                    allowDynamicVersions = true,
                    enabledPackageManagers = null,
                    disabledPackageManagers = null,
                    packageManagers = emptyMap(),
                    skipExcluded = true
                ),
                projects = emptySet(),
                packages = emptySet(),
                issues = emptyList(),
                dependencyGraphs = emptyMap()
            )

            service.storeAnalyzerRun(analyzerRun)

            fixtures.analyzerRunRepository.getByJobId(fixtures.analyzerJob.id) shouldBe analyzerRun
        }
    }

    "storeEvaluatorRun" should {
        "store the run correctly" {
            val evaluatorRun = EvaluatorRun(
                id = 1L,
                evaluatorJobId = fixtures.evaluatorJob.id,
                startTime = Clock.System.now().toDatabasePrecision(),
                endTime = Clock.System.now().toDatabasePrecision(),
                violations = listOf(
                    OrtRuleViolation(
                        rule = "rule",
                        fixtures.identifier,
                        license = "license",
                        licenseSource = "license source",
                        severity = Severity.ERROR,
                        message = "the rule is violated",
                        howToFix = "how to fix info"
                    )
                )
            )

            service.storeEvaluatorRun(evaluatorRun)

            fixtures.evaluatorRunRepository.getByJobId(fixtures.evaluatorJob.id) shouldBe evaluatorRun
        }

        "store a run with a huge howToFix hint" {
            val howToFix = "how to fix info".repeat(25000)
            val evaluatorRun = EvaluatorRun(
                id = 1L,
                evaluatorJobId = fixtures.evaluatorJob.id,
                startTime = Clock.System.now().toDatabasePrecision(),
                endTime = Clock.System.now().toDatabasePrecision(),
                violations = listOf(
                    OrtRuleViolation(
                        rule = "rule",
                        fixtures.identifier,
                        license = "license",
                        licenseSource = "license source",
                        severity = Severity.ERROR,
                        message = "the rule is violated",
                        howToFix = howToFix
                    )
                )
            )

            service.storeEvaluatorRun(evaluatorRun)

            fixtures.evaluatorRunRepository.getByJobId(fixtures.evaluatorJob.id) shouldBe evaluatorRun
        }
    }

    "storeReporterRun" should {
        "store the run correctly" {
            val reporterRun = ReporterRun(
                id = 1L,
                reporterJobId = fixtures.reporterJob.id,
                startTime = Clock.System.now().toDatabasePrecision(),
                endTime = Clock.System.now().toDatabasePrecision(),
                reports = listOf(
                    Report("report1.zip", "token1", Clock.System.now().toDatabasePrecision()),
                    Report("report2.zip", "token2", Clock.System.now().toDatabasePrecision())
                )
            )

            service.storeReporterRun(reporterRun)

            fixtures.reporterRunRepository.getByJobId(fixtures.reporterJob.id) shouldBe reporterRun
        }
    }

    "storeRepositoryInformation" should {
        "store repository information correctly" {
            val repository = db.blockingQuery {
                val vcsInfo = VcsInfoDao.getOrPut(createVcsInfo("https://example.org/repo.git"))
                val processedVcsInfo = VcsInfoDao.getOrPut(createVcsInfo("https://example.org/processed-repo.git"))
                val nestedVcsInfo = VcsInfoDao.getOrPut(createVcsInfo("https://example.org/nested-repo.git"))
                val nestedVcsPath = "path"

                val ortRunDao = OrtRunDao[fixtures.ortRun.id]
                ortRunDao.vcsId = vcsInfo.id
                ortRunDao.vcsProcessedId = processedVcsInfo.id

                val licenseFindingCuration = LicenseFindingCuration(
                    path = "a/path",
                    lineCount = 5,
                    detectedLicense = SpdxExpression.parse("LicenseRef-scancode-free-unknown"),
                    concludedLicense = SpdxExpression.parse("BSD-3-Clause"),
                    reason = LicenseFindingCurationReason.INCORRECT,
                    comment = "Test license finding curation"
                )
                val packageConfiguration = OrtPackageConfiguration(
                    id = OrtIdentifier("type", "namespace", "name", "version"),
                    sourceArtifactUrl = "https://example.org/source.artifact.url",
                    licenseFindingCurations = listOf(licenseFindingCuration)
                )
                val repositoryConfig = OrtTestData.repository.config.copy(
                    packageConfigurations = listOf(packageConfiguration)
                )

                Repository(
                    vcsInfo.mapToModel().mapToOrt(),
                    processedVcsInfo.mapToModel().mapToOrt(),
                    mapOf(nestedVcsPath to nestedVcsInfo.mapToModel().mapToOrt()),
                    repositoryConfig
                )
            }

            service.storeRepositoryInformation(fixtures.ortRun.id, repository)

            // Reload ORT run to load VCS values updated above.
            val ortRun = fixtures.ortRunRepository.get(fixtures.ortRun.id).shouldNotBeNull()
            service.getOrtRepositoryInformation(ortRun) shouldBe repository
        }
    }

    "storeResolvedPackageConfigurations" should {
        "store the resolved package configurations" {
            val configurations = listOf(
                OrtPackageConfiguration(
                    id = OrtTestData.pkgIdentifier,
                    sourceArtifactUrl = OrtTestData.pkgCuratedSourceArtifactUrl,
                    pathExcludes = listOf(OrtTestData.pathExclude),
                    licenseFindingCurations = listOf(OrtTestData.licenseFindingCuration)
                ),
                OrtPackageConfiguration(
                    id = OrtTestData.pkgIdentifier,
                    vcs = VcsMatcher(
                        type = VcsType.GIT,
                        url = OrtTestData.pkgCuratedRepositoryUrl,
                        revision = OrtTestData.pkgCuratedRevision
                    ),
                    pathExcludes = listOf(OrtTestData.pathExclude),
                    licenseFindingCurations = listOf(OrtTestData.licenseFindingCuration)
                )
            )

            service.storeResolvedPackageConfigurations(fixtures.ortRun.id, configurations)

            val resolvedConfiguration =
                dbExtension.fixtures.resolvedConfigurationRepository.getForOrtRun(fixtures.ortRun.id)

            resolvedConfiguration.shouldNotBeNull()
            resolvedConfiguration.packageConfigurations should containExactly(configurations.map { it.mapToModel() })
        }
    }

    "storeResolvedPackageCurations" should {
        "store the resolved package curations" {
            val curations = listOf(
                OrtResolvedPackageCurations(
                    OrtResolvedPackageCurations.Provider("provider1"),
                    curations = listOf(
                        OrtPackageCuration(
                            id = OrtIdentifier("Maven:org.example:package1:1.0"),
                            data = OrtPackageCurationData(
                                comment = "comment 1",
                                purl = "purl",
                                cpe = "cpe",
                                authors = setOf("author 1", "author 2"),
                                concludedLicense = "Apache-2.0".toSpdx(),
                                description = "description",
                                homepageUrl = "homepageUrl",
                                binaryArtifact = OrtRemoteArtifact(
                                    url = "binary URL",
                                    Hash.create("1234567890abcdef1234567890abcdef12345678")
                                ),
                                sourceArtifact = OrtRemoteArtifact(
                                    url = "source URL",
                                    Hash.create("abcdef1234567890abcdef1234567890abcdef12")
                                ),
                                vcs = VcsInfoCurationData(
                                    type = VcsType.GIT,
                                    url = "vcs URL",
                                    revision = "revision",
                                    path = "path"
                                ),
                                isMetadataOnly = false,
                                isModified = false,
                                declaredLicenseMapping = mapOf(
                                    "Apache 2.0" to "Apache-2.0".toSpdx(),
                                    "BSD 3" to "BSD-3-Clause".toSpdx()
                                )
                            )
                        ),
                        OrtPackageCuration(
                            id = OrtIdentifier("Maven:org.example:package1:1.0"),
                            data = OrtPackageCurationData(comment = "comment 2")
                        ),
                        OrtPackageCuration(
                            id = OrtIdentifier("Maven:org.example:package2:1.0"),
                            data = OrtPackageCurationData(comment = "comment 3")
                        )
                    )
                )
            )

            service.storeResolvedPackageCurations(fixtures.ortRun.id, curations)

            val resolvedConfiguration = fixtures.resolvedConfigurationRepository.getForOrtRun(fixtures.ortRun.id)

            resolvedConfiguration.shouldNotBeNull()
            resolvedConfiguration.packageCurations should containExactly(curations.map { it.mapToModel() })
        }
    }

    "storeResolvedResolutions" should {
        "store the resolved resolutions" {
            val resolutions = org.ossreviewtoolkit.model.config.Resolutions(
                issues = listOf(
                    OrtIssueResolution("message", IssueResolutionReason.CANT_FIX_ISSUE, "comment")
                ),
                ruleViolations = listOf(
                    OrtRuleViolationResolution("message", RuleViolationResolutionReason.CANT_FIX_EXCEPTION, "comment")
                ),
                vulnerabilities = listOf(
                    OrtVulnerabilityResolution("id", VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY, "comment")
                )
            )

            service.storeResolvedResolutions(fixtures.ortRun.id, resolutions)

            val resolvedConfiguration = fixtures.resolvedConfigurationRepository.getForOrtRun(fixtures.ortRun.id)

            resolvedConfiguration.shouldNotBeNull()
            resolvedConfiguration.resolutions shouldBe resolutions.mapToModel()
        }
    }

    "storeIssues" should {
        "store the issues" {
            val issues = listOf(
                Issue(
                    Instant.parse("2024-02-20T13:51:00Z"),
                    "issueSource",
                    "Some test issue",
                    Severity.HINT
                ),
                Issue(
                    Instant.parse("2024-02-20T13:52:00Z"),
                    "differentIssueSource",
                    "Some problematic issue",
                    Severity.WARNING
                )
            )

            service.storeIssues(fixtures.ortRun.id, issues)

            val storedIssues = fixtures.ortRunRepository.get(fixtures.ortRun.id)?.issues

            storedIssues shouldBe issues
        }
    }

    "generateOrtResult" should {
        "should return repository information" {

            val vcsInfo = createVcsInfo("https://example.com/repo.git")
            val processedVcsInfo = createVcsInfo("https://example.com/repo-processed.git")
            val nestedVcsInfo1 = createVcsInfo("https://example.com/repo-nested-1.git")
            val nestedVcsInfo2 = createVcsInfo("https://example.com/repo-nested-2.git")

            val ortRun = createOrtRun(
                db,
                vcsInfo,
                processedVcsInfo,
                nestedVcsInfo1,
                nestedVcsInfo2,
                fixtures,
                repositoryConfigRepository
            )

            service.generateOrtResult(ortRun).let { ortResult ->
                ortResult.repository.vcs shouldBe vcsInfo.mapToOrt()
                ortResult.repository.vcsProcessed shouldBe processedVcsInfo.mapToOrt()
                ortResult.repository.nestedRepositories["nested-1"] shouldBe nestedVcsInfo1.mapToOrt()
                ortResult.repository.nestedRepositories["nested-2"] shouldBe nestedVcsInfo2.mapToOrt()
            }
        }

        "throw IllegalArgumentException if repository info is required and does actually not exist" {
            shouldThrow<IllegalArgumentException> {
                service.generateOrtResult(fixtures.ortRun, failIfRepoInfoMissing = true)
            }
        }

        "contain common labels" {
            service.generateOrtResult(fixtures.ortRun, failIfRepoInfoMissing = false).let { ortResult ->
                ortResult.labels shouldContain ("runId" to fixtures.ortRun.id.toString())
            }
        }
    }

    "filterScanResultsByVcsPath" should {
        "keep scan results unmodified if no VCS path is specified" {

            val provenances = createProvenances(vcsPath = "")
            val scanResults = createScanResults()

            service.filterScanResultsByVcsPath(provenances, scanResults).let { filteredOrtScanResults ->
                filteredOrtScanResults.shouldNotBeNull()
                with(filteredOrtScanResults) {
                    size shouldBe 1
                    first().apply {
                        summary.shouldNotBeNull()
                        with(summary) {
                            size shouldBe 1
                            first().apply {
                                // No findings are filtered out.
                                licenseFindings.size shouldBe 2
                                copyrightFindings.size shouldBe 2
                                snippetFindings.size shouldBe 2
                            }
                        }
                    }
                }
            }
        }

        "filter out scan results that are not inside the VCS path" {

            val provenances = createProvenances(vcsPath = "npm/simple")
            val scanResults = createScanResults()

            service.filterScanResultsByVcsPath(provenances, scanResults).let { filteredOrtScanResults ->
                filteredOrtScanResults.shouldNotBeNull()
                with(filteredOrtScanResults) {
                    size shouldBe 1
                    first().apply {
                        summary.shouldNotBeNull()
                        with(summary) {
                            size shouldBe 1
                            first().apply {
                                // Only the findings within the VCS path are kept.
                                licenseFindings.size shouldBe 1
                                copyrightFindings.size shouldBe 1
                                snippetFindings.size shouldBe 1
                            }
                        }
                    }
                }
            }
        }
    }
})

private fun createOrtRun(
    db: Database,
    vcsInfo: VcsInfo?,
    processedVcsInfo: VcsInfo?,
    nestedVcsInfo1: VcsInfo,
    nestedVcsInfo2: VcsInfo,
    fixtures: Fixtures,
    repositoryConfigurationRepository: RepositoryConfigurationRepository
) = db.blockingQuery {
    val vcs = vcsInfo?.let(VcsInfoDao::getOrPut)
    val vcsProcessed = processedVcsInfo?.let(VcsInfoDao::getOrPut)
    val vcsNested = mapOf(
        "nested-1" to VcsInfoDao.getOrPut(nestedVcsInfo1),
        "nested-2" to VcsInfoDao.getOrPut(nestedVcsInfo2)
    )

    val ortRunDao = OrtRunDao[fixtures.ortRun.id]
    ortRunDao.vcsId = vcs?.id
    ortRunDao.vcsProcessedId = vcsProcessed?.id
    vcsNested.forEach { nestedRepository ->
        NestedRepositoriesTable.insert {
            it[ortRunId] = fixtures.ortRun.id
            it[vcsId] = nestedRepository.value.id
            it[path] = nestedRepository.key
        }
    }

    val repositoryConfiguration = OrtTestData.repository.config.mapToModel(fixtures.ortRun.id)

    repositoryConfigurationRepository.create(
        ortRunId = ortRunDao.id.value,
        analyzerConfig = repositoryConfiguration.analyzerConfig,
        excludes = repositoryConfiguration.excludes,
        resolutions = repositoryConfiguration.resolutions,
        curations = repositoryConfiguration.curations,
        packageConfigurations = repositoryConfiguration.packageConfigurations,
        licenseChoices = repositoryConfiguration.licenseChoices,
        provenanceSnippetChoices = repositoryConfiguration.provenanceSnippetChoices
    )

    ortRunDao.mapToModel()
}

private fun createVcsInfo(url: String) = VcsInfo(RepositoryType.GIT, url, "revision", "path")

private fun createProvenances(vcsPath: String): Set<ProvenanceResolutionResult> {
    return setOf(
        ProvenanceResolutionResult(
            id = Identifier("NPM", "", "accepts", "1.3.8"),
            packageProvenance = ArtifactProvenance(
                sourceArtifact = RemoteArtifact(
                    url = "https://registry.npmjs.org/accepts/-/accepts-1.3.8.tgz",
                    hashValue = "0bf0be125b67014adcb0b0921e62db7bffe16b2e",
                    hashAlgorithm = "SHA-1"
                )
            )
        ),
        ProvenanceResolutionResult(
            id = Identifier("NPM", "", "ocaas-test-reference", "0.0.1"),
            packageProvenance = RepositoryProvenance(
                vcsInfo = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "https://github.com/bosch-ocaas/ocaas-test-projects.git",
                    revision = "05f320aff2e2b565f150a48a4f801382f6cf5987",
                    path = vcsPath
                ),
                resolvedRevision = "05f320aff2e2b565f150a48a4f801382f6cf5987"
            )
        )
    )
}

private fun createScanResults(): Set<ScanResult> {
    return setOf(
        ScanResult(
            provenance = RepositoryProvenance(
                vcsInfo = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "https://github.com/bosch-ocaas/ocaas-test-projects.git",
                    revision = "05f320aff2e2b565f150a48a4f801382f6cf5987",
                    path = ""
                ),
                resolvedRevision = "05f320aff2e2b565f150a48a4f801382f6cf5987"
            ),
            scanner = ScannerDetail("scanner", "1.0.0", ""),
            summary = ScanSummary(
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                licenseFindings = setOf(
                    LicenseFinding(
                        spdxLicense = "Apache-2.0",
                        location = TextLocation(
                            path = "gradle-inspector/bootstrap-java17/.ort.yml",
                            startLine = 20,
                            endLine = 20
                        )
                    ),
                    LicenseFinding(
                        spdxLicense = "ISC",
                        location = TextLocation(
                            path = "npm/simple/package.json",
                            startLine = 6,
                            endLine = 6
                        )
                    ),
                ),
                copyrightFindings = setOf(
                    CopyrightFinding(
                        statement = "Copyright (c) 2015-2021 the original",
                        location = TextLocation(
                            path = "gradle-inspector/bootstrap-java17/gradlew",
                            startLine = 4,
                            endLine = 4
                        )
                    ),
                    CopyrightFinding(
                        statement = "Copyright (c) 2024 the test team",
                        location = TextLocation(
                            path = "npm/simple/README.md",
                            startLine = 1,
                            endLine = 1
                        )
                    ),
                ),
                snippetFindings = setOf(
                    SnippetFinding(
                        location = TextLocation(
                            path = "gradle-inspector/bootstrap-java17/gradlew",
                            startLine = 1,
                            endLine = 10
                        ),
                        snippets = emptySet()
                    ),
                    SnippetFinding(
                        location = TextLocation(
                            path = "npm/simple/README.md",
                            startLine = 1,
                            endLine = 10
                        ),
                        snippets = emptySet()
                    )
                )
            ),
            additionalData = emptyMap()
        )
    )
}
