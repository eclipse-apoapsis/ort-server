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

package org.ossreviewtoolkit.server.workers.config

import kotlinx.coroutines.runBlocking

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.server.dao.databaseModule
import org.ossreviewtoolkit.server.model.orchestrator.ConfigRequest
import org.ossreviewtoolkit.server.model.orchestrator.ConfigWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.ConfigWorkerResult
import org.ossreviewtoolkit.server.transport.ConfigEndpoint
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.workerContextModule

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ConfigComponent::class.java)

/**
 * The central entry point into the Config worker. The class sets up the dependency injection framework and processes
 * incoming messages by delegating them to a [ConfigWorker] instance.
 */
class ConfigComponent : EndpointComponent<ConfigRequest>(ConfigEndpoint) {
    override val endpointHandler: EndpointHandler<ConfigRequest> = { message ->
        val configWorker by inject<ConfigWorker>()
        val publisher by inject<MessagePublisher>()

        val runId = message.payload.ortRunId
        val responsePayload = when (val result = runBlocking { configWorker.run(runId) }) {
            is RunResult.Success -> {
                logger.info("Config worker job succeeded for run '$runId'.")
                ConfigWorkerResult(runId)
            }

            is RunResult.Failed -> {
                logger.error("Config worker job failed for run '$runId'.", result.error)
                ConfigWorkerError(runId)
            }

            else -> {
                logger.error("Unexpected result of Config worker: $result")
                ConfigWorkerError(runId)
            }
        }

        publisher.publish(OrchestratorEndpoint, Message(message.header, responsePayload))
    }

    override fun customModules(): List<Module> = listOf(configModule(), databaseModule(), workerContextModule())

    private fun configModule(): Module = module {
        singleOf(::ConfigWorker)
    }
}
