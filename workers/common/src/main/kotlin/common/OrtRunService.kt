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

import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ScannerJob
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterJobRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.ResolvedConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration

class OrtRunService(
    private val db: Database,
    private val advisorJobRepository: AdvisorJobRepository,
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val evaluatorJobRepository: EvaluatorJobRepository,
    private val ortRunRepository: OrtRunRepository,
    private val reporterJobRepository: ReporterJobRepository,
    private val repositoryConfigurationRepository: RepositoryConfigurationRepository,
    private val resolvedConfigurationRepository: ResolvedConfigurationRepository,
    private val scannerJobRepository: ScannerJobRepository
) {
    /**
     * Return the [AdvisorJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getAdvisorJob(ortRunId: Long) = db.blockingQuery { advisorJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [AnalyzerJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getAnalyzerJob(ortRunId: Long) = db.blockingQuery { analyzerJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the [EvaluatorJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getEvaluatorJob(ortRunId: Long) = db.blockingQuery { evaluatorJobRepository.getForOrtRun(ortRunId) }

    /**
     * Fetch the repository data from the database and construct an ORT [Repository] object from a provided ORT run.
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
     * Return the [ReporterJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getReporterJob(ortRunId: Long) = db.blockingQuery { reporterJobRepository.getForOrtRun(ortRunId) }

    /**
     * Return the resolved configuration for the provided [ortRun]. If no resolved configuration is stored, an empty
     * resolved configuration is returned.
     */
    fun getResolvedConfiguration(ortRun: OrtRun) = db.blockingQuery {
        resolvedConfigurationRepository.getForOrtRun(ortRun.id) ?: ResolvedConfiguration()
    }

    /**
     * Return the [ScannerJob] for the provided [ortRunId] or `null` if the job does not exist.
     */
    fun getScannerJob(ortRunId: Long) = db.blockingQuery { scannerJobRepository.getForOrtRun(ortRunId) }
}
