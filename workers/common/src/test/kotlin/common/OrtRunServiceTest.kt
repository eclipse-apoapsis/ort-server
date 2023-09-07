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
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.NestedRepositoriesTable
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.RepositoryConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.ResolvedConfigurationRepository
import org.ossreviewtoolkit.server.model.resolvedconfiguration.PackageCurationProviderConfig
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedPackageCurations
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.IssueResolution
import org.ossreviewtoolkit.server.model.runs.repository.PackageConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCuration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCurationData
import org.ossreviewtoolkit.server.model.runs.repository.Resolutions
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.OrtTestData
import org.ossreviewtoolkit.server.workers.common.mapToModel
import org.ossreviewtoolkit.server.workers.common.mapToOrt
import org.ossreviewtoolkit.utils.common.gibibytes

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
            resolvedConfigurationRepository,
            fixtures.scannerJobRepository,
            fixtures.scannerRunRepository
        )
    }

    "getAdvisorJob" should {
        "return the advisor job" {
            fixtures.advisorJob
            service.getAdvisorJob(fixtures.ortRun.id) shouldBe fixtures.advisorJob
        }

        "return null if the advisor job does not exist" {
            service.getAdvisorJob(-1L) should beNull()
        }
    }

    "getAdvisorRun" should {
        "return the advisor run" {
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
                config = AdvisorConfiguration(null, null, null, null, emptyMap()),
                advisorRecords = emptyMap()
            )

            service.getAdvisorRun(fixtures.ortRun.id) shouldBe createdAdvisorRun
        }

        "return null if the advisor run does not exist" {
            service.getAdvisorRun(-1L) should beNull()
        }
    }

    "getAnalyzerJob" should {
        "return the analyzer job" {
            fixtures.analyzerJob
            service.getAnalyzerJob(fixtures.ortRun.id) shouldBe fixtures.analyzerJob
        }

        "return null if the advisor job does not exist" {
            service.getAnalyzerJob(-1L) should beNull()
        }
    }

    "getAnalyzerRun" should {
        "return the analyzer run" {
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

            service.getAnalyzerRun(fixtures.ortRun.id) shouldBe createdAnalyzerRun
        }

        "return null if the analyzer run does not exist" {
            service.getAnalyzerRun(-1L) should beNull()
        }
    }

    "getEvaluatorJob" should {
        "return the evaluator job" {
            fixtures.evaluatorJob
            service.getEvaluatorJob(fixtures.ortRun.id) shouldBe fixtures.evaluatorJob
        }

        "return null if the evaluator job does not exist" {
            service.getEvaluatorJob(-1L) should beNull()
        }
    }

    "getEvaluatorRun" should {
        "return the evaluator run" {
            val createdEvaluatorRun = dbExtension.fixtures.evaluatorRunRepository.create(
                evaluatorJobId = fixtures.evaluatorJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                violations = emptyList()
            )

            service.getEvaluatorRun(fixtures.ortRun.id) shouldBe createdEvaluatorRun
        }

        "return null if the evaluator run does not exist" {
            service.getEvaluatorRun(-1L) should beNull()
        }
    }

    "getOrtRepositoryInformation" should {
        "return ORT repository object" {
            val vcsInfo = getVcsInfo("https://example.com/repo.git")
            val processedVcsInfo = getVcsInfo("https://example.com/repo-processed.git")
            val nestedVcsInfo1 = getVcsInfo("https://example.com/repo-nested-1.git")
            val nestedVcsInfo2 = getVcsInfo("https://example.com/repo-nested-2.git")

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
            service.getReporterJob(fixtures.ortRun.id) shouldBe fixtures.reporterJob
        }

        "return null if the reporter job does not exist" {
            service.getReporterJob(-1L) should beNull()
        }
    }

    "getReporterRun" should {
        "return the reporter run" {
            val createdReporterRun = dbExtension.fixtures.reporterRunRepository.create(
                reporterJobId = fixtures.reporterJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                reports = emptyList()
            )

            service.getReporterRun(fixtures.ortRun.id) shouldBe createdReporterRun
        }

        "return null if the reporter run does not exist" {
            service.getReporterRun(-1L) should beNull()
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
            service.getScannerJob(fixtures.ortRun.id) shouldBe fixtures.scannerJob
        }

        "return null if the scanner job does not exist" {
            service.getScannerJob(-1L) should beNull()
        }
    }

    "getScannerRun" should {
        "return the scanner run" {
            val createdScannerRun = dbExtension.fixtures.scannerRunRepository.create(
                scannerJobId = fixtures.scannerJob.id,
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
                    options = emptyMap(),
                    storageReaders = null,
                    storageWriters = null,
                    ignorePatterns = emptyList(),
                    provenanceStorage = null
                )
            )

            service.getScannerRun(fixtures.ortRun.id) shouldBe createdScannerRun
        }

        "return null if the scanner run does not exist" {
            service.getScannerRun(-1L) should beNull()
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

private fun getVcsInfo(url: String) = VcsInfo(RepositoryType.GIT, url, "revision", "path")
