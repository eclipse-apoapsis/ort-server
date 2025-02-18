/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.evaluator

import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorWorkerResult
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext
import org.eclipse.apoapsis.ortserver.workers.common.OrtServerFileArchiveStorage
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.workerContextModule
import org.eclipse.apoapsis.ortserver.workers.common.ortRunServiceModule

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.utils.FileArchiver

class EvaluatorComponent : EndpointComponent<EvaluatorRequest>(EvaluatorEndpoint) {
    override val endpointHandler: EndpointHandler<EvaluatorRequest> = { message ->
        val evaluatorWorker by inject<EvaluatorWorker>()
        val publisher by inject<MessagePublisher>()
        val evaluatorJobId = message.payload.evaluatorJobId

        withMdcContext("evaluatorJobId" to evaluatorJobId.toString()) {
            val response = when (val result = evaluatorWorker.run(evaluatorJobId, message.header.traceId)) {
                is RunResult.Success -> {
                    logger.info("Evaluator job '$evaluatorJobId' succeeded.")
                    Message(message.header, EvaluatorWorkerResult(evaluatorJobId))
                }

                is RunResult.FinishedWithIssues -> {
                    logger.warn("Evaluator job '$evaluatorJobId' finished with issues.")
                    Message(message.header, EvaluatorWorkerResult(evaluatorJobId, true))
                }

                is RunResult.Failed -> {
                    logger.error("Evaluator job '$evaluatorJobId' failed.", result.error)
                    Message(message.header, EvaluatorWorkerError(evaluatorJobId, result.error.message))
                }

                is RunResult.Ignored -> null
            }

            if (response != null) publisher.publish(OrchestratorEndpoint, response)
        }
    }

    override fun customModules(): List<Module> =
        listOf(evaluatorModule(), databaseModule(), ortRunServiceModule(), workerContextModule())

    private fun evaluatorModule(): Module = module {
        single {
            val storage = Storage.create(OrtServerFileArchiveStorage.STORAGE_TYPE, get())
            FileArchiver(LicenseFilePatterns.DEFAULT.allLicenseFilenames, OrtServerFileArchiveStorage(storage))
        }

        singleOf(::EvaluatorRunner)
        singleOf(::EvaluatorWorker)
    }
}
