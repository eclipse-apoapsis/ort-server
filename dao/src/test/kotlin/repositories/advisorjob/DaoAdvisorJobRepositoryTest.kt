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

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.repositories.WorkerJobRepositoryTest
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoAdvisorJobRepositoryTest : WorkerJobRepositoryTest<AdvisorJob>() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var advisorJobRepository: DaoAdvisorJobRepository
    private lateinit var jobConfigurations: JobConfigurations
    private lateinit var advisorJobConfiguration: AdvisorJobConfiguration

    private var ortRunId = -1L

    override fun createJob() = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

    override fun getJobRepository() = advisorJobRepository

    init {
        beforeEach {
            advisorJobRepository = dbExtension.fixtures.advisorJobRepository
            jobConfigurations = dbExtension.fixtures.jobConfigurations
            advisorJobConfiguration = jobConfigurations.advisor!!

            ortRunId = dbExtension.fixtures.ortRun.id
        }

        "create should create an entry in the database" {
            val createdAdvisorJob = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

            val dbEntry = advisorJobRepository.get(createdAdvisorJob.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe AdvisorJob(
                id = createdAdvisorJob.id,
                ortRunId = ortRunId,
                createdAt = createdAdvisorJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = advisorJobConfiguration,
                status = JobStatus.CREATED,
            )
        }

        "getForOrtRun should return the job for a run" {
            val advisorJob = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

            advisorJobRepository.getForOrtRun(ortRunId) shouldBe advisorJob
        }

        "get should return null" {
            advisorJobRepository.get(1L).shouldBeNull()
        }

        "get should return the job" {
            val advisorJob = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

            advisorJobRepository.get(advisorJob.id) shouldBe advisorJob
        }

        "update should update an entry in the database" {
            val advisorJob = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

            val updateStartedAt = Clock.System.now().asPresent()
            val updatedFinishedAt = Clock.System.now().asPresent()
            val updateStatus = JobStatus.FINISHED.asPresent()

            val updateResult =
                advisorJobRepository.update(advisorJob.id, updateStartedAt, updatedFinishedAt, updateStatus)

            updateResult shouldBe advisorJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
            advisorJobRepository.get(advisorJob.id) shouldBe advisorJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
        }

        "delete should delete the database entry" {
            val advisorJob = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

            advisorJobRepository.delete(advisorJob.id)

            advisorJobRepository.get(advisorJob.id) shouldBe null
        }
    }
}
