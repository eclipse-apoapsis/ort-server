/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.orchestrator

import com.typesafe.config.ConfigFactory

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoAdvisorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoAnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoEvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoNotifierJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoReporterJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoRepositoryRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerResult
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("org.eclipse.apoapsis.ortserver.orchestrator.EntrypointKt")

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
            is ConfigWorkerResult -> orchestrator.handleConfigWorkerResult(message.header, payload)
            is ConfigWorkerError -> orchestrator.handleConfigWorkerError(payload)
            is AnalyzerWorkerResult -> orchestrator.handleAnalyzerWorkerResult(message.header, payload)
            is AnalyzerWorkerError -> orchestrator.handleAnalyzerWorkerError(payload)
            is AdvisorWorkerResult -> orchestrator.handleAdvisorWorkerResult(message.header, payload)
            is AdvisorWorkerError -> orchestrator.handleAdvisorWorkerError(payload)
            is ScannerWorkerResult -> orchestrator.handleScannerWorkerResult(message.header, payload)
            is ScannerWorkerError -> orchestrator.handleScannerWorkerError(payload)
            is EvaluatorWorkerResult -> orchestrator.handleEvaluatorWorkerResult(message.header, payload)
            is EvaluatorWorkerError -> orchestrator.handleEvaluatorWorkerError(payload)
            is ReporterWorkerResult -> orchestrator.handleReporterWorkerResult(message.header, payload)
            is ReporterWorkerError -> orchestrator.handleReporterWorkerError(payload)
            is NotifierWorkerResult -> orchestrator.handleNotifierWorkerResult(payload)
            is NotifierWorkerError -> orchestrator.handleNotifierWorkerError(payload)
            is WorkerError -> orchestrator.handleWorkerError(message.header.ortRunId, payload)
        }
    }

    override fun customModules(): List<Module> {
        return listOf(orchestratorModule(), databaseModule())
    }

    private fun orchestratorModule(): Module = module {
        single { ConfigFactory.load() }
        single { ConfigManager.create(get()) }

        single<AdvisorJobRepository> { DaoAdvisorJobRepository(get()) }
        single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
        single<EvaluatorJobRepository> { DaoEvaluatorJobRepository(get()) }
        single<ReporterJobRepository> { DaoReporterJobRepository(get()) }
        single<NotifierJobRepository> { DaoNotifierJobRepository(get()) }
        single<RepositoryRepository> { DaoRepositoryRepository(get()) }
        single<OrtRunRepository> { DaoOrtRunRepository(get()) }
        single<ScannerJobRepository> { DaoScannerJobRepository(get()) }

        single { WorkerJobRepositories(get(), get(), get(), get(), get(), get()) }

        singleOf(::Orchestrator)
    }
}
