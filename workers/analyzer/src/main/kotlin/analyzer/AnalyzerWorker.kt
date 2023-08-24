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

package org.ossreviewtoolkit.server.workers.analyzer

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.workers.common.JobIgnoredException
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory
import org.ossreviewtoolkit.server.workers.common.env.EnvironmentService
import org.ossreviewtoolkit.server.workers.common.mapToModel

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class AnalyzerWorker(
    private val db: Database,
    private val downloader: AnalyzerDownloader,
    private val runner: AnalyzerRunner,
    private val dao: AnalyzerWorkerDao,
    private val contextFactory: WorkerContextFactory,
    private val environmentService: EnvironmentService
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        val job = db.dbQuery { getValidAnalyzerJob(jobId) }
        val ortRun = db.dbQuery { dao.getOrtRun(job.ortRunId) }
            ?: throw IllegalArgumentException("The ORT run '${job.ortRunId}' does not exist.")
        val repository = db.dbQuery { dao.getRepository(ortRun.repositoryId) }
            ?: throw IllegalArgumentException("The repository '${ortRun.repositoryId}' does not exist.")

        logger.debug("Analyzer job with id '{}' started at {}.", job.id, job.startedAt)

        val context = contextFactory.createContext(job.ortRunId)
        val repositoryService = environmentService.findInfrastructureServiceForRepository(context)

        repositoryService?.let { infrastructureService ->
            logger.info(
                "Generating a .netrc file with credentials from infrastructure service '{}' to download the " +
                        "repository.",
                infrastructureService
            )

            environmentService.generateNetRcFile(context, listOf(infrastructureService))
        }

        val sourcesDir = downloader.downloadRepository(repository.url, ortRun.revision)

        val envConfigFromJob = job.configuration.environmentConfig
        if (envConfigFromJob != null) {
            logger.info("Setting up environment from configuration provided in the Analyzer job.")
            environmentService.setUpEnvironment(context, envConfigFromJob, repositoryService)
        } else {
            environmentService.setUpEnvironment(context, sourcesDir, repositoryService)
        }

        val ortResult = runner.run(sourcesDir, job.configuration)

        dao.storeRepositoryInformation(ortResult, job)
        dao.storeResolvedPackageCurations(job.ortRunId, ortResult.resolvedConfiguration.packageCurations)

        val analyzerRun = ortResult.analyzer
            ?: throw AnalyzerException("ORT Analyzer failed to create a result.")

        logger.info(
            "Analyzer job '${job.id}' for repository '${repository.url}' with revision ${ortRun.revision} finished " +
                    "with '${analyzerRun.result.issues.values.size}' issues."
        )

        db.dbQuery {
            getValidAnalyzerJob(jobId)
            dao.storeAnalyzerRun(analyzerRun.mapToModel(jobId))
        }

        RunResult.Success
    }.getOrElse {
        when (it) {
            is JobIgnoredException -> {
                logger.warn("Message with traceId '$traceId' ignored: ${it.message}")
                RunResult.Ignored
            }

            else -> {
                logger.error("Error while processing message traceId '$traceId': ${it.message}")
                RunResult.Failed(it)
            }
        }
    }

    private fun getValidAnalyzerJob(jobId: Long) =
        dao.getAnalyzerJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The analyzer job '$jobId' does not exist.")

    private fun AnalyzerJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Analyzer job '$id' status is already set to '$status'.")
        }
    }
}

private class AnalyzerException(message: String) : Exception(message)
