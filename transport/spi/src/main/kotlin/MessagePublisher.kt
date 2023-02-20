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

package org.ossreviewtoolkit.server.transport

import com.typesafe.config.Config

/**
 * A helper class for sending messages to arbitrary supported endpoints.
 *
 * This class releases clients from the burden to manage dedicated [MessageSender] instances for all the endpoints
 * they have to interact with. It offers a simple [publish] function that expects the target endpoint and the
 * message and takes care about the internal plumbing of obtaining the correct sender from the factory.
 */
class MessagePublisher(
    /**
     * The configuration of this endpoint. This determines the transport implementations to be used when sending
     * messages.
     */
    private val config: Config
) {
    /** The sender to the Orchestrator endpoint. */
    private val orchestratorSender by lazy { MessageSenderFactory.createSender(OrchestratorEndpoint, config) }

    /** The sender to the Analyzer endpoint. */
    private val analyzerSender by lazy { MessageSenderFactory.createSender(AnalyzerEndpoint, config) }

    /** The sender to the Advisor endpoint. */
    private val advisorSender by lazy { MessageSenderFactory.createSender(AdvisorEndpoint, config) }

    /** The sender to the Scanner endpoint. */
    private val scannerSender by lazy { MessageSenderFactory.createSender(ScannerEndpoint, config) }

    /** The sender to the Evaluator endpoint. */
    private val evaluatorSender by lazy { MessageSenderFactory.createSender(EvaluatorEndpoint, config) }

    /**
     * Send the given [message] to the specified [endpoint][to].
     */
    fun <T : Any> publish(to: Endpoint<T>, message: Message<T>) {
        @Suppress("UNCHECKED_CAST")
        val sender = when (to) {
            is OrchestratorEndpoint -> orchestratorSender
            is AnalyzerEndpoint -> analyzerSender
            is AdvisorEndpoint -> advisorSender
            is ScannerEndpoint -> scannerSender
            is EvaluatorEndpoint -> evaluatorSender
        } as MessageSender<T>

        sender.send(message)
    }
}
