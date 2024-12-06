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

package org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor

import com.typesafe.config.ConfigFactory

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.ClientBuilder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.DaoAdvisorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.DaoAnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.DaoEvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.DaoNotifierJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.DaoReporterJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.DaoScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.slf4j.LoggerFactory

/**
 * The main entrypoint into the job monitoring module. This class reads the configuration and sets up the required
 * helper components.
 */
internal class MonitorComponent(
    /** The configuration of this module. */
    private val configManager: ConfigManager = ConfigManager.create(ConfigFactory.load())
) : KoinComponent {
    companion object {
        private val logger = LoggerFactory.getLogger(MonitorComponent::class.java)
    }

    init {
        startKoin {
            modules(databaseModule(startEager = false), monitoringModule())
        }
    }

    /** Start this component as it has been configured. */
    suspend fun start() = withContext(Dispatchers.Default) {
        logger.info("Starting Kubernetes Job Monitor Component.")

        val monitorConfig by inject<MonitorConfig>()

        if (monitorConfig.reaperEnabled) {
            logger.info("Starting Reaper component.")

            val scheduler by inject<Scheduler>()
            val reaper by inject<Reaper>()
            reaper.run(scheduler)
        }

        if (monitorConfig.lostJobsEnabled) {
            logger.info("Starting lost jobs detection component.")

            val scheduler by inject<Scheduler>()
            val lostJobsFinder by inject<LostJobsFinder>()
            lostJobsFinder.run(scheduler)
        }

        if (monitorConfig.longRunningJobsEnabled) {
            logger.info("Starting long-running jobs detection component.")

            val scheduler by inject<Scheduler>()
            val longRunningJobsFinder by inject<LongRunningJobsFinder>()
            longRunningJobsFinder.run(scheduler)
        }

        if (monitorConfig.watchingEnabled) {
            logger.info("Starting watcher component.")

            val monitor by inject<JobMonitor>()
            monitor.watch()
        }

        if (monitorConfig.stuckJobsEnabled) {
            logger.info("Starting watcher component.")

            val scheduler by inject<Scheduler>()
            val stuckJobsFinder by inject<OrtRunStuckJobsFinder>()
            stuckJobsFinder.run(scheduler)
        }
    }

    /**
     * Return a [Module] with the components used by this application.
     */
    internal fun monitoringModule(): Module {
        return module {
            single { TimeHelper() }
            single { configManager }
            single { MonitorConfig.create(get()) }
            single { ClientBuilder.defaultClient() }
            single { BatchV1Api(get()) }
            single { CoreV1Api(get()) }

            single { MessageSenderFactory.createSender(OrchestratorEndpoint, configManager) }

            single<AdvisorJobRepository> { DaoAdvisorJobRepository(get()) }
            single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
            single<EvaluatorJobRepository> { DaoEvaluatorJobRepository(get()) }
            single<ReporterJobRepository> { DaoReporterJobRepository(get()) }
            single<ScannerJobRepository> { DaoScannerJobRepository(get()) }
            single<NotifierJobRepository> { DaoNotifierJobRepository(get()) }
            single<OrtRunRepository> { DaoOrtRunRepository(get()) }

            single { Scheduler() }
            single { JobWatchHelper.create(get(), get()) }
            single { JobHandler(get(), get(), get(), get()) }
            single { FailedJobNotifier(get()) }
            singleOf(::JobMonitor)
            singleOf(::Reaper)
            singleOf(::LostJobsFinder)
            singleOf(::LongRunningJobsFinder)
            singleOf(::OrtRunStuckJobsFinder)
        }
    }
}
