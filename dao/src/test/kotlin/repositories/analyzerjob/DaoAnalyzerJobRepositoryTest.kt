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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.repositories.WorkerJobRepositoryTest
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoAnalyzerJobRepositoryTest : WorkerJobRepositoryTest<AnalyzerJob>() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var analyzerJobRepository: DaoAnalyzerJobRepository
    private lateinit var jobConfigurations: JobConfigurations

    private var ortRunId = -1L

    override fun createJob() = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

    override fun getJobRepository() = analyzerJobRepository

    init {
        beforeEach {
            analyzerJobRepository = dbExtension.fixtures.analyzerJobRepository
            jobConfigurations = dbExtension.fixtures.jobConfigurations

            ortRunId = dbExtension.fixtures.ortRun.id
        }

        "create should create an entry in the database" {
            val createdAnalyzerJob =
                analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            val dbEntry = analyzerJobRepository.get(createdAnalyzerJob.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe AnalyzerJob(
                id = createdAnalyzerJob.id,
                ortRunId = ortRunId,
                createdAt = createdAnalyzerJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = jobConfigurations.analyzer,
                status = JobStatus.CREATED
            )
        }

        "getForOrtRun should return the job for a run" {
            val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            analyzerJobRepository.getForOrtRun(ortRunId) shouldBe analyzerJob
        }

        "get should return null" {
            analyzerJobRepository.get(1L).shouldBeNull()
        }

        "get should return the job" {
            val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            analyzerJobRepository.get(analyzerJob.id) shouldBe analyzerJob
        }

        "update should update an entry in the database" {
            val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            val updateStartedAt = Clock.System.now().asPresent()
            val updatedFinishedAt = Clock.System.now().asPresent()
            val updateStatus = JobStatus.FINISHED.asPresent()

            val updateResult =
                analyzerJobRepository.update(analyzerJob.id, updateStartedAt, updatedFinishedAt, updateStatus)

            updateResult shouldBe analyzerJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
            analyzerJobRepository.get(analyzerJob.id) shouldBe analyzerJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
        }

        "delete should delete the database entry" {
            val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            analyzerJobRepository.delete(analyzerJob.id)

            analyzerJobRepository.get(analyzerJob.id) shouldBe null
        }
    }
}
