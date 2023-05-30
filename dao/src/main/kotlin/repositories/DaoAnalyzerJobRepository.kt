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

package org.ossreviewtoolkit.server.dao.repositories

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.AnalyzerJobDao
import org.ossreviewtoolkit.server.dao.tables.AnalyzerJobsTable
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue

class DaoAnalyzerJobRepository(private val db: Database) : AnalyzerJobRepository {
    override fun create(ortRunId: Long, configuration: AnalyzerJobConfiguration): AnalyzerJob = db.blockingQuery {
        AnalyzerJobDao.new {
            ortRun = OrtRunDao[ortRunId]
            createdAt = Clock.System.now()
            this.configuration = configuration
            status = JobStatus.CREATED
        }.mapToModel()
    }

    override fun get(id: Long) = db.entityQuery { AnalyzerJobDao[id].mapToModel() }

    override fun getForOrtRun(ortRunId: Long): AnalyzerJob? = db.blockingQuery {
        AnalyzerJobDao.find { AnalyzerJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()?.mapToModel()
    }

    override fun update(
        id: Long,
        startedAt: OptionalValue<Instant?>,
        finishedAt: OptionalValue<Instant?>,
        status: OptionalValue<JobStatus>
    ): AnalyzerJob = db.blockingQuery {
        val analyzerJob = AnalyzerJobDao[id]

        startedAt.ifPresent { analyzerJob.startedAt = it }
        finishedAt.ifPresent { analyzerJob.finishedAt = it }
        status.ifPresent { analyzerJob.status = it }

        AnalyzerJobDao[id].mapToModel()
    }

    override fun delete(id: Long) = db.blockingQuery { AnalyzerJobDao[id].delete() }
}
