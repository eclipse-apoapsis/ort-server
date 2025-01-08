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

package org.eclipse.apoapsis.ortserver.workers.advisor

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService.Companion.validateForProcessing
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.Severity

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AdvisorWorker::class.java)

internal class AdvisorWorker(
    private val db: Database,
    private val runner: AdvisorRunner,
    private val ortRunService: OrtRunService,
    private val contextFactory: WorkerContextFactory
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidAdvisorJob(jobId)
        val workerContext = contextFactory.createContext(job.ortRunId)
        val ortRun = workerContext.ortRun

        val repository = ortRunService.getOrtRepositoryInformation(ortRun)
        val resolvedConfiguration = ortRunService.getResolvedConfiguration(ortRun)
        val analyzerRun = ortRunService.getAnalyzerRunForOrtRun(job.ortRunId)

        val ortResult = ortRun.mapToOrt(
            repository = repository,
            analyzerRun = analyzerRun?.mapToOrt(),
            resolvedConfiguration = resolvedConfiguration.mapToOrt()
        )

        job = ortRunService.startAdvisorJob(job.id)
            ?: throw IllegalArgumentException("The advisor job with id '$jobId' could not be started.")
        logger.debug("Advisor job with id '{}' started at {}.", job.id, job.startedAt)

        val advisorRun = checkNotNull(
            runner.run(
                contextFactory.createContext(job.ortRunId),
                ortResult = ortResult,
                config = job.configuration
            ).advisor
        ) { "ORT Adviser failed to create a result." }

        db.dbQuery {
            getValidAdvisorJob(jobId)
            ortRunService.storeAdvisorRun(advisorRun.mapToModel(jobId))
        }

        if (advisorRun.results.values.flatten().flatMap { it.summary.issues }.any { it.severity >= Severity.WARNING }) {
            RunResult.FinishedWithIssues
        } else {
            RunResult.Success
        }
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

    private fun getValidAdvisorJob(jobId: Long) =
        ortRunService.getAdvisorJob(jobId).validateForProcessing(jobId)
}
