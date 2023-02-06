/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.advisor

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.server.dao.databaseModule
import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerRunRepository
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorRequest
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerResult
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.transport.AdvisorEndpoint
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.workers.common.RunResult

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AdvisorComponent::class.java)

class AdvisorComponent : EndpointComponent<AdvisorRequest>(AdvisorEndpoint) {
    override val endpointHandler: EndpointHandler<AdvisorRequest> = { message ->
        val advisorWorker by inject<AdvisorWorker>()
        val publisher by inject<MessagePublisher>()

        val advisorJobId = message.payload.advisorJobId
        val analyzerJobId = message.payload.analyzerJobId

        val response = when (val result = advisorWorker.run(advisorJobId, analyzerJobId, message.header.traceId)) {
            is RunResult.Success -> {
                logger.info("Advisor job '$advisorJobId' succeeded.")
                Message(message.header, AdvisorWorkerResult(advisorJobId))
            }

            is RunResult.Failed -> {
                logger.error("Advisor job '$advisorJobId' failed.", result.error)
                Message(message.header, AdvisorWorkerError(advisorJobId))
            }

            is RunResult.Ignored -> null
        }

        if (response != null) publisher.publish(OrchestratorEndpoint, response)
    }

    override fun customModules(): List<Module> = listOf(advisorModule(), databaseModule())

    private fun advisorModule(): Module = module {
        singleOf<AdvisorJobRepository>(::DaoAdvisorJobRepository)
        singleOf<AdvisorRunRepository>(::DaoAdvisorRunRepository)
        singleOf<AnalyzerRunRepository>(::DaoAnalyzerRunRepository)

        singleOf(::AdvisorWorkerDao)
        singleOf(::AdvisorRunner)
        singleOf(::AdvisorWorker)
    }
}
