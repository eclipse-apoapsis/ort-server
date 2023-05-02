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

package org.ossreviewtoolkit.server.workers.reporter

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
import org.ossreviewtoolkit.server.dao.repositories.DaoReporterJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.model.orchestrator.ReporterRequest
import org.ossreviewtoolkit.server.model.orchestrator.ReporterWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.ReporterWorkerResult
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorRunRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterJobRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.ReporterEndpoint
import org.ossreviewtoolkit.server.workers.common.RunResult

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReporterComponent::class.java)

class ReporterComponent : EndpointComponent<ReporterRequest>(ReporterEndpoint) {
    override val endpointHandler: EndpointHandler<ReporterRequest> = { message ->
        val reporterWorker by inject<ReporterWorker>()
        val publisher by inject<MessagePublisher>()
        val reporterJobId = message.payload.reporterJobId

        val response = when (val result = reporterWorker.run(reporterJobId, message.header.traceId)) {
            is RunResult.Success -> {
                logger.info("Reporter job '$reporterJobId' succeeded.")
                Message(message.header, ReporterWorkerResult(reporterJobId))
            }

            is RunResult.Failed -> {
                logger.error("Reporter job '$reporterJobId' failed.", result.error)
                Message(message.header, ReporterWorkerError(reporterJobId))
            }

            is RunResult.Ignored -> null
        }

        if (response != null) publisher.publish(OrchestratorEndpoint, response)
    }

    override fun customModules(): List<Module> = listOf(reporterModule(), databaseModule())

    private fun reporterModule(): Module = module {
        singleOf<AdvisorJobRepository>(::DaoAdvisorJobRepository)
        singleOf<AdvisorRunRepository>(::DaoAdvisorRunRepository)
        singleOf<AnalyzerJobRepository>(::DaoAnalyzerJobRepository)
        singleOf<AnalyzerRunRepository>(::DaoAnalyzerRunRepository)
        singleOf<EvaluatorJobRepository>(::DaoEvaluatorJobRepository)
        singleOf<EvaluatorRunRepository>(::DaoEvaluatorRunRepository)
        singleOf<OrtRunRepository>(::DaoOrtRunRepository)
        singleOf<ReporterJobRepository>(::DaoReporterJobRepository)
        singleOf<RepositoryRepository>(::DaoRepositoryRepository)

        singleOf(::ReporterWorkerDao)
        singleOf(::ReporterRunner)
        singleOf(::ReporterWorker)
    }
}
