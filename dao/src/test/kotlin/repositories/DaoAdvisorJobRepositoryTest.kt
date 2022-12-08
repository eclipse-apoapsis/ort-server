/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.test.repositories

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorJobRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.util.OptionalValue

class DaoAdvisorJobRepositoryTest : StringSpec() {
    private val advisorJobRepository = DaoAdvisorJobRepository()

    private lateinit var fixtures: Fixtures
    private lateinit var jobConfigurations: JobConfigurations
    private var ortRunId = -1L

    init {
        extension(
            DatabaseTestExtension {
                fixtures = Fixtures()
                ortRunId = fixtures.ortRun.id
                jobConfigurations = fixtures.jobConfigurations
            }
        )

        "create should create an entry in the database" {
            val createdAdvisorJob = advisorJobRepository.create(ortRunId, jobConfigurations.advisor)

            val dbEntry = advisorJobRepository.get(createdAdvisorJob.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe AdvisorJob(
                id = createdAdvisorJob.id,
                ortRunId = ortRunId,
                createdAt = createdAdvisorJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = jobConfigurations.advisor,
                status = JobStatus.CREATED,
            )
        }

        "getForOrtRun should return the job for a run" {
            val advisorJob = advisorJobRepository.create(ortRunId, jobConfigurations.advisor)

            advisorJobRepository.getForOrtRun(ortRunId) shouldBe advisorJob
        }

        "update should update an entry in the database" {
            val advisorJob = advisorJobRepository.create(ortRunId, jobConfigurations.advisor)

            val updateStartedAt = OptionalValue.Present(Clock.System.now())
            val updatedFinishedAt = OptionalValue.Present(Clock.System.now())
            val updateStatus = OptionalValue.Present(JobStatus.FINISHED)

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
            val advisorJob = advisorJobRepository.create(ortRunId, jobConfigurations.advisor)

            advisorJobRepository.delete(advisorJob.id)

            advisorJobRepository.get(advisorJob.id) shouldBe null
        }
    }
}
