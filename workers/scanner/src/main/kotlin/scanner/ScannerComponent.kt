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

package org.ossreviewtoolkit.server.workers.scanner

import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.dsl.module

import org.ossreviewtoolkit.server.dao.databaseModule
import org.ossreviewtoolkit.server.dao.repositories.DaoScannerJobRepository
import org.ossreviewtoolkit.server.model.orchestrator.ScannerRequest
import org.ossreviewtoolkit.server.model.orchestrator.ScannerWorkerResult
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.ScannerEndpoint

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ScannerComponent::class.java)

class ScannerComponent : EndpointComponent<ScannerRequest>(ScannerEndpoint) {
    override val endpointHandler: EndpointHandler<ScannerRequest> = { message ->
        val publisher by inject<MessagePublisher>()
        val scannerJobId = message.payload.scannerJobId

        logger.info("Scanner job '$scannerJobId' succeeded.")
        val response = Message(message.header, ScannerWorkerResult(scannerJobId))

        publisher.publish(OrchestratorEndpoint, response)
    }

    override fun customModules(): List<Module> = listOf(scannerModule(), databaseModule())

    private fun scannerModule(): Module = module {
        single<ScannerJobRepository> { DaoScannerJobRepository(get()) }
    }
}
