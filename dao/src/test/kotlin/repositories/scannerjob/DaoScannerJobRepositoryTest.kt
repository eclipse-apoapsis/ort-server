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

package org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.repositories.WorkerJobRepositoryTest
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoScannerJobRepositoryTest : WorkerJobRepositoryTest<ScannerJob>() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var scannerJobRepository: DaoScannerJobRepository
    private lateinit var jobConfigurations: JobConfigurations
    private lateinit var scannerJobConfiguration: ScannerJobConfiguration

    private var ortRunId = -1L

    override fun createJob() = scannerJobRepository.create(ortRunId, scannerJobConfiguration)

    override fun getJobRepository() = scannerJobRepository

    init {
        beforeEach {
            scannerJobRepository = dbExtension.fixtures.scannerJobRepository
            jobConfigurations = dbExtension.fixtures.jobConfigurations
            scannerJobConfiguration = jobConfigurations.scanner!!

            ortRunId = dbExtension.fixtures.ortRun.id
        }

        "create should create an entry in the database" {
            val createdScannerJob = scannerJobRepository.create(ortRunId, scannerJobConfiguration)

            val dbEntry = scannerJobRepository.get(createdScannerJob.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe ScannerJob(
                id = createdScannerJob.id,
                ortRunId = ortRunId,
                createdAt = createdScannerJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = scannerJobConfiguration,
                status = JobStatus.CREATED,
            )
        }

        "getForOrtRun should return the job for a run" {
            val scannerJob = scannerJobRepository.create(ortRunId, scannerJobConfiguration)

            scannerJobRepository.getForOrtRun(ortRunId) shouldBe scannerJob
        }

        "get should return null" {
            scannerJobRepository.get(1L).shouldBeNull()
        }

        "get should return the job" {
            val scannerJob = scannerJobRepository.create(ortRunId, scannerJobConfiguration)

            scannerJobRepository.get(scannerJob.id) shouldBe scannerJob
        }

        "update should update an entry in the database" {
            val scannerJob = scannerJobRepository.create(ortRunId, scannerJobConfiguration)

            val updateStartedAt = Clock.System.now().asPresent()
            val updatedFinishedAt = Clock.System.now().asPresent()
            val updateStatus = JobStatus.FINISHED.asPresent()

            val updateResult =
                scannerJobRepository.update(scannerJob.id, updateStartedAt, updatedFinishedAt, updateStatus)

            updateResult shouldBe scannerJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
            scannerJobRepository.get(scannerJob.id) shouldBe scannerJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
        }

        "delete should delete the database entry" {
            val scannerJob = scannerJobRepository.create(ortRunId, scannerJobConfiguration)

            scannerJobRepository.delete(scannerJob.id)

            scannerJobRepository.get(scannerJob.id) shouldBe null
        }
    }
}
