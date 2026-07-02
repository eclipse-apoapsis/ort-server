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

import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.authorization.service.DbAuthorizationService
import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionEventStore
import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.workerContextModule
import org.eclipse.apoapsis.ortserver.workers.common.env.buildEnvironmentModule
import org.eclipse.apoapsis.ortserver.workers.common.jobMdcKey
import org.eclipse.apoapsis.ortserver.workers.common.ortRunServiceModule

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** The name of the preparation phase. */
private const val PREPARATION_PHASE = "preparation"

/** The name of the analysis phase. */
private const val ANALYSIS_PHASE = "analysis"

/** The name of the result phase. */
private const val RESULT_PHASE = "result"

/** The name of the full phase which includes all other phases. */
private const val FULL_PHASE = "full"

/** A set with the names of all supported phases. */
private val VALID_PHASES = setOf(PREPARATION_PHASE, ANALYSIS_PHASE, RESULT_PHASE, FULL_PHASE)

/**
 * The central entry point into the Analyzer service. The class processes messages to analyze repositories by
 * delegating to helper objects.
 */
class AnalyzerComponent(
    /** The command line arguments passed to the Analyzer endpoint. */
    private val args: Array<String>
) : EndpointComponent<AnalyzerRequest>(AnalyzerEndpoint) {
    override val endpointHandler: EndpointHandler<AnalyzerRequest> = { message ->
        val analyzerWorker by inject<AnalyzerWorker>()
        val publisher by inject<MessagePublisher>()
        val jobId = message.payload.analyzerJobId

        withMdcContext(AnalyzerEndpoint.jobMdcKey(jobId)) {
            val result = analyzerWorker.run(
                jobId,
                message.header.traceId,
                getPhase(),
                args.drop(1).toTypedArray()
            )

            val response = when (result) {
                is RunResult.Success -> {
                    logger.info("Analyzer job '$jobId' succeeded.")
                    Message(message.header, AnalyzerWorkerResult(jobId))
                }

                is RunResult.FinishedWithIssues -> {
                    logger.warn("Analyzer job '$jobId' finished with issues.")
                    Message(message.header, AnalyzerWorkerResult(jobId, true))
                }

                is RunResult.Failed -> {
                    logger.error("Analyzer job '$jobId' failed.", result.error)
                    Message(message.header, AnalyzerWorkerError(jobId, result.error.message))
                }

                is RunResult.Ignored -> null
            }

            // Check if there is a demand to keep the pod alive for manual problem analysis.
            sleepWhileKeepAliveFileExists()

            if (response != null) publisher.publish(OrchestratorEndpoint, response)
        }

        val handleSingleJobOnly = configManager.config.getBoolean("analyzer.handleSingleJobOnly")
        if (handleSingleJobOnly) EndpointHandlerResult.STOP else EndpointHandlerResult.CONTINUE
    }

    override fun customModules(): List<Module> = listOf(
        analyzerModule(),
        databaseModule(),
        ortRunServiceModule(),
        workerContextModule(),
        buildEnvironmentModule(includePackageManagerGenerators = true)
    )

    private fun analyzerModule(): Module = module {
        singleOf(::AnalyzerDownloader)
        singleOf(::AnalyzerRunner)
        singleOf(::AnalyzerWorker)
        singleOf(::FullPhase) { qualifier = named("full") }.bind(AnalyzerPhase::class)
        singleOf(::PreparationPhase) { qualifier = named(PREPARATION_PHASE) }.bind(AnalyzerPhase::class)
        singleOf(::AnalysisPhase) { qualifier = named(ANALYSIS_PHASE) }.bind(AnalyzerPhase::class)
        singleOf(::ResultPhase) { qualifier = named(RESULT_PHASE) }.bind(AnalyzerPhase::class)
        single<AuthorizationService> { DbAuthorizationService(get()) }
        singleOf(::RepositoryService)
        singleOf(::IssueResolutionEventStore)
        singleOf(::IssueResolutionService)
    }

    /**
     * Obtain the current phase for the Analyzer run from the dependency definitions based on the command line
     * arguments. If no argument is specified, use the `FULL` phase.
     */
    private fun getPhase(): AnalyzerPhase {
        val phase = args.firstOrNull()?.lowercase() ?: "full"
        require(phase in VALID_PHASES) {
            "Invalid phase '$phase'. Valid phases are: $VALID_PHASES (ignoring case)."
        }

        return getKoin().get(named(phase))
    }
}
