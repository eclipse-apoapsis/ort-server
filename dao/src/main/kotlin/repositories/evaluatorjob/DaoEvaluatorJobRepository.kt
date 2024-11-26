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

package org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and

class DaoEvaluatorJobRepository(private val db: Database) : EvaluatorJobRepository {
    override fun create(ortRunId: Long, configuration: EvaluatorJobConfiguration): EvaluatorJob = db.blockingQuery {
        EvaluatorJobDao.new {
            ortRun = OrtRunDao[ortRunId]
            createdAt = Clock.System.now()
            this.configuration = configuration
            status = JobStatus.CREATED
        }.mapToModel()
    }

    override fun get(id: Long) = db.entityQuery { EvaluatorJobDao[id].mapToModel() }

    override fun getForOrtRun(ortRunId: Long): EvaluatorJob? = db.blockingQuery {
        EvaluatorJobDao.find { EvaluatorJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()?.mapToModel()
    }

    override fun update(
        id: Long,
        startedAt: OptionalValue<Instant?>,
        finishedAt: OptionalValue<Instant?>,
        status: OptionalValue<JobStatus>
    ): EvaluatorJob = db.blockingQuery {
        val evaluatorJob = EvaluatorJobDao[id]

        startedAt.ifPresent { evaluatorJob.startedAt = it }
        finishedAt.ifPresent { evaluatorJob.finishedAt = it }
        status.ifPresent { evaluatorJob.status = it }

        EvaluatorJobDao[id].mapToModel()
    }

    override fun listActive(before: Instant?): List<EvaluatorJob> = db.blockingQuery {
        EvaluatorJobDao.find {
            val opFinished = EvaluatorJobsTable.finishedAt eq null
            before?.let { opFinished and (EvaluatorJobsTable.createdAt lessEq it) } ?: opFinished
        }.map { it.mapToModel() }
    }

    override fun delete(id: Long) = db.blockingQuery { EvaluatorJobDao[id].delete() }
}
