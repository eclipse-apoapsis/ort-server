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

import kotlin.time.Clock
import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.getEntityOrNull
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.transactionCatching
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database

class DaoNotifierJobRepository(private val db: Database) : NotifierJobRepository {
    override suspend fun create(ortRunId: Long, configuration: NotifierJobConfiguration): NotifierJob = db.transaction {
        NotifierJobDao.new {
            this.ortRunId = ortRunId
            createdAt = Clock.System.now()
            this.configuration = configuration
            status = JobStatus.CREATED
        }.mapToModel()
    }

    override suspend fun get(id: Long): NotifierJob? =
        db.transactionCatching { NotifierJobDao[id].mapToModel() }.getEntityOrNull()

    override suspend fun getForOrtRun(ortRunId: Long): NotifierJob? = db.transaction {
        NotifierJobDao.find { NotifierJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()?.mapToModel()
    }

    override suspend fun update(
        id: Long,
        startedAt: OptionalValue<Instant?>,
        finishedAt: OptionalValue<Instant?>,
        status: OptionalValue<JobStatus>,
        errorMessage: OptionalValue<String>
    ): NotifierJob = db.transaction {
        val notifierJob = NotifierJobDao[id]

        startedAt.ifPresent { notifierJob.startedAt = it }
        finishedAt.ifPresent { notifierJob.finishedAt = it }
        status.ifPresent { notifierJob.status = it }
        errorMessage.ifPresent { notifierJob.errorMessage = it }

        notifierJob.mapToModel()
    }

    override suspend fun listActive(before: Instant?): List<NotifierJob> = db.transaction {
        NotifierJobDao.find {
            val opFinished = NotifierJobsTable.finishedAt eq null
            before?.let { opFinished and (NotifierJobsTable.createdAt lessEq it) } ?: opFinished
        }.map { it.mapToModel() }
    }

    override suspend fun delete(id: Long) = db.transaction { NotifierJobDao[id].delete() }

    override suspend fun deleteMailRecipients(id: Long): NotifierJob = db.transaction {
        val notifierJob = NotifierJobDao[id]
        notifierJob.configuration = notifierJob.configuration.copy(
            recipientAddresses = emptyList()
        )

        notifierJob.mapToModel()
    }
}
