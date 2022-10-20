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

package org.ossreviewtoolkit.server.orchestrator

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobStatus
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue

/**
 * This service is responsible for creating worker jobs for ORT runs.
 */
class OrchestratorService(
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val ortRunRepository: OrtRunRepository,
    private val repositoryRepository: RepositoryRepository,
    private val schedulerService: SchedulerService
) {
    suspend fun createOrtRun(repositoryId: Long, revision: String, config: JobConfigurations): OrtRun = dbQuery {
        val repository = repositoryRepository.get(repositoryId)
        val ortRun = ortRunRepository.create(repositoryId, revision, config)
        val analyzerJob = analyzerJobRepository.create(ortRunId = ortRun.id, configuration = config.analyzer)

        require(repository != null)

        runBlocking { schedulerService.scheduleAnalyzerJob(repository, ortRun, analyzerJob) }.getOrThrow()

        analyzerJobRepository.update(analyzerJob.id, status = OptionalValue.Present(AnalyzerJobStatus.SCHEDULED))

        ortRun
    }.getOrThrow()

    /**
     * Get an analyzer job by [analyzerJobId]. Returns null if the analyzer job is not found.
     */
    suspend fun getAnalyzerJob(analyzerJobId: Long): AnalyzerJob? = dbQuery {
        analyzerJobRepository.get(analyzerJobId)
    }.getOrNull()

    /**
     * If a [scheduled][AnalyzerJobStatus.SCHEDULED]
     */
    suspend fun startAnalyzerJob(): AnalyzerJob? = dbQuery {
        val analyzerJob = analyzerJobRepository.getScheduled()

        if (analyzerJob != null) {
            analyzerJobRepository.update(
                analyzerJob.id,
                startedAt = OptionalValue.Present(Clock.System.now()),
                status = OptionalValue.Present(AnalyzerJobStatus.RUNNING)
            )
        } else null
    }.getOrThrow()

    suspend fun finishAnalyzerJob(analyzerJobId: Long): AnalyzerJob? = dbQuery {
        val analyzerJob = analyzerJobRepository.get(analyzerJobId)

        if (analyzerJob != null) {
            analyzerJobRepository.update(
                analyzerJob.id,
                finishedAt = OptionalValue.Present(Clock.System.now()),
                status = OptionalValue.Present(AnalyzerJobStatus.FINISHED)
            )
        } else null
    }.getOrThrow()
}
