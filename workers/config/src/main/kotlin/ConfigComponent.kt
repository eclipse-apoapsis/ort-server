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

package org.eclipse.apoapsis.ortserver.workers.config

import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.workerContextModule

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The central entry point into the Config worker. The class sets up the dependency injection framework and processes
 * incoming messages by delegating them to a [ConfigWorker] instance.
 */
class ConfigComponent : EndpointComponent<ConfigRequest>(ConfigEndpoint) {
    override val endpointHandler: EndpointHandler<ConfigRequest> = { message ->
        val configWorker by inject<ConfigWorker>()
        val publisher by inject<MessagePublisher>()

        val runId = message.payload.ortRunId
        val responsePayload = when (val result = configWorker.run(runId)) {
            is RunResult.Success -> {
                logger.info("Config worker job succeeded for run '$runId'.")
                ConfigWorkerResult(runId)
            }

            is RunResult.Failed -> {
                logger.error("Config worker job failed for run '$runId'.", result.error)
                ConfigWorkerError(runId, result.error.message)
            }

            else -> {
                logger.error("Unexpected result of Config worker for run '$runId': $result")
                ConfigWorkerError(runId)
            }
        }

        publisher.publish(OrchestratorEndpoint, Message(message.header, responsePayload))

        EndpointHandlerResult.CONTINUE
    }

    override fun customModules(): List<Module> = listOf(configModule(), databaseModule(), workerContextModule())

    private fun configModule(): Module = module {
        singleOf(::ConfigWorker)
    }
}
