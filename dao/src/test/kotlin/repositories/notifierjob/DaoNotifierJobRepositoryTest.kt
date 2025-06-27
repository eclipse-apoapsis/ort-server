/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob

import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.repositories.WorkerJobRepositoryTest
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoNotifierJobRepositoryTest : WorkerJobRepositoryTest<NotifierJob>() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var notifierJobRepository: DaoNotifierJobRepository
    private lateinit var jobConfigurations: JobConfigurations
    private lateinit var notifierJobConfiguration: NotifierJobConfiguration

    private var ortRunId = -1L

    override fun createJob() = notifierJobRepository.create(ortRunId, notifierJobConfiguration)

    override fun getJobRepository() = notifierJobRepository

    init {
        beforeEach {
            notifierJobRepository = dbExtension.fixtures.notifierJobRepository
            jobConfigurations = dbExtension.fixtures.jobConfigurations
            notifierJobConfiguration = jobConfigurations.notifier!!

            ortRunId = dbExtension.fixtures.ortRun.id
        }

        "create should create an entry in the database" {
            val createdNotifierJob = notifierJobRepository.create(ortRunId, notifierJobConfiguration)

            val dbEntry = notifierJobRepository.get(createdNotifierJob.id)

            dbEntry.shouldNotBeNull()

            dbEntry shouldBe NotifierJob(
                id = createdNotifierJob.id,
                ortRunId = ortRunId,
                createdAt = createdNotifierJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = notifierJobConfiguration,
                status = JobStatus.CREATED,
            )
        }

        "getForOrtRun should return the job for a run" {
            val notifierJob = notifierJobRepository.create(ortRunId, notifierJobConfiguration)

            notifierJobRepository.getForOrtRun(ortRunId) shouldBe notifierJob
        }

        "get should return the job" {
            val notifierJob = notifierJobRepository.create(ortRunId, notifierJobConfiguration)

            notifierJobRepository.get(notifierJob.id) shouldBe notifierJob
        }

        "update should update an entry in the database" {
            val notifierJob = notifierJobRepository.create(ortRunId, notifierJobConfiguration)

            val updatedStartedAt = Clock.System.now().asPresent()
            val updatedFinishedAt = Clock.System.now().asPresent()
            val updatedStatus = JobStatus.FINISHED.asPresent()

            val updateResult = notifierJobRepository.update(
                notifierJob.id,
                updatedStartedAt,
                updatedFinishedAt,
                updatedStatus
            )

             updateResult shouldBe notifierJob.copy(
                startedAt = updatedStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updatedStatus.value
            )

            notifierJobRepository.get(notifierJob.id) shouldBe notifierJob.copy(
                startedAt = updatedStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updatedStatus.value
            )
        }

        "delete should delete the database entry" {
            val notifierJob = notifierJobRepository.create(ortRunId, notifierJobConfiguration)

            notifierJobRepository.delete(notifierJob.id)

            notifierJobRepository.get(notifierJob.id) shouldBe null
        }

        "deleteMailRecipients should only delete the mail recipients from the configuration" {
            val notifierJob = notifierJobRepository.create(ortRunId, notifierJobConfiguration)
            notifierJob.configuration.recipientAddresses shouldNot beEmpty()

            val updatedNotifierJob = notifierJobRepository.deleteMailRecipients(notifierJob.id)

            updatedNotifierJob.configuration.recipientAddresses shouldBe emptyList()
        }
    }
}
