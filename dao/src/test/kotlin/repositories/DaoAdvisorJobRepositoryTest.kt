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

package org.ossreviewtoolkit.server.dao.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.util.asPresent

class DaoAdvisorJobRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var advisorJobRepository: DaoAdvisorJobRepository
    lateinit var jobConfigurations: JobConfigurations
    lateinit var advisorJobConfiguration: AdvisorJobConfiguration

    var ortRunId = -1L

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

        val updateResult = advisorJobRepository.update(advisorJob.id, updateStartedAt, updatedFinishedAt, updateStatus)

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

    "complete should mark a job as completed" {
        val advisorJob = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

        val updatedFinishedAt = Clock.System.now()
        val updateStatus = JobStatus.FINISHED

        val updateResult = advisorJobRepository.complete(advisorJob.id, updatedFinishedAt, updateStatus)

        updateResult shouldBe advisorJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
        advisorJobRepository.get(advisorJob.id) shouldBe advisorJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
    }

    "tryComplete should mark a job as completed" {
        val advisorJob = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

        val updatedFinishedAt = Clock.System.now()
        val updateStatus = JobStatus.FINISHED

        val updateResult = advisorJobRepository.tryComplete(advisorJob.id, updatedFinishedAt, updateStatus)

        updateResult shouldBe advisorJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
        advisorJobRepository.get(advisorJob.id) shouldBe advisorJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
    }

    "tryComplete should not change an already completed job" {
        val advisorJob = advisorJobRepository.create(ortRunId, advisorJobConfiguration)

        val updatedFinishedAt = Clock.System.now()
        val updateStatus = JobStatus.FAILED
        advisorJobRepository.complete(advisorJob.id, updatedFinishedAt, updateStatus)

        val updateResult =
            advisorJobRepository.tryComplete(advisorJob.id, updatedFinishedAt.plus(10.seconds), JobStatus.FINISHED)

        updateResult should beNull()

        advisorJobRepository.get(advisorJob.id) shouldBe advisorJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
    }

    "tryComplete should fail for a non-existing job" {
        shouldThrow<IllegalArgumentException> {
            advisorJobRepository.tryComplete(-1, Clock.System.now(), JobStatus.FAILED)
        }
    }
})
