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

package org.ossreviewtoolkit.server.workers.analyzer

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.NestedRepositoriesTable
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.workers.common.mapToModel

class AnalyzerWorkerDao(
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val analyzerRunRepository: AnalyzerRunRepository,
    private val db: Database
) {
    fun getAnalyzerJob(analyzerJobId: Long) = analyzerJobRepository.get(analyzerJobId)

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

    fun storeRepositoryInformation(ortResult: OrtResult, job: AnalyzerJob) {
        db.blockingQuery {
            val vcsInfoDao = VcsInfoDao.getOrPut(ortResult.repository.vcs.mapToModel())

            val processedVcsInfoDao = VcsInfoDao.getOrPut(ortResult.repository.vcsProcessed.mapToModel())

            ortResult.repository.nestedRepositories.map { nestedRepository ->
                val nestedVcsInfoDao = VcsInfoDao.getOrPut(nestedRepository.value.mapToModel())

                NestedRepositoriesTable.insert {
                    it[ortRunId] = job.ortRunId
                    it[vcsId] = nestedVcsInfoDao.id
                    it[path] = nestedRepository.key
                }
            }

            val ortRunDao = OrtRunDao[job.ortRunId]
            ortRunDao.vcsId = vcsInfoDao.id
            ortRunDao.vcsProcessedId = processedVcsInfoDao.id
        }
    }
}
