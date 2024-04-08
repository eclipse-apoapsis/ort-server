/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerWorker::class.java)

private val invalidStates = setOf(JobStatus.FAILED, JobStatus.FINISHED)

internal class AnalyzerWorker(
    private val db: Database,
    private val downloader: AnalyzerDownloader,
    private val runner: AnalyzerRunner,
    private val ortRunService: OrtRunService,
    private val contextFactory: WorkerContextFactory,
    private val environmentService: EnvironmentService
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidAnalyzerJob(jobId)
        val ortRun = ortRunService.getOrtRun(job.ortRunId)
            ?: throw IllegalArgumentException("The ORT run '${job.ortRunId}' does not exist.")
        val repository = ortRunService.getHierarchyForOrtRun(ortRun.id)?.repository
            ?: throw IllegalArgumentException("The repository '${ortRun.repositoryId}' does not exist.")

        job = ortRunService.startAnalyzerJob(job.id)
            ?: throw IllegalArgumentException("The analyzer job with id '$jobId' could not be started.")
        logger.debug("Analyzer job with id '{}' started at {}.", job.id, job.startedAt)

        val context = contextFactory.createContext(job.ortRunId)
        val envConfigFromJob = job.configuration.environmentConfig

        val (repositoryService, resolvedEnvConfigFromJob) = if (envConfigFromJob != null) {
            logger.info("Setting up environment from configuration provided in the Analyzer job.")
            null to environmentService.setUpEnvironment(context, envConfigFromJob)
        } else {
            environmentService.findInfrastructureServiceForRepository(context)?.also { serviceForRepo ->
                logger.info(
                    "Generating a .netrc file with credentials from infrastructure service '{}' to download the " +
                            "repository.",
                    serviceForRepo
                )

                environmentService.generateNetRcFile(context, listOf(serviceForRepo))
            } to null
        }

        val sourcesDir = downloader.downloadRepository(
            repository.url,
            ortRun.revision,
            ortRun.path.orEmpty()
        )

        val resolvedEnvConfigFromRepo = if (envConfigFromJob == null) {
            environmentService.setUpEnvironment(context, sourcesDir, repositoryService)
        } else {
            null
        }

        val resolvedEnvConfig = resolvedEnvConfigFromJob ?: resolvedEnvConfigFromRepo
        val ortResult = runner.run(context, sourcesDir, job.configuration, resolvedEnvConfig!!)

        ortRunService.storeRepositoryInformation(ortRun.id, ortResult.repository)
        ortRunService.storeResolvedPackageCurations(job.ortRunId, ortResult.resolvedConfiguration.packageCurations)

        val analyzerRun = ortResult.analyzer
            ?: throw AnalyzerException("ORT Analyzer failed to create a result.")

        logger.info(
            "Analyzer job '${job.id}' for repository '${repository.url}' with revision ${ortRun.revision} finished " +
                    "with '${analyzerRun.result.issues.values.size}' issues."
        )

        db.dbQuery {
            getValidAnalyzerJob(jobId)
            ortRunService.storeAnalyzerRun(analyzerRun.mapToModel(jobId))
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
        ortRunService.getAnalyzerJob(jobId)?.validate()
            ?: throw IllegalArgumentException("The analyzer job '$jobId' does not exist.")

    private fun AnalyzerJob.validate() = apply {
        if (status in invalidStates) {
            throw JobIgnoredException("Analyzer job '$id' status is already set to '$status'.")
        }
    }
}

private class AnalyzerException(message: String) : Exception(message)
