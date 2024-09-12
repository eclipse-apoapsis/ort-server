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

package org.eclipse.apoapsis.ortserver.services.maintenance

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.milliseconds

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

import org.eclipse.apoapsis.ortserver.dao.findSingle
import org.eclipse.apoapsis.ortserver.dao.tables.MaintenanceJobDao
import org.eclipse.apoapsis.ortserver.dao.tables.MaintenanceJobsTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.MaintenanceJobData
import org.eclipse.apoapsis.ortserver.model.MaintenanceJobStatus

import org.jetbrains.exposed.sql.transactions.transaction

class MaintenanceServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    "run" should {
        "run the supplied jobs" {
            val service = MaintenanceService(dbExtension.db, 100.milliseconds)

            (1..5).forEach { index ->
                service.addJob(object : MaintenanceJob() {
                    override val name = "TestJob$index"
                    override suspend fun execute(service: MaintenanceService, jobData: MaintenanceJobData) {
                        service.updateJob(jobData.id, status = MaintenanceJobStatus.FINISHED)
                    }
                })
            }

            service.run()

            transaction {
                val jobData = MaintenanceJobDao.all().map { it.mapToModel() }
                jobData.forEach { it.status shouldBe MaintenanceJobStatus.FINISHED }
                jobData.map { it.name } should
                        containExactlyInAnyOrder("TestJob1", "TestJob2", "TestJob3", "TestJob4", "TestJob5")
            }
        }
    }

    "updateJob" should {
        "update the job data" {
            val service = MaintenanceService(dbExtension.db, 100.milliseconds)
            val jobId = createJob()
            val jobData = JobData(42)

            service.updateJob(jobId, data = Json.Default.encodeToJsonElement(jobData).jsonObject)

            transaction {
                val jobDao = MaintenanceJobDao.findSingle { MaintenanceJobsTable.id eq jobId }
                jobDao.data?.let { Json.Default.decodeFromJsonElement<JobData>(it) } shouldBe jobData
            }
        }

        "update the status" {
            val service = MaintenanceService(dbExtension.db, 100.milliseconds)
            val jobId = createJob()

            service.updateJob(jobId, status = MaintenanceJobStatus.FAILED)

            transaction {
                val jobDao = MaintenanceJobDao.findSingle { MaintenanceJobsTable.id eq jobId }
                jobDao.status shouldBe MaintenanceJobStatus.FAILED
            }
        }
    }
})

@Serializable
private data class JobData(val progress: Int)

private fun createJob() = transaction {
    MaintenanceJobDao.new {
        name = "TestJob"
        status = MaintenanceJobStatus.STARTED
    }.id.value
}
