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

import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
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
import org.eclipse.apoapsis.ortserver.workers.common.ortRunServiceModule

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The central entry point into the Analyzer service. The class processes messages to analyze repositories by
 * delegating to helper objects.
 */
class AnalyzerComponent : EndpointComponent<AnalyzerRequest>(AnalyzerEndpoint) {
    override val endpointHandler: EndpointHandler<AnalyzerRequest> = { message ->
        val analyzerWorker by inject<AnalyzerWorker>()
        val publisher by inject<MessagePublisher>()
        val jobId = message.payload.analyzerJobId

        withMdcContext("analyzerJobId" to jobId.toString()) {
            val response = when (val result = analyzerWorker.run(jobId, message.header.traceId)) {
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

        EndpointHandlerResult.CONTINUE
    }

    override fun customModules(): List<Module> = listOf(
        analyzerModule(),
        databaseModule(),
        ortRunServiceModule(),
        workerContextModule(),
        buildEnvironmentModule()
    )

    private fun analyzerModule(): Module = module {
        singleOf(::AnalyzerDownloader)
        singleOf(::AnalyzerRunner)
        singleOf(::AnalyzerWorker)
    }
}
