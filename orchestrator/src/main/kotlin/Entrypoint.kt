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

package org.ossreviewtoolkit.server.orchestrator

import com.typesafe.config.ConfigFactory

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.createDataSource
import org.ossreviewtoolkit.server.dao.createDatabaseConfig
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("org.ossreviewtoolkit.server.orchestrator.EntrypointKt")

fun main() {
    log.info("Starting ORT-Server Orchestrator.")

    val config = ConfigFactory.load()

    // TODO: The `connect()` method also runs the migration, this might not be desired.
    createDataSource(createDatabaseConfig(config)).connect()

    val orchestrator = Orchestrator(
        DaoAnalyzerJobRepository(),
        DaoRepositoryRepository(),
        DaoOrtRunRepository(),
        MessageSenderFactory.createSender(AnalyzerEndpoint, config)
    )

    // Register the message receiver and handle the messages.
    MessageReceiverFactory.createReceiver(OrchestratorEndpoint, config) { message ->
        log.info("Received '${message.payload::class.simpleName}' message. TraceID: '${message.header.traceId}'.")

        when (message.payload) {
            is CreateOrtRun -> orchestrator.handleCreateOrtRun(message.header, message.payload as CreateOrtRun)

            is AnalyzerWorkerResult -> orchestrator.handleAnalyzerWorkerResult(
                message.payload as AnalyzerWorkerResult
            )

            is AnalyzerWorkerError -> orchestrator.handleAnalyzerWorkerError(
                message.payload as AnalyzerWorkerError
            )

            else -> TODO("Support for message type '${message.payload::class.simpleName}' not yet implemented.")
        }
    }
}
