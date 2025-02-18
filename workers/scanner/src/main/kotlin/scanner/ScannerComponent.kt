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

package org.eclipse.apoapsis.ortserver.workers.scanner

import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerWorkerResult
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext
import org.eclipse.apoapsis.ortserver.workers.common.OrtServerFileArchiveStorage
import org.eclipse.apoapsis.ortserver.workers.common.OrtServerFileListStorage
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.workerContextModule
import org.eclipse.apoapsis.ortserver.workers.common.env.buildEnvironmentModule
import org.eclipse.apoapsis.ortserver.workers.common.ortRunServiceModule

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.utils.FileArchiver

class ScannerComponent : EndpointComponent<ScannerRequest>(ScannerEndpoint) {
    override val endpointHandler: EndpointHandler<ScannerRequest> = { message ->
        val scannerWorker by inject<ScannerWorker>()
        val publisher by inject<MessagePublisher>()
        val scannerJobId = message.payload.scannerJobId

        withMdcContext("scannerJobId" to scannerJobId.toString()) {
            val response = when (val result = scannerWorker.run(scannerJobId, message.header.traceId)) {
                is RunResult.Success -> {
                    logger.info("Scanner job '$scannerJobId' succeeded.")
                    Message(message.header, ScannerWorkerResult(scannerJobId))
                }

                is RunResult.FinishedWithIssues -> {
                    logger.warn("Scanner job '$scannerJobId' finished with issues.")
                    Message(message.header, ScannerWorkerResult(scannerJobId, true))
                }

                is RunResult.Failed -> {
                    logger.error("Scanner job '$scannerJobId' failed.", result.error)
                    Message(message.header, ScannerWorkerError(scannerJobId, result.error.message))
                }

                is RunResult.Ignored -> null
            }

            if (response != null) publisher.publish(OrchestratorEndpoint, response)
        }
    }

    override fun customModules(): List<Module> = listOf(
        scannerModule(),
        databaseModule(),
        ortRunServiceModule(),
        workerContextModule(),
        buildEnvironmentModule()
    )

    private fun scannerModule(): Module = module {
        single {
            val storage = Storage.create(OrtServerFileArchiveStorage.STORAGE_TYPE, get())
            FileArchiver(LicenseFilePatterns.DEFAULT.allLicenseFilenames, OrtServerFileArchiveStorage(storage))
        }

        single {
            val storage = Storage.create(OrtServerFileListStorage.STORAGE_TYPE, get())
            OrtServerFileListStorage(storage)
        }

        singleOf(::ScannerRunner)
        singleOf(::ScannerWorker)
    }
}
