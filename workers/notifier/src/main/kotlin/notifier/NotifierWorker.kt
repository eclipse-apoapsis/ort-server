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

package org.eclipse.apoapsis.ortserver.workers.notifier

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.runs.notifier.NotifierRun
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService.Companion.validateForProcessing
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(NotifierWorker::class.java)

internal class NotifierWorker(
    private val db: Database,
    private val runner: NotifierRunner,
    private val ortRunService: OrtRunService,
    private val workerContextFactory: WorkerContextFactory,
    private val ortResultGenerator: NotifierOrtResultGenerator
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidNotifierJob(jobId)
        val workerContext = workerContextFactory.createContext(job.ortRunId)
        val ortRun = workerContext.ortRun

        job = ortRunService.startNotifierJob(job.id)
            ?: throw IllegalArgumentException("The notifier job '$jobId' does not exist.")
        logger.debug("Notifier job with id '{}' started at '{}'.", jobId, job.startedAt)

        val ortResult = ortResultGenerator.generateOrtResult(ortRun, job)

        val startTime = Clock.System.now()

        runner.run(ortResult, job.configuration, workerContext)

        val endTime = Clock.System.now()

        val notifierRun = NotifierRun(
            id = -1L,
            notifierJobId = job.id,
            startTime = startTime,
            endTime = endTime
        )

        db.dbQuery {
            ortRunService.storeNotifierRun(notifierRun)
        }

        RunResult.Success
    }.getOrElse {
        when (it) {
            is JobIgnoredException -> {
                logger.warn("Message with traceId '$traceId' ignored: ${it.message}")
                RunResult.Ignored
            }

            else -> {
                logger.error("Error while processing message with traceId '$traceId': ${it.message}")
                RunResult.Failed(it)
            }
        }
    }

    private fun getValidNotifierJob(jobId: Long) =
        ortRunService.getNotifierJob(jobId).validateForProcessing(jobId)
}
