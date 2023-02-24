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

package org.ossreviewtoolkit.server.workers.evaluator

import org.koin.core.component.inject
import org.koin.core.module.Module

import org.ossreviewtoolkit.server.dao.databaseModule
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorRequest
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerResult
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.EvaluatorEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EvaluatorComponent::class.java)

class EvaluatorComponent : EndpointComponent<EvaluatorRequest>(EvaluatorEndpoint) {
    override val endpointHandler: EndpointHandler<EvaluatorRequest> = { message ->
        val publisher by inject<MessagePublisher>()
        val evaluatorJobId = message.payload.evaluatorJobId

        logger.info("Evaluator job '$evaluatorJobId' succeeded.")

        publisher.publish(OrchestratorEndpoint, Message(message.header, EvaluatorWorkerResult(evaluatorJobId)))
    }

    override fun customModules(): List<Module> = listOf(databaseModule())
}
