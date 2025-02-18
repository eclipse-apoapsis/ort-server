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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.DaoAdvisorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.DaoAdvisorRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.DaoAnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.DaoAnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.workerContextModule
import org.eclipse.apoapsis.ortserver.workers.common.ortRunServiceModule

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

class AdvisorComponent : EndpointComponent<AdvisorRequest>(AdvisorEndpoint) {
    override val endpointHandler: EndpointHandler<AdvisorRequest> = { message ->
        val advisorWorker by inject<AdvisorWorker>()
        val publisher by inject<MessagePublisher>()

        val advisorJobId = message.payload.advisorJobId

        withMdcContext("advisorJobId" to advisorJobId.toString()) {
            val response = when (val result = advisorWorker.run(advisorJobId, message.header.traceId)) {
                is RunResult.Success -> {
                    logger.info("Advisor job '$advisorJobId' succeeded.")
                    Message(message.header, AdvisorWorkerResult(advisorJobId))
                }

                is RunResult.FinishedWithIssues -> {
                    logger.error("Advisor job '$advisorJobId' finished with issues.")
                    Message(message.header, AdvisorWorkerResult(advisorJobId, true))
                }

                is RunResult.Failed -> {
                    logger.error("Advisor job '$advisorJobId' failed.", result.error)
                    Message(message.header, AdvisorWorkerError(advisorJobId, result.error.message))
                }

                is RunResult.Ignored -> null
            }

            if (response != null) publisher.publish(OrchestratorEndpoint, response)
        }
    }

    override fun customModules(): List<Module> =
        listOf(advisorModule(), databaseModule(), ortRunServiceModule(), workerContextModule())

    private fun advisorModule(): Module = module {
        single<AdvisorJobRepository> { DaoAdvisorJobRepository(get()) }
        single<AdvisorRunRepository> { DaoAdvisorRunRepository(get()) }
        single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
        single<AnalyzerRunRepository> { DaoAnalyzerRunRepository(get()) }
        single<OrtRunRepository> { DaoOrtRunRepository(get()) }

        single { ConfigManager.create(get()) }

        singleOf(::AdvisorRunner)
        singleOf(::AdvisorWorker)
    }
}
