/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and

class DaoNotifierJobRepository(private val db: Database) : NotifierJobRepository {
    override fun create(ortRunId: Long, configuration: NotifierJobConfiguration): NotifierJob = db.blockingQuery {
        NotifierJobDao.new {
            this.ortRunId = ortRunId
            createdAt = Clock.System.now()
            this.configuration = configuration
            status = JobStatus.CREATED
        }.mapToModel()
    }

    override fun get(id: Long): NotifierJob? = db.entityQuery { NotifierJobDao[id].mapToModel() }

    override fun getForOrtRun(ortRunId: Long): NotifierJob? = db.blockingQuery {
        NotifierJobDao.find { NotifierJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()?.mapToModel()
    }

    override fun update(
        id: Long,
        startedAt: OptionalValue<Instant?>,
        finishedAt: OptionalValue<Instant?>,
        status: OptionalValue<JobStatus>
    ): NotifierJob = db.blockingQuery {
        val notifierJob = NotifierJobDao[id]

        startedAt.ifPresent { notifierJob.startedAt = it }
        finishedAt.ifPresent { notifierJob.finishedAt = it }
        status.ifPresent { notifierJob.status = it }

        notifierJob.mapToModel()
    }

    override fun listActive(before: Instant?): List<NotifierJob> = db.blockingQuery {
        NotifierJobDao.find {
            val opFinished = NotifierJobsTable.finishedAt eq null
            before?.let { opFinished and (NotifierJobsTable.createdAt lessEq it) } ?: opFinished
        }.map { it.mapToModel() }
    }

    override fun delete(id: Long) = db.blockingQuery { NotifierJobDao[id].delete() }

    override fun deleteMailRecipients(id: Long): NotifierJob = db.blockingQuery {
        val notifierJob = NotifierJobDao[id]
        notifierJob.configuration = notifierJob.configuration.copy(
            mail = notifierJob.configuration.mail?.copy(
                recipientAddresses = emptyList()
            )
        )

        notifierJob.mapToModel()
    }
}
