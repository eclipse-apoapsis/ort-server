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

package org.ossreviewtoolkit.server.workers.advisor

import com.typesafe.config.Config

import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerResult
import org.ossreviewtoolkit.server.transport.AdvisorEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.workers.common.RunResult

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AdvisorReceiver::class.java)

class AdvisorReceiver(private val config: Config) {
    fun receive(handler: (advisorJobId: Long, analyzerJobId: Long, traceId: String) -> RunResult) {
        val sender = MessageSenderFactory.createSender(OrchestratorEndpoint, config)

        MessageReceiverFactory.createReceiver(AdvisorEndpoint, config) { message ->
            val token = message.header.token
            val traceId = message.header.traceId
            val advisorJobId = message.payload.advisorJobId
            val analyzerJobId = message.payload.analyzerJobId

            val response = when (val result = handler(advisorJobId, analyzerJobId, traceId)) {
                is RunResult.Success -> {
                    logger.info("Advisor job '$advisorJobId' succeeded.")
                    Message(MessageHeader(token, traceId), AdvisorWorkerResult(advisorJobId))
                }

                is RunResult.Failed -> {
                    logger.error("Advisor job '$advisorJobId' failed.", result.error)
                    Message(MessageHeader(token, traceId), AdvisorWorkerError(advisorJobId))
                }

                is RunResult.Ignored -> null
            }

            if (response != null) sender.send(response)
        }
    }
}
