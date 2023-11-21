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

package org.ossreviewtoolkit.server.workers.common.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import kotlinx.datetime.Clock

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.PackageCuration as OrtPackageCuration
import org.ossreviewtoolkit.model.PackageCurationData as OrtPackageCurationData
import org.ossreviewtoolkit.model.RemoteArtifact
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
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.NestedRepositoriesTable
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.RepositoryConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.ResolvedConfigurationRepository
import org.ossreviewtoolkit.server.model.resolvedconfiguration.PackageCurationProviderConfig
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedPackageCurations
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.EvaluatorRun
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtRuleViolation
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.reporter.Report
import org.ossreviewtoolkit.server.model.runs.reporter.ReporterRun
import org.ossreviewtoolkit.server.model.runs.repository.IssueResolution
import org.ossreviewtoolkit.server.model.runs.repository.PackageConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCuration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCurationData
import org.ossreviewtoolkit.server.model.runs.repository.Resolutions
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.OrtTestData
import org.ossreviewtoolkit.server.workers.common.mapToModel
import org.ossreviewtoolkit.server.workers.common.mapToOrt
import org.ossreviewtoolkit.utils.common.gibibytes
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

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
                    archive = null,
                    createMissingArchives = true,
                    detectedLicenseMappings = mapOf("license-1" to "spdx-license-1"),
                    config = emptyMap(),
                    storages = emptyMap(),
                    storageReaders = listOf("reader-1"),
                    storageWriters = listOf("writer-1"),
                    ignorePatterns = listOf("pattern-1"),
                    provenanceStorage = null
                ),
                provenances = emptySet(),
                scanResults = emptySet()
            )

            service.createScannerRun(scannerRun.scannerJobId)
            service.finalizeScannerRun(scannerRun)

            fixtures.scannerRunRepository.getByJobId(fixtures.scannerJob.id) shouldBe scannerRun
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
                advisorRecords = emptyMap()
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
                config = AnalyzerConfiguration(),
                projects = emptySet(),
                packages = emptySet(),
                issues = emptyMap(),
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
            val ortRun = fixtures.ortRun

            val exception = shouldThrow<IllegalArgumentException> {
                service.getOrtRepositoryInformation(ortRun)
            }

            exception.message shouldBe "VCS information is missing from ORT run '1'."
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
                    archive = null,
                    createMissingArchives = true,
                    detectedLicenseMappings = emptyMap(),
                    storages = emptyMap(),
                    config = emptyMap(),
                    storageReaders = null,
                    storageWriters = null,
                    ignorePatterns = emptyList(),
                    provenanceStorage = null
                )
            )

            service.getScannerRunForOrtRun(fixtures.ortRun.id) shouldBe createdScannerRun
        }

        "return null if no scanner run for the ORT run exists" {
            service.getScannerRunForOrtRun(-1L) should beNull()
        }
    }

    "startAdvisorJob" should {
        "start the job" {
            with(service.startAdvisorJob(fixtures.advisorJob.id)) {
                this.shouldNotBeNull()
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
            with(service.startAnalyzerJob(fixtures.analyzerJob.id)) {
                this.shouldNotBeNull()
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
            with(service.startEvaluatorJob(fixtures.evaluatorJob.id)) {
                this.shouldNotBeNull()
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
            with(service.startReporterJob(fixtures.reporterJob.id)) {
                this.shouldNotBeNull()
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
            with(service.startScannerJob(fixtures.scannerJob.id)) {
                this.shouldNotBeNull()
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
                advisorRecords = emptyMap()
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
                config = AnalyzerConfiguration(packageManagers = emptyMap()),
                projects = emptySet(),
                packages = emptySet(),
                issues = emptyMap(),
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
                        severity = "ERROR",
                        message = "the rule is violated",
                        howToFix = "how to fix info"
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
                reports = listOf(Report("report1.zip"), Report("report2.zip"))
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
                                binaryArtifact = RemoteArtifact(
                                    url = "binary URL",
                                    Hash.create("1234567890abcdef1234567890abcdef12345678")
                                ),
                                sourceArtifact = RemoteArtifact(
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
})

private fun createOrtRun(
    db: Database,
    vcsInfo: VcsInfo,
    processedVcsInfo: VcsInfo,
    nestedVcsInfo1: VcsInfo,
    nestedVcsInfo2: VcsInfo,
    fixtures: Fixtures,
    repositoryConfigurationRepository: RepositoryConfigurationRepository
) = db.blockingQuery {
    val vcs = VcsInfoDao.getOrPut(vcsInfo)
    val vcsProcessed = VcsInfoDao.getOrPut(processedVcsInfo)
    val vcsNested = mapOf(
        "nested-1" to VcsInfoDao.getOrPut(nestedVcsInfo1),
        "nested-2" to VcsInfoDao.getOrPut(nestedVcsInfo2)
    )

    val ortRunDao = OrtRunDao[fixtures.ortRun.id]
    ortRunDao.vcsId = vcs.id
    ortRunDao.vcsProcessedId = vcsProcessed.id
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
        licenseChoices = repositoryConfiguration.licenseChoices
    )

    ortRunDao.mapToModel()
}

private fun createVcsInfo(url: String) = VcsInfo(RepositoryType.GIT, url, "revision", "path")
