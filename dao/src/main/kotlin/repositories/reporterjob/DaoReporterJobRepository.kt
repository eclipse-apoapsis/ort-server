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

package org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.tables.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.ReporterJobDao
import org.eclipse.apoapsis.ortserver.dao.tables.ReporterJobsTable
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and

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
        findJobForOrtRun(ortRunId)?.mapToModel()
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

    override fun listActive(before: Instant?): List<ReporterJob> = db.blockingQuery {
        ReporterJobDao.find {
            val opFinished = ReporterJobsTable.finishedAt eq null
            before?.let { opFinished and (ReporterJobsTable.createdAt lessEq it) } ?: opFinished
        }.map { it.mapToModel() }
    }

    override fun delete(id: Long) = db.blockingQuery { ReporterJobDao[id].delete() }

    override fun getReportByToken(ortRunId: Long, token: String): Report? = db.blockingQuery {
        val time = Clock.System.now()
        val linkSuffix = "/downloads/report/$token"
        findJobForOrtRun(ortRunId)?.reporterRun?.reports?.find {
            it.downloadTokenExpiryDate > time && it.downloadLink.endsWith(linkSuffix)
        }?.mapToModel()
    }

    override fun getNonExpiredReports(ortRunId: Long) = db.blockingQuery {
        val time = Clock.System.now()
        findJobForOrtRun(ortRunId)?.reporterRun?.reports?.filter {
            it.downloadTokenExpiryDate > time
        }?.map { it.mapToModel() }.orEmpty()
    }

    /**
     * Return the [ReporterJobDao] for the given [ortRunId], or *null* if no job exists for the run.
     */
    private fun findJobForOrtRun(ortRunId: Long) =
        ReporterJobDao.find { ReporterJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()
}
