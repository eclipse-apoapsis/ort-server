/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.notifier

import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerResult
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.workerContextModule
import org.eclipse.apoapsis.ortserver.workers.common.ortRunServiceModule

import org.koin.core.component.inject
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(NotifierComponent::class.java)

class NotifierComponent : EndpointComponent<NotifierRequest>(NotifierEndpoint) {
    override val endpointHandler: EndpointHandler<NotifierRequest> = { message ->
        val notifierWorker by inject<NotifierWorker>()
        val publisher by inject<MessagePublisher>()
        val notifierJobId = message.payload.notifierJobId

        val response = when (val result = notifierWorker.run(notifierJobId, message.header.traceId)) {
            is RunResult.Success -> {
                logger.info("Notifier job '$notifierJobId' succeeded.")
                Message(message.header, NotifierWorkerResult(notifierJobId))
            }

            is RunResult.Failed -> {
                logger.error("Notifier job '$notifierJobId' failed.", result.error)
                Message(message.header, NotifierWorkerError(notifierJobId))
            }

            is RunResult.Ignored -> null
        }

        if (response != null) publisher.publish(OrchestratorEndpoint, response)
    }

    override fun customModules() =
        listOf(notifierModule(), databaseModule(), ortRunServiceModule(), workerContextModule())

    private fun notifierModule() = module {
        singleOf(::NotifierRunner)
        singleOf(::NotifierWorker)
    }
}
