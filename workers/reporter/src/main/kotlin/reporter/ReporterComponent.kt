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

package org.eclipse.apoapsis.ortserver.workers.reporter

import kotlin.time.Duration.Companion.days

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerError
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterWorkerResult
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext
import org.eclipse.apoapsis.ortserver.workers.common.OrtServerFileArchiveStorage
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

        /**
         * The path in the configuration containing the properties for the reporter worker.
         */
        private const val PATH_REPORTER = "reporter"

        /**
         * Name of the configuration property that defines the prefix for download links. This prefix should contain
         * the protocol, the host, and an optional path component. The path starting with "/api" is then added.
         */
        private const val REPORT_LINK_PREFIX_PROPERTY = "reportDownloadLinkPrefix"

        /**
         * Name of the configuration property that defines the length of the tokens that are generated to download
         * reports without authentication. If unauthenticated report download is not desired, set this property to a
         * value less than or equal to zero.
         */
        private const val REPORT_TOKEN_LENGTH_PROPERTY = "reportTokenLength"

        /**
         * Name of the configuration property that defines the validity time of generated tokens. The value is
         * interpreted as the number of days a token should be valid.
         */
        private const val REPORT_TOKEN_VALIDITY_PROPERTY = "reportTokenValidityDays"
    }

    override val endpointHandler: EndpointHandler<ReporterRequest> = { message ->
        val reporterWorker by inject<ReporterWorker>()
        val publisher by inject<MessagePublisher>()
        val reporterJobId = message.payload.reporterJobId

        withMdcContext("reporterJobId" to reporterJobId.toString()) {
            val response = when (val result = reporterWorker.run(reporterJobId, message.header.traceId)) {
                is RunResult.Success -> {
                    logger.info("Reporter job '$reporterJobId' succeeded.")
                    Message(message.header, ReporterWorkerResult(reporterJobId))
                }

                is RunResult.FinishedWithIssues -> {
                    logger.warn("Reporter job '$reporterJobId' finished with issues.")
                    Message(message.header, ReporterWorkerResult(reporterJobId, true))
                }

                is RunResult.Failed -> {
                    logger.error("Reporter job '$reporterJobId' failed.", result.error)
                    Message(message.header, ReporterWorkerError(reporterJobId, result.error.message))
                }

                is RunResult.Ignored -> null
            }

            // Check if there is a demand to keep the pod alive for manual problem analysis.
            sleepWhileKeepAliveFileExists()

            if (response != null) publisher.publish(OrchestratorEndpoint, response)
        }

        val handleSingleJobOnly = configManager.config.getBoolean("reporter.handleSingleJobOnly")
        if (handleSingleJobOnly) EndpointHandlerResult.STOP else EndpointHandlerResult.CONTINUE
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
        single { Storage.create(ReportStorage.STORAGE_TYPE, get()) }

        single {
            val storage = Storage.create(OrtServerFileArchiveStorage.STORAGE_TYPE, get())
            FileArchiver(LicenseFilePatterns.DEFAULT.allLicenseFilenames, OrtServerFileArchiveStorage(storage))
        }

        single {
            with(configManager.subConfig(Path(PATH_REPORTER))) {
                ReportDownloadLinkGenerator(
                    getString(REPORT_LINK_PREFIX_PROPERTY),
                    getInt(REPORT_TOKEN_LENGTH_PROPERTY),
                    getInt(REPORT_TOKEN_VALIDITY_PROPERTY).days
                )
            }
        }

        singleOf(::ReportStorage)
        singleOf(::ReporterRunner)
        singleOf(::ReporterWorker)
    }
}
