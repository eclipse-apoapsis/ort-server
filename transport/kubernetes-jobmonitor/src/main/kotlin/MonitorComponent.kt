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

package org.eclipse.apoapsis.ortserver.transport.kubernetes.jobmonitor

import com.typesafe.config.ConfigFactory

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.ClientBuilder

import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoAdvisorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoAnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoEvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoNotifierJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoReporterJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
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
        /** The prefix for all configuration properties. */
        private const val CONFIG_PREFIX = "jobMonitor"

        /** The configuration property that selects the namespace to watch. */
        private const val NAMESPACE_PROPERTY = "$CONFIG_PREFIX.namespace"

        /** The configuration property defining the interval in which the reaper runs. */
        private const val REAPER_INTERVAL_PROPERTY = "$CONFIG_PREFIX.reaperInterval"

        /** The configuration property defining the interval in which the lost jobs detection should run. */
        private const val LOST_JOBS_INTERVAL_PROPERTY = "$CONFIG_PREFIX.lostJobsInterval"

        /** The configuration property defining the minimum age of a job (in seconds) to be considered lost. */
        private const val LOST_JOBS_MIN_AGE_PROPERTY = "$CONFIG_PREFIX.lostJobsMinAge"

        /** The configuration property to enable or disable the watching component. */
        private const val WATCHING_ENABLED_PROPERTY = "$CONFIG_PREFIX.enableWatching"

        /** The configuration property to enable or disable the Reaper component. */
        private const val REAPER_ENABLED_PROPERTY = "$CONFIG_PREFIX.enableReaper"

        /** The configuration property to enable or disable the detection of lost jobs. */
        private const val LOST_JOBS_ENABLED_PROPERTY = "$CONFIG_PREFIX.enableLostJobs"

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

        if (configManager.getBoolean(REAPER_ENABLED_PROPERTY)) {
            logger.info("Starting Reaper component.")

            val scheduler by inject<Scheduler>()
            val reaper by inject<Reaper>()
            reaper.run(scheduler, configManager.getInt(REAPER_INTERVAL_PROPERTY).seconds)
        }

        if (configManager.getBoolean(LOST_JOBS_ENABLED_PROPERTY)) {
            logger.info("Starting lost jobs detection component.")

            val scheduler by inject<Scheduler>()
            val lostJobsFinder by inject<LostJobsFinder>()
            lostJobsFinder.run(scheduler, configManager.getInt(LOST_JOBS_INTERVAL_PROPERTY).seconds)
        }

        if (configManager.getBoolean(WATCHING_ENABLED_PROPERTY)) {
            logger.info("Starting watcher component.")

            val monitor by inject<JobMonitor>()
            monitor.watch()
        }
    }

    /**
     * Return a [Module] with the components used by this application.
     */
    internal fun monitoringModule(): Module {
        val namespace = configManager.getString(NAMESPACE_PROPERTY)

        return module {
            single<Clock> { Clock.System }
            single { configManager }
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

            single { Scheduler() }
            single { JobWatchHelper.create(get(), namespace) }
            single { JobHandler(get(), get(), get(), namespace) }
            single { FailedJobNotifier(get()) }
            singleOf(::JobMonitor)
            single { Reaper(get(), configManager.getInt(REAPER_INTERVAL_PROPERTY).seconds) }
            single {
                LostJobsFinder(
                    get(),
                    get(),
                    configManager.getInt(LOST_JOBS_MIN_AGE_PROPERTY).seconds,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get()
                )
            }
        }
    }
}
