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
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.server.dao.databaseModule
import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoEvaluatorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoEvaluatorRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoScannerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoScannerRunRepository
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorRequest
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerResult
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorRunRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerRunRepository
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.EvaluatorEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.workers.common.OrtRunService
import org.ossreviewtoolkit.server.workers.common.RunResult

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EvaluatorComponent::class.java)

class EvaluatorComponent : EndpointComponent<EvaluatorRequest>(EvaluatorEndpoint) {
    override val endpointHandler: EndpointHandler<EvaluatorRequest> = { message ->
        val evaluatorWorker by inject<EvaluatorWorker>()
        val publisher by inject<MessagePublisher>()
        val evaluatorJobId = message.payload.evaluatorJobId

        val response = when (val result = evaluatorWorker.run(evaluatorJobId, message.header.traceId)) {
            is RunResult.Success -> {
                logger.info("Evaluator job '$evaluatorJobId' succeeded.")
                Message(message.header, EvaluatorWorkerResult(evaluatorJobId))
            }

            is RunResult.Failed -> {
                logger.error("Evaluator job '$evaluatorJobId' failed.", result.error)
                Message(message.header, EvaluatorWorkerError(evaluatorJobId))
            }

            is RunResult.Ignored -> null
        }

        if (response != null) publisher.publish(OrchestratorEndpoint, response)
    }

    override fun customModules(): List<Module> = listOf(evaluatorModule(), databaseModule())

    private fun evaluatorModule(): Module = module {
        single<AdvisorJobRepository> { DaoAdvisorJobRepository(get()) }
        single<AdvisorRunRepository> { DaoAdvisorRunRepository(get()) }
        single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
        single<AnalyzerRunRepository> { DaoAnalyzerRunRepository(get()) }
        single<EvaluatorJobRepository> { DaoEvaluatorJobRepository(get()) }
        single<EvaluatorRunRepository> { DaoEvaluatorRunRepository(get()) }
        single<OrtRunRepository> { DaoOrtRunRepository(get()) }
        single<RepositoryRepository> { DaoRepositoryRepository(get()) }
        single<ScannerJobRepository> { DaoScannerJobRepository(get()) }
        single<ScannerRunRepository> { DaoScannerRunRepository(get()) }

        single { OrtRunService(get()) }

        singleOf(::EvaluatorWorkerDao)
        singleOf(::EvaluatorRunner)
        singleOf(::EvaluatorWorker)
    }
}
