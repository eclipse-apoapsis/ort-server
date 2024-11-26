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

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.repositories.WorkerJobRepositoryTest
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoEvaluatorJobRepositoryTest : WorkerJobRepositoryTest<EvaluatorJob>() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var evaluatorJobRepository: DaoEvaluatorJobRepository
    private lateinit var jobConfigurations: JobConfigurations
    private lateinit var evaluatorJobConfiguration: EvaluatorJobConfiguration

    private var ortRunId = -1L

    override fun createJob() = evaluatorJobRepository.create(ortRunId, evaluatorJobConfiguration)

    override fun getJobRepository() = evaluatorJobRepository

    init {
        beforeEach {
            evaluatorJobRepository = dbExtension.fixtures.evaluatorJobRepository
            jobConfigurations = dbExtension.fixtures.jobConfigurations
            evaluatorJobConfiguration = jobConfigurations.evaluator!!

            ortRunId = dbExtension.fixtures.ortRun.id
        }

        "create should create an entry in the database" {
            val createdEvaluatorJob = evaluatorJobRepository.create(ortRunId, evaluatorJobConfiguration)

            val dbEntry = evaluatorJobRepository.get(createdEvaluatorJob.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe EvaluatorJob(
                id = createdEvaluatorJob.id,
                ortRunId = ortRunId,
                createdAt = createdEvaluatorJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = evaluatorJobConfiguration,
                status = JobStatus.CREATED,
            )
        }

        "getForOrtRun should return the job for a run" {
            val evaluatorJob = evaluatorJobRepository.create(ortRunId, evaluatorJobConfiguration)

            evaluatorJobRepository.getForOrtRun(ortRunId) shouldBe evaluatorJob
        }

        "get should return null" {
            evaluatorJobRepository.get(1L).shouldBeNull()
        }

        "get should return the job" {
            val evaluatorJob = evaluatorJobRepository.create(ortRunId, evaluatorJobConfiguration)

            evaluatorJobRepository.get(evaluatorJob.id) shouldBe evaluatorJob
        }

        "update should update an entry in the database" {
            val evaluatorJob = evaluatorJobRepository.create(ortRunId, evaluatorJobConfiguration)

            val updateStartedAt = Clock.System.now().asPresent()
            val updatedFinishedAt = Clock.System.now().asPresent()
            val updateStatus = JobStatus.FINISHED.asPresent()

            val updateResult =
                evaluatorJobRepository.update(evaluatorJob.id, updateStartedAt, updatedFinishedAt, updateStatus)

            updateResult shouldBe evaluatorJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
            evaluatorJobRepository.get(evaluatorJob.id) shouldBe evaluatorJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
        }

        "delete should delete the database entry" {
            val evaluatorJob = evaluatorJobRepository.create(ortRunId, evaluatorJobConfiguration)

            evaluatorJobRepository.delete(evaluatorJob.id)

            evaluatorJobRepository.get(evaluatorJob.id) shouldBe null
        }
    }
}
