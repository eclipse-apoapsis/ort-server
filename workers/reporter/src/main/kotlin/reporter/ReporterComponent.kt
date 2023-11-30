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

import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.dao.databaseModule
import org.ossreviewtoolkit.server.model.orchestrator.ReporterRequest
import org.ossreviewtoolkit.server.model.orchestrator.ReporterWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.ReporterWorkerResult
import org.ossreviewtoolkit.server.storage.Storage
import org.ossreviewtoolkit.server.transport.EndpointComponent
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint
import org.ossreviewtoolkit.server.transport.ReporterEndpoint
import org.ossreviewtoolkit.server.workers.common.OrtServerFileArchiveStorage
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.workerContextModule
import org.ossreviewtoolkit.server.workers.common.env.buildEnvironmentModule
import org.ossreviewtoolkit.server.workers.common.ortRunServiceModule

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReporterComponent::class.java)

class ReporterComponent : EndpointComponent<ReporterRequest>(ReporterEndpoint) {
    companion object {
        /**
         * A prefix used by the reporter worker to mark options in the job configuration as template files. When
         * processing the job options, all values starting with this prefix are interpreted as template files that
         * need to be downloaded via the config manager. Via this mechanism, arbitrary template files can be
         * specified in a generic way.
         */
        const val TEMPLATE_REFERENCE = "reporter-template://"

        /**
         * A placeholder that can be used in reporter options to refer to the current working directory. It is
         * replaced by the temporary directory in which reporter templates and asset files are located. Some reporters
         * need this information, for instance to define the search path for fonts.
         */
        const val WORK_DIR_PLACEHOLDER = "\${workdir}"
    }

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

    override fun customModules(): List<Module> =
        listOf(
            reporterModule(),
            databaseModule(),
            ortRunServiceModule(),
            workerContextModule(),
            buildEnvironmentModule()
        )

    private fun reporterModule(): Module = module {
        single { ConfigManager.create(get()) }
        single { Storage.create(ReportStorage.STORAGE_TYPE, get()) }

        single {
            val storage = Storage.create(OrtServerFileArchiveStorage.STORAGE_TYPE, get())
            FileArchiver(LicenseFilePatterns.DEFAULT.allLicenseFilenames, OrtServerFileArchiveStorage(storage))
        }

        singleOf(::ReportStorage)
        singleOf(::ReporterRunner)
        singleOf(::ReporterWorker)
    }
}
