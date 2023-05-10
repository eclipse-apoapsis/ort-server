/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.EvaluatorJobDao
import org.ossreviewtoolkit.server.dao.tables.EvaluatorJobsTable
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue

class DaoEvaluatorJobRepository : EvaluatorJobRepository {
    override fun create(ortRunId: Long, configuration: EvaluatorJobConfiguration): EvaluatorJob = blockingQuery {
        EvaluatorJobDao.new {
            ortRun = OrtRunDao[ortRunId]
            createdAt = Clock.System.now()
            this.configuration = configuration
            status = JobStatus.CREATED
        }.mapToModel()
    }

    override fun get(id: Long) = entityQuery { EvaluatorJobDao[id].mapToModel() }

    override fun getForOrtRun(ortRunId: Long): EvaluatorJob? = blockingQuery {
        EvaluatorJobDao.find { EvaluatorJobsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()?.mapToModel()
    }

    override fun update(
        id: Long,
        startedAt: OptionalValue<Instant?>,
        finishedAt: OptionalValue<Instant?>,
        status: OptionalValue<JobStatus>
    ): EvaluatorJob = blockingQuery {
        val evaluatorJob = EvaluatorJobDao[id]

        startedAt.ifPresent { evaluatorJob.startedAt = it }
        finishedAt.ifPresent { evaluatorJob.finishedAt = it }
        status.ifPresent { evaluatorJob.status = it }

        EvaluatorJobDao[id].mapToModel()
    }

    override fun delete(id: Long) = blockingQuery { EvaluatorJobDao[id].delete() }
}
