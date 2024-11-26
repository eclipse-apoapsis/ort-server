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

package org.eclipse.apoapsis.ortserver.dao.repositories.reporterrun

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.ReporterJobDao
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.reporter.ReporterRun

import org.jetbrains.exposed.sql.Database

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
        val reportsList = mapAndDeduplicate(reports) {
            ReportDao.new {
                filename = it.filename
                downloadLink = it.downloadLink
                downloadTokenExpiryDate = it.downloadTokenExpiryDate
            }
        }

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
