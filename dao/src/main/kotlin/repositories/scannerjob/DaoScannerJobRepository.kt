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

package org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and

class DaoScannerJobRepository(private val db: Database) : ScannerJobRepository {
    override fun create(ortRunId: Long, configuration: ScannerJobConfiguration): ScannerJob = db.blockingQuery {
        ScannerJobDao.new {
            this.ortRunId = ortRunId
            createdAt = Clock.System.now()
            this.configuration = configuration
            status = JobStatus.CREATED
        }.mapToModel()
    }

    override fun get(id: Long) = db.entityQuery { ScannerJobDao[id].mapToModel() }

    override fun getForOrtRun(ortRunId: Long): ScannerJob? = db.blockingQuery {
        ScannerJobDao.find { ScannerJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()?.mapToModel()
    }

    override fun update(
        id: Long,
        startedAt: OptionalValue<Instant?>,
        finishedAt: OptionalValue<Instant?>,
        status: OptionalValue<JobStatus>
    ): ScannerJob = db.blockingQuery {
        val scannerJob = ScannerJobDao[id]

        startedAt.ifPresent { scannerJob.startedAt = it }
        finishedAt.ifPresent { scannerJob.finishedAt = it }
        status.ifPresent { scannerJob.status = it }

        ScannerJobDao[id].mapToModel()
    }

    override fun listActive(before: Instant?): List<ScannerJob> = db.blockingQuery {
        ScannerJobDao.find {
            val opFinished = ScannerJobsTable.finishedAt eq null
            before?.let { opFinished and (ScannerJobsTable.createdAt lessEq it) } ?: opFinished
        }.map { it.mapToModel() }
    }

    override fun delete(id: Long) = db.blockingQuery { ScannerJobDao[id].delete() }
}
