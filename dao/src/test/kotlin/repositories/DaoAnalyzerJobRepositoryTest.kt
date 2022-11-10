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

import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobStatus
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class DaoAnalyzerJobRepositoryTest : DatabaseTest() {
    private val analyzerJobRepository = DaoAnalyzerJobRepository()

    private lateinit var fixtures: Fixtures
    private lateinit var jobConfigurations: JobConfigurations
    private var ortRunId = -1L

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()

        fixtures = Fixtures()
        ortRunId = fixtures.ortRun.id
        jobConfigurations = fixtures.jobConfigurations
    }

    init {
        test("create should create an entry in the database") {
            val createdAnalyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            val dbEntry = analyzerJobRepository.get(createdAnalyzerJob.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe AnalyzerJob(
                id = createdAnalyzerJob.id,
                createdAt = createdAnalyzerJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = jobConfigurations.analyzer,
                status = AnalyzerJobStatus.CREATED,
                repositoryUrl = fixtures.repository.url,
                repositoryRevision = fixtures.ortRun.revision
            )
        }

        test("getForOrtRun should return the job for a run") {
            val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            analyzerJobRepository.getForOrtRun(ortRunId) shouldBe analyzerJob
        }

        test("getScheduled should return a scheduled job") {
            val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)
            val scheduledAnalyzerJob = analyzerJobRepository.update(
                id = analyzerJob.id,
                status = OptionalValue.Present(AnalyzerJobStatus.SCHEDULED)
            )

            analyzerJobRepository.getScheduled() shouldBe scheduledAnalyzerJob
        }

        test("update should update an entry in the database") {
            val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            val updateStartedAt = OptionalValue.Present(Clock.System.now())
            val updatedFinishedAt = OptionalValue.Present(Clock.System.now())
            val updateStatus = OptionalValue.Present(AnalyzerJobStatus.FINISHED)

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

        test("delete should delete the database entry") {
            val analyzerJob = analyzerJobRepository.create(ortRunId, jobConfigurations.analyzer)

            analyzerJobRepository.delete(analyzerJob.id)

            analyzerJobRepository.get(analyzerJob.id) shouldBe null
        }
    }
}
