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

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.model.util.asPresent

class DaoReporterJobRepositoryTest : WorkerJobRepositoryTest<ReporterJob>() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var reporterJobRepository: DaoReporterJobRepository
    private lateinit var jobConfigurations: JobConfigurations
    private lateinit var reporterJobConfiguration: ReporterJobConfiguration

    private var ortRunId = -1L

    override fun createJob() = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

    override fun getJobRepository() = reporterJobRepository

    init {
        beforeEach {
            reporterJobRepository = dbExtension.fixtures.reporterJobRepository
            jobConfigurations = dbExtension.fixtures.jobConfigurations
            reporterJobConfiguration = jobConfigurations.reporter!!

            ortRunId = dbExtension.fixtures.ortRun.id
        }

        "create should create an entry in the database" {
            val createdReporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            val dbEntry = reporterJobRepository.get(createdReporterJob.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe ReporterJob(
                id = createdReporterJob.id,
                ortRunId = ortRunId,
                createdAt = createdReporterJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = reporterJobConfiguration,
                status = JobStatus.CREATED,
            )
        }

        "getForOrtRun should return the job for a run" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            reporterJobRepository.getForOrtRun(ortRunId) shouldBe reporterJob
        }

        "get should return null" {
            reporterJobRepository.get(1L).shouldBeNull()
        }

        "get should return the job" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            reporterJobRepository.get(reporterJob.id) shouldBe reporterJob
        }

        "update should update an entry in the database" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            val updateStartedAt = Clock.System.now().asPresent()
            val updatedFinishedAt = Clock.System.now().asPresent()
            val updateStatus = JobStatus.FINISHED.asPresent()

            val updateResult =
                reporterJobRepository.update(reporterJob.id, updateStartedAt, updatedFinishedAt, updateStatus)

            updateResult shouldBe reporterJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
            reporterJobRepository.get(reporterJob.id) shouldBe reporterJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
        }

        "delete should delete the database entry" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            reporterJobRepository.delete(reporterJob.id)

            reporterJobRepository.get(reporterJob.id) shouldBe null
        }
    }
}
