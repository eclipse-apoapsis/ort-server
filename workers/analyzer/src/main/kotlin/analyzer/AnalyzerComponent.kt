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

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.server.dao.databaseModule
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerRunRepository
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.workerContextModule
import org.ossreviewtoolkit.server.workers.common.env.buildEnvironmentModule

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerComponent::class.java)

/**
 * The central entry point into the Analyzer service. The class processes messages to analyze repositories by
 * delegating to helper objects.
 */
class AnalyzerComponent : EndpointComponent<AnalyzerRequest>(AnalyzerEndpoint) {
    override val endpointHandler: EndpointHandler<AnalyzerRequest> = { message ->
        val analyzerWorker by inject<AnalyzerWorker>()
        val publisher by inject<MessagePublisher>()

        val jobId = message.payload.analyzerJobId
        val response = when (val result = analyzerWorker.run(jobId, message.header.traceId)) {
            is RunResult.Success -> {
                logger.info("Analyzer job '$jobId' succeeded.")
                Message(message.header, AnalyzerWorkerResult(jobId))
            }

            is RunResult.Failed -> {
                logger.error("Analyzer job '$jobId' failed.", result.error)
                Message(message.header, AnalyzerWorkerError(jobId))
            }

            is RunResult.Ignored -> null
        }

        if (response != null) publisher.publish(OrchestratorEndpoint, response)
    }

    override fun customModules(): List<Module> =
        listOf(analyzerModule(), databaseModule(), workerContextModule(), buildEnvironmentModule())

    private fun analyzerModule(): Module = module {
        single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
        single<AnalyzerRunRepository> { DaoAnalyzerRunRepository(get()) }

        singleOf(::AnalyzerWorkerDao)
        singleOf(::AnalyzerDownloader)
        singleOf(::AnalyzerRunner)
        singleOf(::AnalyzerWorker)
    }
}
