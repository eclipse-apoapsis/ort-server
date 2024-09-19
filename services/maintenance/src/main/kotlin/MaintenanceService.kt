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

import java.sql.Connection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.tables.MaintenanceJobDao
import org.eclipse.apoapsis.ortserver.dao.tables.MaintenanceJobsTable
import org.eclipse.apoapsis.ortserver.model.MaintenanceJobData
import org.eclipse.apoapsis.ortserver.model.MaintenanceJobStatus

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(MaintenanceService::class.java)

/**
 * A service to run maintenance jobs. The current implementation has the following limitations:
 *
 * - The service runs on core, on the long-term maintenance jobs should run on a separate node.
 * - It only supports jobs which run exactly once, support for running jobs multiple times or on a schedule is missing.
 * - The mechanism to prevent that a job is executed in parallel is based on a heuristic and needs to be
 *   replaced with a more sophisticated approach.
 * - No proper error handling is implemented yet. If a job fails, it is not retried and if a job throws and exception,
 *   the whole job execution stops.
 */
class MaintenanceService(private val db: Database, private val updateInterval: Duration = 5.minutes) {
    private val jobs: MutableList<MaintenanceJob> = mutableListOf()
    private val mutex = Mutex()

    /**
     * Add a [job] to the list of jobs to run. This function should be called before [run].
     */
    suspend fun addJob(job: MaintenanceJob) {
        mutex.withLock { jobs += job }
    }

    /**
     * Run the previously added jobs. This function is blocking and should be called from a coroutine.
     */
    suspend fun run() {
        logger.info("Starting maintenance service.")

        withContext(Dispatchers.IO) {
            createJobs()

            var uncompletedJobs = getUncompletedJobs()

            while (uncompletedJobs.isNotEmpty()) {
                // Check which jobs need to be started.
                val activeJobs = mutex.withLock { jobs.filterNot { it.active } }

                db.blockingQuery(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                    activeJobs.forEach { job ->
                        logger.info("Checking if maintenance job '${job.name}' needs to be started.")
                        val jobData = MaintenanceJobDao.find { MaintenanceJobsTable.name eq job.name }.firstOrNull()

                        if (jobData == null) {
                            logger.warn("Could not find job '${job.name}' in the database.")
                            return@forEach
                        }

                        if (jobData.status !in MaintenanceJobStatus.uncompletedStates) {
                            logger.info("Not starting maintenance job '${job.name}' as it is already completed.")
                            return@forEach
                        }

                        // Only start the job if it was not updated in the last five minutes. This heuristic needs
                        // to be replaced with a more reliable mechanism to find out if the job is already running.
                        if (jobData.updatedAt?.let { it > Clock.System.now() - 5.minutes } == true) {
                            logger.info("Not starting maintenance job '${job.name}' as it is already running.")
                            return@forEach
                        }

                        logger.info("Starting maintenance job '${job.name}'.")

                        val jobDataModel = jobData.mapToModel()

                        launch {
                            job.start(this@MaintenanceService, jobDataModel)
                        }
                    }
                }

                delay(updateInterval)

                uncompletedJobs = getUncompletedJobs()
            }

            logger.info("All maintenance jobs have been completed.")
        }
    }

    /**
     * Create entries in the database for all jobs that are not yet present.
     */
    private suspend fun createJobs() {
        mutex.withLock {
            db.blockingQuery(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                val existingJobs = MaintenanceJobDao.all()

                jobs.forEach { job ->
                    if (existingJobs.none { it.name == job.name }) {
                        logger.info("Creating maintenance job '${job.name}'.")

                        MaintenanceJobDao.new {
                            name = job.name
                            status = MaintenanceJobStatus.STARTED
                            startedAt = Clock.System.now()
                        }
                    }
                }
            }
        }
    }

    /**
     * Get all uncompleted jobs from the database.
     */
    private suspend fun getUncompletedJobs(): List<MaintenanceJobData> {
        return mutex.withLock {
            db.blockingQuery(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                val jobNames = jobs.map { it.name }
                MaintenanceJobDao.find {
                    MaintenanceJobsTable.name inList jobNames and
                            (MaintenanceJobsTable.status inList MaintenanceJobStatus.uncompletedStates)
                }.map { it.mapToModel() }
            }
        }
    }

    /**
     * Update the job with the given [id] with the given [data] and/or [status].
     */
    internal fun updateJob(id: Long, data: JsonObject? = null, status: MaintenanceJobStatus? = null) {
        db.blockingQuery(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
            val job = MaintenanceJobDao.findById(id)

            if (job == null) {
                logger.warn("Could not find job with ID $id.")
                return@blockingQuery
            }

            if (job.status.completed) {
                logger.warn("Job '${job.name}' with ID $id is already completed.")
                return@blockingQuery
            }

            val verb = when (status) {
                MaintenanceJobStatus.FINISHED -> "Finish"
                MaintenanceJobStatus.FAILED -> "Fail"
                else -> "Update"
            }

            logger.info("$verb job '${job.name}' with ID $id.")

            job.data = data
            job.updatedAt = Clock.System.now()

            if (status != null) {
                job.status = status
                if (status.completed) {
                    job.finishedAt = Clock.System.now()
                }
            }
        }
    }
}
