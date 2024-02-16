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

package org.ossreviewtoolkit.server.dao.repositories

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.ReporterJobDao
import org.ossreviewtoolkit.server.dao.tables.ReporterJobsTable
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.model.repositories.ReporterJobRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue

class DaoReporterJobRepository(private val db: Database) : ReporterJobRepository {
    override fun create(ortRunId: Long, configuration: ReporterJobConfiguration): ReporterJob = db.blockingQuery {
        ReporterJobDao.new {
            ortRun = OrtRunDao[ortRunId]
            createdAt = Clock.System.now()
            this.configuration = configuration
            status = JobStatus.CREATED
        }.mapToModel()
    }

    override fun get(id: Long): ReporterJob? = db.entityQuery { ReporterJobDao[id].mapToModel() }

    override fun getForOrtRun(ortRunId: Long): ReporterJob? = db.blockingQuery {
        ReporterJobDao.find { ReporterJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()?.mapToModel()
    }

    override fun update(
        id: Long,
        startedAt: OptionalValue<Instant?>,
        finishedAt: OptionalValue<Instant?>,
        status: OptionalValue<JobStatus>
    ): ReporterJob = db.blockingQuery {
        val reporterJob = ReporterJobDao[id]

        startedAt.ifPresent { reporterJob.startedAt = it }
        finishedAt.ifPresent { reporterJob.finishedAt = it }
        status.ifPresent { reporterJob.status = it }

        ReporterJobDao[id].mapToModel()
    }

    override fun delete(id: Long) = db.blockingQuery { ReporterJobDao[id].delete() }
}
