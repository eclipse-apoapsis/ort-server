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

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.server.dao.databaseModule
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("org.ossreviewtoolkit.server.orchestrator.EntrypointKt")

fun main() {
    log.info("Starting ORT-Server Orchestrator.")

    OrchestratorComponent().start()
}

class OrchestratorComponent : EndpointComponent<OrchestratorMessage>(OrchestratorEndpoint) {
    override val endpointHandler: EndpointHandler<OrchestratorMessage> = { message ->
        log.info("Received '${message.payload::class.simpleName}' message. TraceID: '${message.header.traceId}'.")

        val orchestrator by inject<Orchestrator>()

        when (val payload = message.payload) {
            is CreateOrtRun -> orchestrator.handleCreateOrtRun(message.header, payload)

            is AnalyzerWorkerResult -> orchestrator.handleAnalyzerWorkerResult(message.header, payload)

            is AnalyzerWorkerError -> orchestrator.handleAnalyzerWorkerError(payload)

            is AdvisorWorkerResult -> orchestrator.handleAdvisorWorkerResult(payload)

            is AdvisorWorkerError -> orchestrator.handleAdvisorWorkerError(payload)
        }
    }

    override fun customModules(): List<Module> {
        return listOf(orchestratorModule(), databaseModule())
    }

    private fun orchestratorModule(): Module = module {
        singleOf<AnalyzerJobRepository>(::DaoAnalyzerJobRepository)
        singleOf<RepositoryRepository>(::DaoRepositoryRepository)
        singleOf<OrtRunRepository>(::DaoOrtRunRepository)

        singleOf(::Orchestrator)
    }
}
