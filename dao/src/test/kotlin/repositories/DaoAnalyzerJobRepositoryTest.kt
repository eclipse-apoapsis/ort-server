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
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.util.asPresent

class DaoAnalyzerJobRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var analyzerJobRepository: DaoAnalyzerJobRepository
    lateinit var jobConfigurations: JobConfigurations

    var ortRunId = -1L

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

    "complete should mark a job as completed" {
        val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

        val updatedFinishedAt = Clock.System.now()
        val updateStatus = JobStatus.FINISHED

        val updateResult = analyzerJobRepository.complete(analyzerJob.id, updatedFinishedAt, updateStatus)

        updateResult shouldBe analyzerJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
        analyzerJobRepository.get(analyzerJob.id) shouldBe analyzerJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
    }

    "complete should only accept states that indicate a completed job" {
        val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

        listOf(JobStatus.CREATED, JobStatus.SCHEDULED, JobStatus.RUNNING).forAll { status ->
            shouldThrow<IllegalArgumentException> {
                analyzerJobRepository.complete(analyzerJob.id, Clock.System.now(), status)
            }
        }
    }

    "tryComplete should mark a job as completed" {
        val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

        val updatedFinishedAt = Clock.System.now()
        val updateStatus = JobStatus.FINISHED

        val updateResult = analyzerJobRepository.tryComplete(analyzerJob.id, updatedFinishedAt, updateStatus)

        updateResult shouldBe analyzerJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
        analyzerJobRepository.get(analyzerJob.id) shouldBe analyzerJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
    }

    "tryComplete should not change an already completed job" {
        val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

        val updatedFinishedAt = Clock.System.now()
        val updateStatus = JobStatus.FAILED
        analyzerJobRepository.complete(analyzerJob.id, updatedFinishedAt, updateStatus)

        val updateResult =
            analyzerJobRepository.tryComplete(analyzerJob.id, updatedFinishedAt.plus(10.seconds), JobStatus.FINISHED)

        updateResult should beNull()

        analyzerJobRepository.get(analyzerJob.id) shouldBe analyzerJob.copy(
            finishedAt = updatedFinishedAt.toDatabasePrecision(),
            status = updateStatus
        )
    }

    "tryComplete should fail for a non-existing job" {
        shouldThrow<IllegalArgumentException> {
            analyzerJobRepository.tryComplete(-1, Clock.System.now(), JobStatus.FAILED)
        }
    }
})
