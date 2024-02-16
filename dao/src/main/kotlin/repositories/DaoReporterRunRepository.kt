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

import kotlinx.datetime.Instant

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.mapAndDeduplicate
import org.ossreviewtoolkit.server.dao.tables.ReporterJobDao
import org.ossreviewtoolkit.server.dao.tables.runs.reporter.ReportDao
import org.ossreviewtoolkit.server.dao.tables.runs.reporter.ReporterRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.reporter.ReporterRunsTable
import org.ossreviewtoolkit.server.model.repositories.ReporterRunRepository
import org.ossreviewtoolkit.server.model.runs.reporter.Report
import org.ossreviewtoolkit.server.model.runs.reporter.ReporterRun

/**
 * An implementation of [ReporterRunRepository] that stores reporter runs in [ReporterRunsTable].
 */
class DaoReporterRunRepository(private val db: Database) : ReporterRunRepository {
    override fun create(
        reporterJobId: Long,
        startTime: Instant,
        endTime: Instant,
        reports: List<Report>
    ): ReporterRun = db.blockingQuery {
        val reportsList = mapAndDeduplicate(reports) { ReportDao.new { filename = it.filename } }

        ReporterRunDao.new {
            this.reporterJob = ReporterJobDao[reporterJobId]
            this.startTime = startTime
            this.endTime = endTime
            this.reports = reportsList
        }.mapToModel()
    }

    override fun get(id: Long): ReporterRun? = db.entityQuery { ReporterRunDao[id].mapToModel() }

    override fun getByJobId(reporterJobId: Long): ReporterRun? = db.entityQuery {
        ReporterRunDao.find { ReporterRunsTable.reporterJobId eq reporterJobId }.firstOrNull()?.mapToModel()
    }
}
