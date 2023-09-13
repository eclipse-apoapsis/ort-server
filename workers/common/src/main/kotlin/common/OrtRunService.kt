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

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.NestedRepositoriesTable
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ScannerJob
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorRunRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterJobRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.repositories.ResolvedConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerRunRepository
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.EvaluatorRun
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.reporter.ReporterRun
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun

@Suppress("LongParameterList", "TooManyFunctions")
class OrtRunService(
    private val db: Database,
    private val advisorJobRepository: AdvisorJobRepository,
    private val advisorRunRepository: AdvisorRunRepository,
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val analyzerRunRepository: AnalyzerRunRepository,
    private val evaluatorJobRepository: EvaluatorJobRepository,
    private val evaluatorRunRepository: EvaluatorRunRepository,
    private val ortRunRepository: OrtRunRepository,
    private val reporterJobRepository: ReporterJobRepository,
    private val reporterRunRepository: ReporterRunRepository,
    private val repositoryConfigurationRepository: RepositoryConfigurationRepository,
    private val repositoryRepository: RepositoryRepository,
    private val resolvedConfigurationRepository: ResolvedConfigurationRepository,
    private val scannerJobRepository: ScannerJobRepository,
    private val scannerRunRepository: ScannerRunRepository
) {
    /**
     * Return the [AdvisorJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getAdvisorJob(id: Long) = db.blockingQuery { advisorJobRepository.get(id) }

    /**
     * Return the [AdvisorJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getAdvisorJobForOrtRun(ortRunId: Long) = db.blockingQuery { advisorJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [AdvisorRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getAdvisorRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getAdvisorJobForOrtRun(ortRunId)?.let { advisorRunRepository.getByJobId(it.id) }
    }

    /**
     * Return the [AnalyzerJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getAnalyzerJob(id: Long) = db.blockingQuery { analyzerJobRepository.get(id) }

    /**
     * Return the [AnalyzerJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getAnalyzerJobForOrtRun(ortRunId: Long) = db.blockingQuery { analyzerJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [AnalyzerRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getAnalyzerRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getAnalyzerJobForOrtRun(ortRunId)?.let { analyzerRunRepository.getByJobId(it.id) }
    }

    /**
     * Return the [EvaluatorJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getEvaluatorJob(id: Long) = db.blockingQuery { evaluatorJobRepository.get(id) }

    /**
     * Return the [EvaluatorJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getEvaluatorJobForOrtRun(ortRunId: Long) = db.blockingQuery { evaluatorJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [EvaluatorRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getEvaluatorRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getEvaluatorJobForOrtRun(ortRunId)?.let { evaluatorRunRepository.getByJobId(it.id) }
    }

    /**
     * Return the [Hierarchy] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getHierarchyForOrtRun(ortRunId: Long) = db.blockingQuery {
        getOrtRun(ortRunId)?.let { repositoryRepository.getHierarchy(it.repositoryId) }
    }

    /**
     * Return the [Repository] information for the provided [OrtRun].
     */
    fun getOrtRepositoryInformation(ortRun: OrtRun) = db.blockingQuery {
        val vcsId = ortRun.vcsId
        requireNotNull(vcsId) {
            "VCS information is missing from ORT run '${ortRun.id}'."
        }

        val vcsProcessedId = ortRun.vcsProcessedId
        requireNotNull(vcsProcessedId) {
            "VCS processed information is missing from ORT run '${ortRun.id}'."
        }

        val nestedRepositoryIds = ortRun.nestedRepositoryIds
        requireNotNull(nestedRepositoryIds) {
            "Nested repositories information is missing from ORT run '${ortRun.id}'."
        }

        val vcsInfo = VcsInfoDao[vcsId].mapToModel()
        val vcsProcessedInfo = VcsInfoDao[vcsProcessedId].mapToModel()
        val nestedRepositories =
            nestedRepositoryIds.map { Pair(it.key, VcsInfoDao[it.value].mapToModel().mapToOrt()) }.toMap()

        val repositoryConfig =
            ortRun.repositoryConfigId?.let { repositoryConfigurationRepository.get(it)?.mapToOrt() }
                ?: RepositoryConfiguration()

        Repository(
            vcs = vcsInfo.mapToOrt(),
            vcsProcessed = vcsProcessedInfo.mapToOrt(),
            nestedRepositories = nestedRepositories,
            config = repositoryConfig
        )
    }

    /**
     * Return the [OrtRun] with the provided [id] or `null` if the ORT run does not exist.
     */
    fun getOrtRun(id: Long) = db.blockingQuery { ortRunRepository.get(id) }

    /**
     * Return the [ReporterJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getReporterJob(id: Long) = db.blockingQuery { reporterJobRepository.get(id) }

    /**
     * Return the [ReporterJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getReporterJobForOrtRun(ortRunId: Long) = db.blockingQuery { reporterJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [ReporterRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getReporterRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getReporterJobForOrtRun(ortRunId)?.let { reporterRunRepository.getByJobId(it.id) }
    }

    /**
     * Return the resolved configuration for the provided [ortRun]. If no resolved configuration is stored, an empty
     * resolved configuration is returned.
     */
    fun getResolvedConfiguration(ortRun: OrtRun) = db.blockingQuery {
        resolvedConfigurationRepository.getForOrtRun(ortRun.id) ?: ResolvedConfiguration()
    }

    /**
     * Return the [ScannerJob] with the provided [id] or `null` if the job does not exist.
     */
    fun getScannerJob(id: Long) = db.blockingQuery { scannerJobRepository.get(id) }

    /**
     * Return the [ScannerJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getScannerJobForOrtRun(ortRunId: Long) = db.blockingQuery { scannerJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [ScannerRun] for the provided [ortRunId] or `null` if the run does not exist.
     */
    fun getScannerRunForOrtRun(ortRunId: Long) = db.blockingQuery {
        getScannerJobForOrtRun(ortRunId)?.let { scannerRunRepository.getByJobId(it.id) }
    }

    /**
     * Store the provided [advisorRun].
     */
    fun storeAdvisorRun(advisorRun: AdvisorRun) {
        advisorRunRepository.create(
            advisorJobId = advisorRun.advisorJobId,
            startTime = advisorRun.startTime,
            endTime = advisorRun.endTime,
            environment = advisorRun.environment,
            config = advisorRun.config,
            advisorRecords = advisorRun.advisorRecords
        )
    }

    /**
     * Store the provided [analyzerRun].
     */
    fun storeAnalyzerRun(analyzerRun: AnalyzerRun) {
        analyzerRunRepository.create(
            analyzerJobId = analyzerRun.analyzerJobId,
            startTime = analyzerRun.startTime,
            endTime = analyzerRun.endTime,
            environment = analyzerRun.environment,
            config = analyzerRun.config,
            projects = analyzerRun.projects,
            packages = analyzerRun.packages,
            issues = analyzerRun.issues,
            dependencyGraphs = analyzerRun.dependencyGraphs
        )
    }

    /**
     * Store the provided [evaluatorRun].
     */
    fun storeEvaluatorRun(evaluatorRun: EvaluatorRun) {
        evaluatorRunRepository.create(
            evaluatorRun.evaluatorJobId,
            evaluatorRun.startTime,
            evaluatorRun.endTime,
            evaluatorRun.violations
        )
    }

    /**
     * Store the provided [reporterRun].
     */
    fun storeReporterRun(reporterRun: ReporterRun) {
        reporterRunRepository.create(
            reporterRun.reporterJobId,
            reporterRun.startTime,
            reporterRun.endTime,
            reporterRun.reports
        )
    }

    /**
     * Store the provided [repositoryInformation] associated with the [ortRunId].
     */
    fun storeRepositoryInformation(ortRunId: Long, repositoryInformation: Repository) {
        db.blockingQuery {
            val vcsInfoDao = VcsInfoDao.getOrPut(repositoryInformation.vcs.mapToModel())

            val processedVcsInfoDao = VcsInfoDao.getOrPut(repositoryInformation.vcsProcessed.mapToModel())

            val repositoryConfiguration = repositoryInformation.config.mapToModel(ortRunId)

            repositoryInformation.nestedRepositories.map { nestedRepository ->
                val nestedVcsInfoDao = VcsInfoDao.getOrPut(nestedRepository.value.mapToModel())

                NestedRepositoriesTable.insert {
                    it[this.ortRunId] = ortRunId
                    it[vcsId] = nestedVcsInfoDao.id
                    it[path] = nestedRepository.key
                }
            }

            repositoryConfigurationRepository.create(
                ortRunId = repositoryConfiguration.ortRunId,
                analyzerConfig = repositoryConfiguration.analyzerConfig,
                excludes = repositoryConfiguration.excludes,
                resolutions = repositoryConfiguration.resolutions,
                curations = repositoryConfiguration.curations,
                packageConfigurations = repositoryConfiguration.packageConfigurations,
                licenseChoices = repositoryConfiguration.licenseChoices
            )

            val ortRunDao = OrtRunDao[ortRunId]
            ortRunDao.vcsId = vcsInfoDao.id
            ortRunDao.vcsProcessedId = processedVcsInfoDao.id
        }
    }

    /**
     * Store the provided resolved [packageConfigurations] associated with the [ortRunId].
     */
    fun storeResolvedPackageConfigurations(ortRunId: Long, packageConfigurations: List<PackageConfiguration>) {
        db.blockingQuery {
            resolvedConfigurationRepository.addPackageConfigurations(
                ortRunId,
                packageConfigurations.map { it.mapToModel() }
            )
        }
    }

    /**
     * Store the provided resolved [packageCurations] associated with the [ortRunId].
     */
    fun storeResolvedPackageCurations(ortRunId: Long, packageCurations: List<ResolvedPackageCurations>) {
        db.blockingQuery {
            resolvedConfigurationRepository.addPackageCurations(ortRunId, packageCurations.map { it.mapToModel() })
        }
    }

    /**
     * Store the provided resolved [resolutions] associated with the [ortRunId].
     */
    fun storeResolvedResolutions(ortRunId: Long, resolutions: Resolutions) {
        db.blockingQuery {
            resolvedConfigurationRepository.addResolutions(ortRunId, resolutions.mapToModel())
        }
    }

    /**
     * Store the provided [scannerRun].
     */
    fun storeScannerRun(scannerRun: ScannerRun) {
        scannerRunRepository.create(
            scannerJobId = scannerRun.scannerJobId,
            startTime = scannerRun.startTime,
            endTime = scannerRun.endTime,
            environment = scannerRun.environment,
            config = scannerRun.config
        )
    }
}
