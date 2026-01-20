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

package org.eclipse.apoapsis.ortserver.workers.evaluator

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.loadGlobalResolutions
import org.eclipse.apoapsis.ortserver.workers.common.validateForProcessing

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.utils.ort.ORT_VERSION

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EvaluatorWorker::class.java)

internal class EvaluatorWorker(
    private val db: Database,
    private val runner: EvaluatorRunner,
    private val ortRunService: OrtRunService,
    private val workerContextFactory: WorkerContextFactory,
    private val adminConfigService: AdminConfigService
) {
    suspend fun run(jobId: Long, traceId: String): RunResult = runCatching {
        var job = getValidEvaluatorJob(jobId)
        workerContextFactory.withContext(job.ortRunId) { workerContext ->
            val ortRun = workerContext.ortRun

            job = ortRunService.startEvaluatorJob(job.id)
                ?: throw IllegalArgumentException("The evaluator job with id '$jobId' could not be started.")
            logger.debug("Evaluator job with id '{}' started at {}.", job.id, job.startedAt)
            logger.info("Using ORT version {}.", ORT_VERSION)

            if (job.configuration.keepAliveWorker) {
                EndpointComponent.generateKeepAliveFile()
            }

            val ortResult = ortRunService.generateOrtResult(ortRun)

            val evaluatorRunnerResult = runner.run(ortResult, job.configuration, workerContext)

            db.dbQuery {
                getValidEvaluatorJob(job.id)
                ortRunService.storeEvaluatorRun(evaluatorRunnerResult.evaluatorRun.mapToModel(job.id))
                ortRunService.storeResolvedPackageConfigurations(ortRun.id, evaluatorRunnerResult.packageConfigurations)
                ortRunService.storeResolvedItems(ortRun.id, evaluatorRunnerResult.resolvedItems)
            }

            val allRuleViolations = evaluatorRunnerResult.evaluatorRun.violations

            val repositoryConfigRuleViolationResolutions = ortResult.repository.config.resolutions.ruleViolations
            val globalRuleViolationResolutions = workerContext.loadGlobalResolutions(adminConfigService).ruleViolations

            val unresolvedRuleViolations = allRuleViolations.filter { ruleViolation ->
                repositoryConfigRuleViolationResolutions.none { it.matches(ruleViolation) } &&
                        globalRuleViolationResolutions.none { it.matches(ruleViolation) }
            }

            logger.info(
                "Evaluator job ${job.id} finished with ${allRuleViolations.size} total violations" +
                        " and ${unresolvedRuleViolations.size} unresolved violations."
            )

            if (unresolvedRuleViolations.any { it.severity >= Severity.WARNING }) {
                RunResult.FinishedWithIssues
            } else {
                RunResult.Success
            }
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

    private fun getValidEvaluatorJob(jobId: Long) =
        ortRunService.getEvaluatorJob(jobId).validateForProcessing(jobId)
}
