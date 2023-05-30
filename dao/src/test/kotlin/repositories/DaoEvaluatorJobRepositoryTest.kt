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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.util.asPresent

class DaoEvaluatorJobRepositoryTest : StringSpec({
    lateinit var evaluatorJobRepository: DaoEvaluatorJobRepository
    lateinit var fixtures: Fixtures
    lateinit var jobConfigurations: JobConfigurations
    lateinit var evaluatorJobConfiguration: EvaluatorJobConfiguration
    var ortRunId = -1L

    extension(
        DatabaseTestExtension { db ->
            evaluatorJobRepository = DaoEvaluatorJobRepository(db)
            fixtures = Fixtures(db)
            ortRunId = fixtures.ortRun.id
            jobConfigurations = fixtures.jobConfigurations
            evaluatorJobConfiguration = jobConfigurations.evaluator!!
        }
    )

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
})
