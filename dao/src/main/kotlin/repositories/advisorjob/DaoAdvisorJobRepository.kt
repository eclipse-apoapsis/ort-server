/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob

import kotlin.time.Clock
import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.getEntityOrNull
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.transactionCatching
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database

class DaoAdvisorJobRepository(private val db: Database) : AdvisorJobRepository {
    override suspend fun create(ortRunId: Long, configuration: AdvisorJobConfiguration): AdvisorJob = db.transaction {
        AdvisorJobDao.new {
            this.ortRunId = ortRunId
            createdAt = Clock.System.now()
            this.configuration = configuration
            status = JobStatus.CREATED
        }.mapToModel()
    }

    override suspend fun get(id: Long) = db.transactionCatching { AdvisorJobDao[id].mapToModel() }.getEntityOrNull()

    override suspend fun getForOrtRun(ortRunId: Long): AdvisorJob? = db.transaction {
        AdvisorJobDao.find { AdvisorJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()?.mapToModel()
    }

    override suspend fun update(
        id: Long,
        startedAt: OptionalValue<Instant?>,
        finishedAt: OptionalValue<Instant?>,
        status: OptionalValue<JobStatus>,
        errorMessage: OptionalValue<String>
    ): AdvisorJob = db.transaction {
        val advisorJob = AdvisorJobDao[id]

        startedAt.ifPresent { advisorJob.startedAt = it }
        finishedAt.ifPresent { advisorJob.finishedAt = it }
        status.ifPresent { advisorJob.status = it }
        errorMessage.ifPresent { advisorJob.errorMessage = it }

        AdvisorJobDao[id].mapToModel()
    }

    override suspend fun listActive(before: Instant?): List<AdvisorJob> = db.transaction {
        AdvisorJobDao.find {
            val opFinished = AdvisorJobsTable.finishedAt eq null
            before?.let { opFinished and (AdvisorJobsTable.createdAt lessEq it) } ?: opFinished
        }.map { it.mapToModel() }
    }

    override suspend fun delete(id: Long) = db.transaction { AdvisorJobDao[id].delete() }
}
